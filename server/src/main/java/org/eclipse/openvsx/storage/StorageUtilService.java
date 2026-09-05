/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.log.DownloadCountService;
import org.eclipse.openvsx.util.HttpHeadersUtil;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UrlUtil;

import static org.eclipse.openvsx.entities.FileResource.*;
import static org.eclipse.openvsx.util.UrlUtil.createApiFileUrl;

/**
 * Provides utility around storing file resources and acts as a composite storage
 * service around the actually configured external services.
 */
@Component
public class StorageUtilService implements IStorageService {

    private final RepositoryService repositories;
    private final GoogleCloudStorageService googleStorage;
    private final AzureBlobStorageService azureStorage;
    private final LocalStorageService localStorage;
    private final AwsStorageService awsStorage;
    private final DownloadCountService downloadCountService;
    private final ExtensionDownloadMetrics downloadMetrics;
    private final SearchUtilService search;
    private final CacheService cache;
    private final EntityManager entityManager;
    private final FileCacheDurationConfig fileCacheDurationConfig;
    private final CdnServiceConfig cdnServiceConfig;
    private final JsonMapper jsonMapper;

    /** Determines which external storage service to use in case multiple services are configured. */
    @Value("${ovsx.storage.primary-service:}")
    String primaryService;

    /** Determines which resource types are stored externally. Default: all resources ({@code *}) */
    @Value("${ovsx.storage.external-resource-types:*}")
    String[] externalResourceTypes;

    public StorageUtilService(
            RepositoryService repositories,
            GoogleCloudStorageService googleStorage,
            AzureBlobStorageService azureStorage,
            LocalStorageService localStorage,
            AwsStorageService awsStorage,
            DownloadCountService downloadCountService,
            ExtensionDownloadMetrics downloadMetrics,
            SearchUtilService search,
            CacheService cache,
            EntityManager entityManager,
            FileCacheDurationConfig fileCacheDurationConfig,
            CdnServiceConfig cdnServiceConfig
    ) {
        this.repositories = repositories;
        this.googleStorage = googleStorage;
        this.azureStorage = azureStorage;
        this.localStorage = localStorage;
        this.awsStorage = awsStorage;
        this.downloadCountService = downloadCountService;
        this.downloadMetrics = downloadMetrics;
        this.search = search;
        this.cache = cache;
        this.entityManager = entityManager;
        this.fileCacheDurationConfig = fileCacheDurationConfig;
        this.cdnServiceConfig = cdnServiceConfig;
        this.jsonMapper = JsonMapper.shared();
    }

    public boolean shouldStoreExternally(FileResource resource) {
        return (externalResourceTypes.length == 1 && "*".equals(externalResourceTypes[0]))
                || Arrays.asList(externalResourceTypes).contains(resource.getType());
    }

    private boolean shouldStoreLogoExternally() {
        return (externalResourceTypes.length == 1 && "*".equals(externalResourceTypes[0]))
                || Arrays.asList(externalResourceTypes).contains("namespace-logo");
    }

    @Override
    public boolean isEnabled() {
        return googleStorage.isEnabled() || azureStorage.isEnabled() || localStorage.isEnabled()
                || awsStorage.isEnabled();
    }

    public String getActiveStorageType() {
        var storageTypes = new ArrayList<String>(3);
        if (googleStorage.isEnabled()) {
            storageTypes.add(STORAGE_GOOGLE);
        }
        if (azureStorage.isEnabled()) {
            storageTypes.add(STORAGE_AZURE);
        }
        if (awsStorage.isEnabled()) {
            storageTypes.add(STORAGE_AWS);
        }
        if (!StringUtils.isEmpty(primaryService)) {
            if (!storageTypes.contains(primaryService)) {
                throw new IllegalStateException("The selected primary storage service is not available.");
            }
            return primaryService;
        }
        if (storageTypes.isEmpty()) {
            return getLocalStorageIfEnabled();
        }
        if (storageTypes.size() == 1) {
            return storageTypes.getFirst();
        }
        throw new IllegalStateException(
                "Multiple external storage services are available. Please select a primary service.");
    }

    /**
     * Returns the local storage type if it is enabled, otherwise raise an exception to fail fast.
     */
    private String getLocalStorageIfEnabled() {
        if (localStorage.isEnabled()) {
            return STORAGE_LOCAL;
        } else {
            throw new IllegalStateException("The fallback local storage service is not available.");
        }
    }

    /**
     * Returns the storage type for the specified {@link FileResource}.
     *
     * @param resource the {@link FileResource} to consider
     * @return the active storage type or the local type if the resource should not be stored externally
     * @throws IllegalStateException if the determined storage type is not enabled
     */
    private String getStorageTypeForResource(FileResource resource) {
        var storageType = getActiveStorageType();

        if (!storageType.equals(STORAGE_LOCAL) && !shouldStoreExternally(resource)) {
            storageType = getLocalStorageIfEnabled();
        }

        return storageType;
    }

    /**
     * Returns the storage type for a logo.
     *
     * @return the active storage type or the local type if a logo should not be stored externally
     * @throws IllegalStateException if the determined storage type is not enabled
     */
    private String getStorageTypeForLogo() {
        var storageType = getActiveStorageType();

        if (!storageType.equals(STORAGE_LOCAL) && !shouldStoreLogoExternally()) {
            storageType = getLocalStorageIfEnabled();
        }

        return storageType;
    }

    private IStorageService getStorageService(String storageType) {
        return switch (storageType) {
            case STORAGE_GOOGLE -> googleStorage;
            case STORAGE_AZURE -> azureStorage;
            case STORAGE_AWS -> awsStorage;
            case STORAGE_LOCAL -> localStorage;
            default -> throw new IllegalArgumentException("Storage '" + storageType + "' is not available.");
        };
    }

    private IStorageService getStorageServiceForRetrieval(String storageType) {
        return switch (storageType) {
            case STORAGE_GOOGLE -> googleStorage;
            case STORAGE_AZURE -> azureStorage;
            case STORAGE_AWS -> awsStorage;
            case STORAGE_LOCAL -> localStorage;
            default -> null;
        };
    }

    @Override
    public void uploadFile(TempFile tempFile) {
        var resource = tempFile.getResource();
        var storageType = getStorageTypeForResource(resource);
        getStorageService(storageType).uploadFile(tempFile);
        resource.setStorageType(storageType);
        resource.setSize(getFileSize(tempFile));
    }

    private long getFileSize(TempFile tempFile) {
        try {
            return Files.size(tempFile.getPath());
        } catch (IOException e) {
            throw new ServerErrorException("Failed to determine file size", e);
        }
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void uploadNamespaceLogo(TempFile logoFile) {
        var namespace = logoFile.getNamespace();
        var storageType = getStorageTypeForLogo();
        getStorageService(getStorageTypeForLogo()).uploadNamespaceLogo(logoFile);
        namespace.setLogoStorageType(storageType);
    }

    @Override
    public void removeFile(FileResource resource) {
        var storageType = resource.getStorageType();
        getStorageService(storageType).removeFile(resource);
    }

    @Override
    public void removeNamespaceLogo(Namespace namespace) {
        var storageType = namespace.getLogoStorageType();
        getStorageService(storageType).removeNamespaceLogo(namespace);
    }

    @Override
    public URI getLocation(FileResource resource) {
        String storageType = resource.getStorageType();
        var storageService = getStorageServiceForRetrieval(storageType);
        if (storageService != null) {
            String cdnFrontUrl = cdnServiceConfig.getCdnFrontUrl(storageType);
            if (cdnFrontUrl != null) {
                return UrlUtil.createURI(cdnFrontUrl, storageService.getObjectKeyAsUrlPath(resource));
            } else {
                return storageService.getLocation(resource);
            }
        } else {
            return null;
        }
    }

    @Override
    public URI getNamespaceLogoLocation(Namespace namespace) {
        String storageType = namespace.getLogoStorageType();
        var storageService = getStorageServiceForRetrieval(storageType);
        if (storageService != null) {
            String cdnFrontUrl = cdnServiceConfig.getCdnFrontUrl(storageType);
            if (cdnFrontUrl != null) {
                return UrlUtil.createURI(cdnFrontUrl, storageService.getObjectKeyAsUrlPath(namespace));
            } else {
                return storageService.getNamespaceLogoLocation(namespace);
            }
        } else {
            return null;
        }
    }

    public TempFile downloadFile(FileResource resource) throws IOException {
        var storageService = getStorageServiceForRetrieval(resource.getStorageType());
        return storageService != null ? storageService.downloadFile(resource) : null;
    }

    /**
     * Returns the size in bytes of {@code resource} as already stored, via a metadata-only lookup
     * rather than downloading its content -- see {@link IStorageService#getFileSize(FileResource)}.
     */
    public long getFileSize(FileResource resource) throws IOException {
        var storageService = getStorageServiceForRetrieval(resource.getStorageType());
        if (storageService == null) {
            throw new IOException("Storage '" + resource.getStorageType() + "' is not available.");
        }

        return storageService.getFileSize(resource);
    }

    /**
     * The stored files of these versions, by version id, in one query.
     * <p>
     * {@link #getFileUrls} is the URL-only shorthand over this; a caller that needs more than the URL of a
     * file - its size, say - takes the resources themselves rather than paying for a second query to read
     * the other column.
     */
    public Map<Long, List<FileResource>> getFiles(Collection<ExtensionVersion> extVersions, String... types) {
        var byVersion = extVersions.stream()
                .map(ev -> Map.<Long, List<FileResource>>entry(ev.getId(), new ArrayList<>(types.length)))
                .collect(
                        Collectors.<Map.Entry<Long, List<FileResource>>, Long, List<FileResource>>toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue));

        for (var resource : repositories.findFilesByType(extVersions, Arrays.asList(types))) {
            byVersion.get(resource.getExtension().getId()).add(resource);
        }

        return byVersion;
    }

    /**
     * Returns URLs for the given file types as a map of ExtensionVersion.id by a map of type by file URL, to be used in JSON response data.
     */
    public Map<Long, Map<String, String>> getFileUrls(
            Collection<ExtensionVersion> extVersions,
            String serverUrl,
            String... types
    ) {
        var type2Url = HashMap.<Long, Map<String, String>>newHashMap(extVersions.size());
        getFiles(extVersions, types).forEach((extVersionId, resources) -> {
            var urls = new LinkedHashMap<String, String>(types.length);
            for (var resource : resources) {
                urls.put(
                        resource.getType(),
                        createApiFileUrl(serverUrl, resource.getExtension(), resource.getName()));
            }

            type2Url.put(extVersionId, urls);
        });

        return type2Url;
    }

    @Transactional
    public void increaseDownloadCount(FileResource resource) {
        downloadMetrics.recordDownload(resource);

        if (downloadCountService.isEnabled(resource)) {
            // don't count downloads twice
            return;
        }

        var managedResource = entityManager.find(FileResource.class, resource.getId());
        var extension = managedResource.getExtension().getExtension();
        extension.setDownloadCount(extension.getDownloadCount() + 1);

        cache.evictNamespaceDetails(extension);
        cache.evictExtensionJsons(extension);
        if (extension.isActive()) {
            search.updateSearchEntry(extension);
        }
    }

    public ResponseEntity<StreamingResponseBody> getFileResponse(FileResource resource) {
        if (resource.getStorageType().equals(STORAGE_LOCAL)) {
            return localStorage.getFile(resource);
        } else {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(getLocation(resource))
                    .cacheControl(CacheControl.maxAge(fileCacheDurationConfig.getCacheDuration()).cachePublic())
                    .build();
        }
    }

    public ResponseEntity<StreamingResponseBody> getFileResponse(Path path) {
        var headers = HttpHeadersUtil.createFileResponseHeaders(path);
        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream -> {
                    try (var in = Files.newInputStream(path)) {
                        in.transferTo(outputStream);
                    }
                });
    }

    public ResponseEntity<StreamingResponseBody> getFileResponse(ArrayNode node) {
        var baseUrl = UrlUtil.getBaseUrl();
        var headers = HttpHeadersUtil.createJsonFileResponseHeaders();
        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream -> {
                    var value = jsonMapper.createArrayNode();
                    for (var item : node) {
                        value.add(baseUrl + item.asString());
                    }
                    jsonMapper.writeValue(outputStream, value);
                });
    }

    public ResponseEntity<StreamingResponseBody> getNamespaceLogo(Namespace namespace) {
        if (namespace.getLogoStorageType().equals(STORAGE_LOCAL)) {
            return localStorage.getNamespaceLogo(namespace);
        } else {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(getNamespaceLogoLocation(namespace))
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .build();
        }
    }

    @Override
    public void copyFiles(List<Pair<FileResource, FileResource>> pairs) {
        var groupedByStorageType = pairs.stream().collect(Collectors.groupingBy(p -> p.getFirst().getStorageType()));
        for (var entry : groupedByStorageType.entrySet()) {
            var storageType = entry.getKey();
            var group = entry.getValue();
            getStorageService(storageType).copyFiles(group);
        }
    }

    @Override
    public void copyNamespaceLogo(Namespace oldNamespace, Namespace newNamespace) {
        var storageType = oldNamespace.getLogoStorageType();
        getStorageService(storageType).copyNamespaceLogo(oldNamespace, newNamespace);
        newNamespace.setLogoStorageType(oldNamespace.getLogoStorageType());
    }

    @Override
    public Path getCachedFile(FileResource resource) {
        var storageService = getStorageServiceForRetrieval(resource.getStorageType());
        return storageService != null ? storageService.getCachedFile(resource) : null;
    }
}
