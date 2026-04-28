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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Maps;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.metrics.DownloadCountValidator;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.log.DownloadCountService;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UrlUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    private final Optional<DownloadCountValidator> downloadCountValidator;

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
            CdnServiceConfig cdnServiceConfig,
            Optional<DownloadCountValidator> downloadCountValidator
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
        this.downloadCountValidator = downloadCountValidator;
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
        return googleStorage.isEnabled() || azureStorage.isEnabled() || localStorage.isEnabled() || awsStorage.isEnabled();
    }

    public String getActiveStorageType() {
        var storageTypes = new ArrayList<String>(3);
        if (googleStorage.isEnabled())
            storageTypes.add(STORAGE_GOOGLE);
        if (azureStorage.isEnabled())
            storageTypes.add(STORAGE_AZURE);
        if (awsStorage.isEnabled())
            storageTypes.add(STORAGE_AWS);
        if (!StringUtils.isEmpty(primaryService)) {
            if (!storageTypes.contains(primaryService))
                throw new IllegalStateException("The selected primary storage service is not available.");
            return primaryService;
        }
        if (storageTypes.isEmpty())
            return getLocalStorageIfEnabled();
        if (storageTypes.size() == 1)
            return storageTypes.getFirst();
        throw new IllegalStateException("Multiple external storage services are available. Please select a primary service.");
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

        if(!storageType.equals(STORAGE_LOCAL) && !shouldStoreExternally(resource)) {
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

        if(!storageType.equals(STORAGE_LOCAL) && !shouldStoreLogoExternally()) {
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
                return UrlUtil.createURI(cdnFrontUrl, storageService.getObjectKey(resource));
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
                return UrlUtil.createURI(cdnFrontUrl, storageService.getObjectKey(namespace));
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
     * Returns URLs for the given file types as a map of ExtensionVersion.id by a map of type by file URL, to be used in JSON response data.
     */
    public Map<Long, Map<String, String>> getFileUrls(Collection<ExtensionVersion> extVersions, String serverUrl, String... types) {
        var type2Url = extVersions.stream()
                .map(ev -> Map.<Long, Map<String, String>>entry(ev.getId(), Maps.newLinkedHashMapWithExpectedSize(types.length)))
                .collect(Collectors.<Map.Entry<Long, Map<String, String>>, Long, Map<String, String>>toMap(Map.Entry::getKey, Map.Entry::getValue));

        var resources = repositories.findFilesByType(extVersions, Arrays.asList(types));
        for (var resource : resources) {
            var extVersion = resource.getExtension();
            type2Url.get(extVersion.getId()).put(resource.getType(), createApiFileUrl(serverUrl, extVersion, resource.getName()));
        }

        return type2Url;
    }

    @Transactional
    public void increaseDownloadCount(FileResource resource) {
        downloadMetrics.recordDownload(resource);

        if(downloadCountService.isEnabled(resource)) {
            // don't count downloads twice
            return;
        }

        var managedResource = entityManager.find(FileResource.class, resource.getId());
        var extension = managedResource.getExtension().getExtension();

        if (downloadCountValidator.isPresent() && downloadCountValidator.get().isValidationEnabled()) {
            var request = getCurrentHttpRequest();
            if (!downloadCountValidator.get().shouldCountDownload(extension, request)) {
                return;
            }
        }

        extension.setDownloadCount(extension.getDownloadCount() + 1);

        cache.evictNamespaceDetails(extension);
        cache.evictExtensionJsons(extension);
        if (extension.isActive()) {
            search.updateSearchEntry(extension);
        }
    }

    /**
     * Gets the current HttpServletRequest from Spring's RequestContextHolder.
     * Returns null if called outside a servlet request context (e.g., batch jobs).
     */
    private HttpServletRequest getCurrentHttpRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
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
        var fileName = path.getFileName().toString();
        var headers = new HttpHeaders();
        headers.setContentType(StorageUtil.getFileType(fileName));
        headers.setCacheControl(StorageUtil.getCacheControl(fileName));
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
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream -> {
                    var mapper = new ObjectMapper();
                    var value = mapper.createArrayNode();
                    for(var item : node) {
                        value.add(baseUrl + item.asText());
                    }
                    mapper.writeValue(outputStream, value);
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
    public void copyFiles(List<Pair<FileResource,FileResource>> pairs) {
        var groupedByStorageType = pairs.stream().collect(Collectors.groupingBy(p -> p.getFirst().getStorageType()));
        for(var entry : groupedByStorageType.entrySet()) {
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