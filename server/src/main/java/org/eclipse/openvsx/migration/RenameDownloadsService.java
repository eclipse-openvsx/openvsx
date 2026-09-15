/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.migration;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.FileResource;

@Component
public class RenameDownloadsService {

    private final EntityManager entityManager;

    public RenameDownloadsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public FileResource cloneResource(FileResource resource, String name) {
        // Nothing on `resource` is updated here; find still returns a managed entity, so the lazy
        // getExtension() below resolves just the same - see #989.
        var resourceId = resource.getId();
        resource = entityManager.find(FileResource.class, resourceId);
        if (resource == null) {
            // Deleted since the job loaded it. The caller uses the clone straight away, so there is
            // nothing to skip - fail with the id rather than an NPE on the next line.
            throw new IllegalStateException("Cannot clone file resource " + resourceId + ": it no longer exists");
        }
        var clone = new FileResource();
        clone.setName(name);
        clone.setStorageType(resource.getStorageType());
        clone.setType(resource.getType());
        clone.setExtension(resource.getExtension());
        return clone;
    }

    /**
     * Persists the new name its caller set on {@code resource}, and nothing else - see
     * {@code MigrationService#updateResourceSize} for why this is named for its field.
     */
    @Transactional
    public void updateResourceName(FileResource resource) {
        var managedResource = entityManager.find(FileResource.class, resource.getId());
        if (managedResource == null) {
            return;
        }
        managedResource.setName(resource.getName());
    }
}
