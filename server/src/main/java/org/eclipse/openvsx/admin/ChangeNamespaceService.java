/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TimeUtil;

@Component
public class ChangeNamespaceService {

    private final RepositoryService repositories;
    private final EntityManager entityManager;
    private final CacheService cache;
    private final SearchUtilService search;

    public ChangeNamespaceService(
            RepositoryService repositories,
            EntityManager entityManager,
            CacheService cache,
            SearchUtilService search
    ) {
        this.repositories = repositories;
        this.entityManager = entityManager;
        this.cache = cache;
        this.search = search;
    }

    @Transactional
    public void changeNamespaceInDatabase(
            Namespace newNamespace,
            Namespace oldNamespace,
            List<FileResource> updatedResources,
            boolean createNewNamespace,
            boolean removeOldNamespace
    ) {
        var extensions = repositories.findExtensions(oldNamespace);
        for (var extension : extensions) {
            cache.evictExtensionJsons(extension);
            cache.evictLatestExtensionVersion(extension);
        }

        // Read before the namespace changes, so departure withdraws the still-current old tuple.
        // Known gap: not locked against a concurrent writer (scan, admin action) on these extensions -
        // pre-existing, not introduced here; left as a follow-up.
        var now = TimeUtil.getCurrentUTC();
        recordNamespaceDeparture(extensions, now);

        if (createNewNamespace) {
            entityManager.persist(newNamespace);
        } else {
            // Deliberately a merge, unlike the rest of #989's call sites: the caller mutates this
            // detached namespace before handing it over (ChangeNamespaceJobRequestHandler sets
            // logoName on it), so the update belongs to the caller and only merge can carry it.
            // find-then-set would silently drop that rename.
            newNamespace = entityManager.merge(newNamespace);
        }

        changeExtensionNamespace(extensions, newNamespace);
        // Now under the new namespace - report the still-active versions there.
        recordNamespaceArrival(extensions, now);
        changeMembershipNamespace(oldNamespace, newNamespace, removeOldNamespace);
        renameResources(updatedResources);

        if (removeOldNamespace) {
            // find, not merge: this row is about to go, so merging every column of a detached copy
            // first was a wasted UPDATE - and would have resurrected a row already deleted.
            var managedOldNamespace = entityManager.find(Namespace.class, oldNamespace.getId());
            if (managedOldNamespace != null) {
                entityManager.remove(managedOldNamespace);
            }
        }

        cache.evictSitemap();
        cache.evictNamespaceDetails(oldNamespace);
        search.updateSearchEntries(extensions.filter(Extension::isActive).toList());
    }

    /**
     * Applies the names the caller computed for the copied resources. Only the name changes there
     * (see {@code ChangeNamespaceJobRequestHandler}), so this writes that field rather than merging
     * every column of each detached resource back - see #989.
     */
    private void renameResources(List<FileResource> updatedResources) {
        for (var resource : updatedResources) {
            var managedResource = entityManager.find(FileResource.class, resource.getId());
            if (managedResource != null) {
                managedResource.setName(resource.getName());
            }
        }
    }

    /**
     * Withdraws every version's old-namespace tuple as {@code REMOVED}. Nothing to withdraw for a
     * version never reported as available -- see {@link RepositoryService#wasReportedAsAvailable}.
     */
    private void recordNamespaceDeparture(Streamable<Extension> extensions, LocalDateTime now) {
        for (var extension : extensions) {
            for (var version : repositories.findVersions(extension)) {
                if (repositories.wasReportedAsAvailable(version)) {
                    repositories.recordExtensionVersionChange(version, ExtensionVersionState.REMOVED, now);
                }
            }
        }
    }

    /**
     * Announces every still-active version as {@code ACTIVE} under its new namespace. Inactive/removed
     * versions get nothing here -- not available under the new name either.
     */
    private void recordNamespaceArrival(Streamable<Extension> extensions, LocalDateTime now) {
        for (var extension : extensions) {
            for (var version : repositories.findVersions(extension)) {
                if (version.isActive()) {
                    repositories.recordExtensionVersionChange(version, ExtensionVersionState.ACTIVE, now);
                }
            }
        }
    }

    private void changeExtensionNamespace(Streamable<Extension> extensions, Namespace newNamespace) {
        for (var extension : extensions) {
            // findExtensions ran inside changeNamespaceInDatabase's transaction, so these are
            // already managed and merge just handed back the same instance; the setter is what
            // persists the change.
            extension.setNamespace(newNamespace);
        }
    }

    private void changeMembershipNamespace(Namespace oldNamespace, Namespace newNamespace, boolean removeOldNamespace) {
        var oldMemberships = repositories.findMemberships(oldNamespace).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), m -> m));
        var newMemberships = repositories.findMemberships(newNamespace).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), m -> m));

        for (var entry : oldMemberships.entrySet()) {
            if (!newMemberships.containsKey(entry.getKey())) {
                entry.getValue().setNamespace(newNamespace);
            } else if (removeOldNamespace) {
                entityManager.remove(entry.getValue());
            }
        }
    }
}
