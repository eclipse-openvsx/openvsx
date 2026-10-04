/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.migration;

import java.util.LinkedHashSet;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;

@Component
public class OrphanNamespaceMigration {

    protected final Logger logger = LoggerFactory.getLogger(OrphanNamespaceMigration.class);

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final ExtensionSizeLimitService sizeLimits;

    public OrphanNamespaceMigration(
            EntityManager entityManager,
            RepositoryService repositories,
            ExtensionSizeLimitService sizeLimits
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.sizeLimits = sizeLimits;
    }

    @Transactional
    public void fixOrphanNamespaces() {
        int[] count = new int[3];
        repositories.findOrphanNamespaces().forEach(namespace -> {
            var extensions = repositories.findExtensions(namespace);
            if (extensions.isEmpty()) {
                // The namespace is orphaned and empty - remove it
                entityManager.remove(namespace);
                count[0]++;
            } else {
                var publishers = getExtensionPublishers(extensions);
                if (publishers.isEmpty()) {
                    // This can happen if all extension versions are inactive
                    count[2]++;
                } else {
                    makePublishersNamespaceContributors(namespace, publishers);
                    count[1]++;
                }
            }
        });

        if (count[0] > 0) {
            // A deleted namespace takes any size override with it through the cascade, so the
            // ceiling derived from those overrides may no longer be the highest one configured.
            sizeLimits.invalidateCeiling();
            logger.info("Deleted {} namespaces that were orphaned and empty.", count[0]);
        }
        if (count[1] > 0) {
            logger.info("Assigned explicit members to {} orphaned namespaces.", count[1]);
        }
        if (count[2] > 0) {
            logger.info("Found {} orphaned namespaces that could not be fixed.", count[2]);
        }
    }

    private void makePublishersNamespaceContributors(Namespace namespace, LinkedHashSet<UserData> publishers) {
        for (var publisher : publishers) {
            var membership = new NamespaceMembership();
            membership.setNamespace(namespace);
            membership.setUser(publisher);
            membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
            entityManager.persist(membership);
        }
    }

    private LinkedHashSet<UserData> getExtensionPublishers(Streamable<Extension> extensions) {
        var publishers = new LinkedHashSet<UserData>();
        for (var extension : extensions) {
            for (var extVersion : repositories.findActiveVersions(extension)) {
                if (extVersion.getPublishedBy() != null) {
                    publishers.add(extVersion.getPublishedBy());
                }
            }
        }

        return publishers;
    }
}
