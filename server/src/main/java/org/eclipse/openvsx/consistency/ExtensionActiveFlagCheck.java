/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.consistency;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.NamingUtil;

/**
 * Finds and repairs any {@link Extension} whose {@code active} flag disagrees with whether it actually
 * has an active version. This can only happen from a cross-transaction lost-update race: a transaction
 * that never touches {@code active} (e.g. a download-count bump, a review, mirror metadata sync) loads
 * the row, and a full-row {@code UPDATE} committed after a concurrent delete overwrites {@code active}
 * back to its stale, pre-delete value. {@code @DynamicUpdate} on {@code Extension} prevents this going
 * forward; this check repairs any row already left inconsistent by it before that fix (or by any future
 * regression of the same kind).
 */
@Component
public class ExtensionActiveFlagCheck implements ConsistencyCheck {

    public static final String ID = "extension-active-flag";

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final ExtensionService extensions;

    public ExtensionActiveFlagCheck(
            EntityManager entityManager,
            RepositoryService repositories,
            ExtensionService extensions
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.extensions = extensions;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Extension active flag";
    }

    @Override
    public String getDescription() {
        return "Extensions whose `active` flag disagrees with whether any of their versions is active.";
    }

    @Override
    @Transactional
    public List<ConsistencyFinding> check() {
        return repositories.findExtensionsWithInconsistentActiveFlag().stream()
                .map(
                        extension -> new ConsistencyFinding(
                                extension.getId(),
                                NamingUtil.toExtensionId(extension),
                                extension.isActive()
                                        ? "marked active, but no version of it is active"
                                        : "marked inactive, but an active version exists"))
                .toList();
    }

    @Override
    @Transactional
    public void fix(long entityId) {
        var extension = entityManager.find(Extension.class, entityId);
        if (extension == null) {
            return;
        }

        extensions.updateExtension(extension);
    }
}
