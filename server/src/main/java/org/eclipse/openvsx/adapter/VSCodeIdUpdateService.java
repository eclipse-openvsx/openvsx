/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.BuiltInExtensionUtil;
import org.eclipse.openvsx.util.NamingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VSCodeIdUpdateService {
    private final Logger logger = LoggerFactory.getLogger(VSCodeIdUpdateService.class);

    private final RepositoryService repositories;
    private final VSCodeIdService service;

    public VSCodeIdUpdateService(RepositoryService repositories, VSCodeIdService service) {
        this.repositories = repositories;
        this.service = service;
    }

    public void update(String namespaceName, String extensionName) {
        if (BuiltInExtensionUtil.isBuiltIn(namespaceName)) {
            logger.atDebug()
                    .setMessage("SKIP BUILT-IN EXTENSION {}")
                    .addArgument(() -> NamingUtil.toExtensionId(namespaceName, extensionName))
                    .log();
            return;
        }

        var extension = repositories.findPublicId(namespaceName, extensionName);
        var extensionUpdates = new HashMap<Long, String>();
        updateExtensionPublicId(extension, extensionUpdates, false);
        if (!extensionUpdates.isEmpty()) {
            repositories.updateExtensionPublicIds(extensionUpdates);
        }

        var namespaceUpdates = new HashMap<Long, String>();
        updateNamespacePublicId(extension, namespaceUpdates, false);
        if (!namespaceUpdates.isEmpty()) {
            repositories.updateNamespacePublicIds(namespaceUpdates);
        }
    }

    private void updateExtensionPublicId(Extension extension, Map<Long, String> updates, boolean mustUpdate) {
        logger.atDebug()
                .setMessage("updateExtensionPublicId: {}")
                .addArgument(() -> NamingUtil.toExtensionId(extension))
                .log();

        var oldPublicId = extension.getPublicId();
        var newPublicId = service.getUpstreamPublicIds(extension).extension();
        if (newPublicId == null || (mustUpdate && newPublicId.equals(oldPublicId))) {
            do {
                newPublicId = service.getRandomPublicId();
                logger.debug("RANDOM EXTENSION PUBLIC ID: {}", newPublicId);
            } while (updates.containsValue(newPublicId) || repositories.extensionPublicIdExists(newPublicId));
            logger.debug("RANDOM PUT UPDATE: {} - {}", extension.getId(), newPublicId);
            updates.put(extension.getId(), newPublicId);
        } else if (!newPublicId.equals(oldPublicId)) {
            logger.debug("UPSTREAM PUT UPDATE: {} - {}", extension.getId(), newPublicId);
            updates.put(extension.getId(), newPublicId);
            var duplicatePublicId = repositories.findPublicId(newPublicId);
            if (duplicatePublicId != null) {
                updateExtensionPublicId(duplicatePublicId, updates, true);
            }
        }
    }

    private void updateNamespacePublicId(Extension extension, Map<Long, String> updates, boolean mustUpdate) {
        logger.debug("updateNamespacePublicId: {}", extension.getNamespace().getName());
        var oldPublicId = extension.getNamespace().getPublicId();
        var newPublicId = service.getUpstreamPublicIds(extension).namespace();
        var id = extension.getNamespace().getId();
        if (newPublicId == null || (mustUpdate && newPublicId.equals(oldPublicId))) {
            do {
                newPublicId = service.getRandomPublicId();
                logger.debug("RANDOM NAMESPACE PUBLIC ID: {}", newPublicId);
            } while(updates.containsValue(newPublicId) || repositories.namespacePublicIdExists(newPublicId));
            logger.debug("RANDOM PUT UPDATE: {} - {}", id, newPublicId);
            updates.put(id, newPublicId);
        } else if (!newPublicId.equals(oldPublicId)) {
            logger.debug("UPSTREAM PUT UPDATE: {} - {}", id, newPublicId);
            updates.put(id, newPublicId);
            var duplicatePublicId = repositories.findNamespacePublicId(newPublicId);
            if(duplicatePublicId != null) {
                updateNamespacePublicId(duplicatePublicId, updates, true);
            }
        }
    }

    public void updateAll() {
        logger.debug("DAILY UPDATE ALL");
        var extensions = repositories.findAllPublicIds();
        var extensionPublicIdsMap = extensions.stream()
                .filter(e -> StringUtils.isNotEmpty(e.getPublicId()))
                .collect(Collectors.toMap(Extension::getId, Extension::getPublicId));
        var namespacePublicIdsMap = extensions.stream()
                .map(Extension::getNamespace)
                .filter(n -> StringUtils.isNotEmpty(n.getPublicId()))
                .collect(Collectors.toMap(Namespace::getId, Namespace::getPublicId, (id1, _) -> id1));

        var upstreamExtensionPublicIds = new HashMap<Long, String>();
        var upstreamNamespacePublicIds = new HashMap<Long, String>();
        for(var extension : extensions) {
            if(BuiltInExtensionUtil.isBuiltIn(extension)) {
                logger.atTrace()
                        .setMessage("SKIP BUILT-IN EXTENSION {}")
                        .addArgument(() -> NamingUtil.toExtensionId(extension))
                        .log();
                continue;
            }
            logger.atTrace()
                    .setMessage("GET UPSTREAM PUBLIC ID: {} | {}")
                    .addArgument(extension::getId)
                    .addArgument(() -> NamingUtil.toExtensionId(extension))
                    .log();

            var publicIds = service.getUpstreamPublicIds(extension);
            if (upstreamExtensionPublicIds.get(extension.getId()) == null) {
                logger.trace("ADD EXTENSION PUBLIC ID: {} - {}", extension.getId(), publicIds.extension());
                upstreamExtensionPublicIds.put(extension.getId(), publicIds.extension());
            }

            var namespace = extension.getNamespace();
            if (upstreamNamespacePublicIds.get(namespace.getId()) == null) {
                logger.trace("ADD NAMESPACE PUBLIC ID: {} - {}", namespace.getId(), publicIds.namespace());
                upstreamNamespacePublicIds.put(namespace.getId(), publicIds.namespace());
            }
        }

        var changedExtensionPublicIds = getChangedPublicIds(upstreamExtensionPublicIds, extensionPublicIdsMap);
        logger.debug("UPSTREAM EXTENSIONS: {}", upstreamExtensionPublicIds.size());
        logger.debug("CHANGED EXTENSIONS: {}", changedExtensionPublicIds.size());
        if (!changedExtensionPublicIds.isEmpty()) {
            logger.debug("CHANGED EXTENSION PUBLIC IDS");
            for (var entry : changedExtensionPublicIds.entrySet()) {
                logger.debug("{}: {}", entry.getKey(), entry.getValue());
            }

            repositories.updateExtensionPublicIds(changedExtensionPublicIds);
        }

        var changedNamespacePublicIds = getChangedPublicIds(upstreamNamespacePublicIds, namespacePublicIdsMap);
        logger.debug("UPSTREAM NAMESPACES: {}", upstreamNamespacePublicIds.size());
        logger.debug("CHANGED NAMESPACES: {}", changedNamespacePublicIds.size());
        if (!changedNamespacePublicIds.isEmpty()) {
            logger.debug("CHANGED NAMESPACE PUBLIC IDS");
            for (var entry : changedNamespacePublicIds.entrySet()) {
                logger.debug("{}: {}", entry.getKey(), entry.getValue());
            }

            repositories.updateNamespacePublicIds(changedNamespacePublicIds);
        }
    }

    private Map<Long, String> getChangedPublicIds(Map<Long, String> upstreamPublicIds, Map<Long, String> currentPublicIds) {
        var changedPublicIds = new HashMap<Long, String>();
        upstreamPublicIds.entrySet().stream()
                .filter(e -> !Objects.equals(currentPublicIds.get(e.getKey()), e.getValue()))
                .forEach(e -> changedPublicIds.put(e.getKey(), e.getValue()));

        if (!changedPublicIds.isEmpty()) {
            var newPublicIds = new HashSet<>(upstreamPublicIds.values());
            updatePublicIdNulls(changedPublicIds, newPublicIds, currentPublicIds);
        }

        return changedPublicIds;
    }

    private void updatePublicIdNulls(Map<Long, String> changedPublicIds, Set<String> newPublicIds, Map<Long, String> publicIdMap) {
        // remove unchanged random public ids
        changedPublicIds.entrySet().removeIf(e -> {
            var publicId = e.getValue() == null ? publicIdMap.get(e.getKey()) : null;
            var remove = publicId != null && !newPublicIds.contains(publicId);
            if (remove) {
                newPublicIds.add(publicId);
            }

            return remove;
        });

        // put random public ids where upstream public id is missing
        for (var entry : changedPublicIds.entrySet()) {
            if(entry.getValue() != null) {
                continue;
            }

            String publicId = null;
            while (newPublicIds.contains(publicId)) {
                publicId = service.getRandomPublicId();
                logger.debug("NEW PUBLIC ID - {}: '{}'", entry.getKey(), publicId);
            }

            entry.setValue(publicId);
            newPublicIds.add(publicId);
        }
    }
}
