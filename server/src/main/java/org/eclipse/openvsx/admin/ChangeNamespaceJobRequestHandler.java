/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.admin;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;

import static org.eclipse.openvsx.entities.FileResource.*;

@Component
public class ChangeNamespaceJobRequestHandler implements JobRequestHandler<ChangeNamespaceJobRequest> {

    private static final String EXT_PACKAGE = ".vsix";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeNamespaceJobRequestHandler.class);

    private static final List<String> RENAME_TYPES = List.of(DOWNLOAD, DOWNLOAD_SHA256, DOWNLOAD_SIG);
    private static final Map<String, Object> LOCKS;

    static {
        var MAX_SIZE = 100;
        LOCKS = Collections.synchronizedMap(new LinkedHashMap<>(MAX_SIZE) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_SIZE;
            }
        });
    }

    private final ExtensionValidator validator;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    private final ChangeNamespaceService service;

    public ChangeNamespaceJobRequestHandler(
            ExtensionValidator validator,
            RepositoryService repositories,
            StorageUtilService storageUtil,
            ChangeNamespaceService service
    ) {
        this.validator = validator;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
        this.service = service;
    }

    @Override
    public void run(ChangeNamespaceJobRequest jobRequest) throws Exception {
        var oldNamespace = jobRequest.getData().oldNamespace();
        Object lock;
        synchronized (LOCKS) {
            lock = LOCKS.computeIfAbsent(oldNamespace, key -> new Object());
        }
        synchronized (lock) {
            execute(jobRequest);
        }
    }

    private void execute(ChangeNamespaceJobRequest jobRequest) {
        var json = jobRequest.getData();
        LOGGER.info(">> Change namespace from {} to {}", json.oldNamespace(), json.newNamespace());
        var oldNamespace = repositories.findNamespace(json.oldNamespace());
        if (oldNamespace == null) {
            return;
        }

        var newNamespaceOptional = Optional.ofNullable(repositories.findNamespace(json.newNamespace()));
        var createNewNamespace = newNamespaceOptional.isEmpty();
        var newNamespace = newNamespaceOptional.orElseGet(() -> {
            validateNamespace(json.newNamespace());
            var namespace = new Namespace();
            namespace.setName(json.newNamespace());
            return namespace;
        });

        var oldResources = repositories.findFileResources(oldNamespace);
        var pairs = copyResources(oldResources, newNamespace);
        storageUtil.copyFiles(pairs);
        var updatedResources = pairs.stream()
                .filter(pair -> RENAME_TYPES.contains(pair.getFirst().getType()))
                .map(pair -> {
                    var oldResource = pair.getFirst();
                    var newResource = pair.getSecond();
                    oldResource.setName(newResource.getName());
                    return oldResource;
                })
                .collect(Collectors.toList());

        if (StringUtils.isNotEmpty(oldNamespace.getLogoName())) {
            newNamespace.setLogoName(NamingUtil.changeLogoName(oldNamespace, newNamespace));
            storageUtil.copyNamespaceLogo(oldNamespace, newNamespace);
        }

        service.changeNamespaceInDatabase(
                newNamespace,
                oldNamespace,
                updatedResources,
                createNewNamespace,
                json.removeOldNamespace());

        // remove the old resources from external storage
        pairs.stream()
                .map(Pair::getFirst)
                .forEach(storageUtil::removeFile);

        LOGGER.info("<< Changed namespace from {} to {}", json.oldNamespace(), json.newNamespace());
    }

    private void validateNamespace(String namespace) {
        var namespaceIssue = validator.validateNamespace(namespace);
        if (namespaceIssue.isPresent()) {
            throw new ErrorResultException(namespaceIssue.get().toString());
        }
    }

    private List<Pair<FileResource, FileResource>> copyResources(
            Streamable<FileResource> resources,
            Namespace newNamespace
    ) {
        var extVersions = resources.stream()
                .map(FileResource::getExtension)
                .collect(Collectors.toMap(ExtensionVersion::getId, ev -> ev, (ev1, ev2) -> ev1));

        var extensions = extVersions.values().stream()
                .map(ExtensionVersion::getExtension)
                .collect(Collectors.groupingBy(Extension::getId))
                .entrySet().stream()
                .map(entry -> {
                    var extension = entry.getValue().get(0);
                    var newExtension = new Extension();
                    newExtension.setId(extension.getId());
                    newExtension.setName(extension.getName());
                    newExtension.setNamespace(newNamespace);
                    return Map.entry(entry.getKey(), newExtension);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (var entry : extVersions.entrySet()) {
            var extVersion = entry.getValue();
            var newExtVersion = new ExtensionVersion();
            newExtVersion.setId(extVersion.getId());
            newExtVersion.setExtension(extensions.get(extVersion.getExtension().getId()));
            newExtVersion.setVersion(extVersion.getVersion());
            newExtVersion.setTargetPlatform(extVersion.getTargetPlatform());
            entry.setValue(newExtVersion);
        }

        var newBinaryNames = extVersions.values().stream()
                .map(extVersion -> Map.entry(extVersion.getId(), newBinaryName(newNamespace, extVersion)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return resources.stream()
                .map(resource -> {
                    var newExtVersion = extVersions.get(resource.getExtension().getId());
                    var newResource = new FileResource();
                    newResource.setId(resource.getId());
                    newResource.setExtension(newExtVersion);
                    newResource.setType(resource.getType());
                    newResource.setStorageType(resource.getStorageType());
                    newResource.setName(getNewResourceName(resource, newBinaryNames));
                    return Pair.of(resource, newResource);
                })
                .collect(Collectors.toList());
    }

    private String getNewResourceName(FileResource resource, Map<Long, String> newBinaryNames) {
        var name = RENAME_TYPES.contains(resource.getType())
                ? newBinaryNames.get(resource.getExtension().getId())
                : resource.getName();

        if (resource.getType().equals(DOWNLOAD_SHA256)) {
            name = name.replace(EXT_PACKAGE, ".sha256");
        }
        if (resource.getType().equals(DOWNLOAD_SIG)) {
            name = name.replace(EXT_PACKAGE, ".sigzip");
        }

        LOGGER.info("New resource name: {}", name);
        return name;
    }

    private String newBinaryName(Namespace newNamespace, ExtensionVersion extVersion) {
        return NamingUtil.toFileFormat(
                newNamespace.getName(),
                extVersion.getExtension().getName(),
                extVersion.getTargetPlatform(),
                extVersion.getVersion(),
                EXT_PACKAGE);
    }
}
