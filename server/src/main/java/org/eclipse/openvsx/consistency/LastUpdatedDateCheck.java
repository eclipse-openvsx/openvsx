/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.consistency;

import java.util.Comparator;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;

/**
 * Finds and repairs any {@link Extension} whose {@code lastUpdatedDate} - what {@code sitemap.xml}
 * reports as {@code lastmod} - is older than the timestamp of its own latest active version. Same
 * cross-transaction lost-update race as {@link ExtensionActiveFlagCheck}: a transaction that never
 * touches {@code lastUpdatedDate} (a download-count bump, a review, mirror metadata sync) loads the
 * row, and a full-row {@code UPDATE} committed after a concurrent publish overwrites it back to its
 * stale, pre-publish value. {@code @DynamicUpdate} on {@code Extension} prevents this going forward;
 * this check repairs any row already left inconsistent by it. See issue #2229.
 */
@Component
public class LastUpdatedDateCheck implements ConsistencyCheck {

    public static final String ID = "extension-last-updated-date";

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final CacheService cache;

    public LastUpdatedDateCheck(EntityManager entityManager, RepositoryService repositories, CacheService cache) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.cache = cache;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Extension last updated date";
    }

    @Override
    public String getDescription() {
        return "Extensions whose last-updated date is older than the timestamp of their own latest "
                + "active version.";
    }

    @Override
    @Transactional
    public List<ConsistencyFinding> check() {
        return repositories.findExtensionsWithStaleLastUpdatedDate().stream()
                .map(
                        extension -> new ConsistencyFinding(
                                extension.getId(),
                                NamingUtil.toExtensionId(extension),
                                "last updated " + extension.getLastUpdatedDate()
                                        + ", but its latest active version is newer"))
                .toList();
    }

    @Override
    @Transactional
    public void fix(long entityId) {
        // Locked, not a plain find: a concurrent publish or activation for this same extension takes
        // this same kind of lock (see ExtensionRepository.findByNameIgnoreCaseAndNamespaceNameIgnoreCaseForUpdate)
        // to write lastUpdatedDate, and without it here the two could interleave so this fixer computes
        // "latest" from a pre-publish snapshot and then overwrites the publish's newer, correct value
        // with its own stale one - recreating the exact corruption this check exists to repair.
        var extension = entityManager.find(Extension.class, entityId, LockModeType.PESSIMISTIC_WRITE);
        if (extension == null) {
            return;
        }

        var latest = repositories.findActiveVersions(extension).stream()
                .map(ExtensionVersion::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latest == null) {
            return;
        }

        var current = extension.getLastUpdatedDate();
        if (current != null && !latest.isAfter(current)) {
            return;
        }

        extension.setLastUpdatedDate(latest);
        cache.evictSitemap();
    }
}
