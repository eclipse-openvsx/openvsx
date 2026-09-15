/** ******************************************************************************
 * Copyright (c) 2025 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.storage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionProcessor;
import org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchExplainService;
import org.eclipse.openvsx.search.SearchUtilService;

import static org.eclipse.openvsx.entities.FileResource.README;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@MockitoBean(
    types = {
        EntityManager.class,
        SearchUtilService.class,
        SearchExplainService.class,
        GoogleCloudStorageService.class,
        DownloadIngestionProcessor.class,
        ExtensionDownloadMetrics.class,
        CacheService.class,
        UserService.class,
        FileCacheDurationConfig.class,
        FilesCacheKeyGenerator.class,
        RepositoryService.class,
        LocalStorageService.class
    }
)
@ContextConfiguration(classes = StorageUtilServiceTest.TestConfig.class)
public class StorageUtilServiceTest {

    @Autowired
    CdnServiceConfig cdnServiceConfig;

    @Autowired
    AzureBlobStorageService azureStorage;

    @Autowired
    StorageUtilService storageUtilService;

    @Test
    public void testCdnEnabled() {
        cdnServiceConfig.setEnabled(true);

        // Test a file resource for which a cdn prefix is enabled
        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "1.0.0", "universal");
        var resourceAws = mockFileResource(1, extensionVersion, "README.md", README, FileResource.STORAGE_AWS);
        var resourceAzure = mockFileResource(1, extensionVersion, "README.md", README, FileResource.STORAGE_AZURE);

        assertEquals(
                "https://test.cloudfront.com/aws/redhat/vscode-yaml/1.0.0/README.md",
                storageUtilService.getLocation(resourceAws).toString());
        assertEquals(
                "https://test.cloudfront.com/azure/redhat/vscode-yaml/1.0.0/README.md",
                storageUtilService.getLocation(resourceAzure).toString());

        cdnServiceConfig.setEnabled(false);
    }

    /**
     * A version may carry semver build metadata, and S3 reads the {@code +} in a URL path as a space, so
     * the CDN URL for an AWS resource has to escape it. Other storage types keep the literal {@code +}.
     * See <a href="https://github.com/eclipse-openvsx/openvsx/issues/1459">issue 1459</a>.
     */
    @Test
    public void testCdnEnabledWithPlusSignInVersion() {
        cdnServiceConfig.setEnabled(true);
        try {
            var extension = mockExtension();
            var extensionVersion = mockExtensionVersion(extension, 1, "1.0.0+10", "universal");
            var resourceAws = mockFileResource(1, extensionVersion, "README.md", README, FileResource.STORAGE_AWS);
            var resourceAzure = mockFileResource(1, extensionVersion, "README.md", README, FileResource.STORAGE_AZURE);

            assertEquals(
                    "https://test.cloudfront.com/aws/redhat/vscode-yaml/1.0.0%2B10/README.md",
                    storageUtilService.getLocation(resourceAws).toString());
            assertEquals(
                    "https://test.cloudfront.com/azure/redhat/vscode-yaml/1.0.0+10/README.md",
                    storageUtilService.getLocation(resourceAzure).toString());
        } finally {
            cdnServiceConfig.setEnabled(false);
        }
    }

    @Test
    public void testCdnDisabled() {
        // Test a file resource for which no cdn prefix is enabled
        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "1.0.0", "universal");
        var resource = mockFileResource(1, extensionVersion, "README.md", README, FileResource.STORAGE_AZURE);

        azureStorage.serviceEndpoint = "http://azure.blob.storage/";
        azureStorage.blobContainer = "blob-container";

        var url = storageUtilService.getLocation(resource).toString();
        assertEquals("http://azure.blob.storage/blob-container/redhat/vscode-yaml/1.0.0/README.md", url);
    }

    // ---------- UTILITY ----------//

    private Extension mockExtension() {
        var namespace = new Namespace();
        namespace.setId(2);
        namespace.setPublicId("test-2");
        namespace.setName("redhat");

        var extension = new Extension();
        extension.setId(1);
        extension.setPublicId("test-1");
        extension.setName("vscode-yaml");
        extension.setAverageRating(3.0);
        extension.setReviewCount(10L);
        extension.setDownloadCount(100);
        extension.setPublishedDate(LocalDateTime.parse("1999-12-01T09:00"));
        extension.setLastUpdatedDate(LocalDateTime.parse("2000-01-01T10:00"));
        extension.setNamespace(namespace);

        return extension;
    }

    private ExtensionVersion mockExtensionVersion(Extension extension, long id, String version, String targetPlatform) {
        var extVersion = new ExtensionVersion();
        extVersion.setId(id);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform(targetPlatform);
        extVersion.setPreview(true);
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        extVersion.setDisplayName("YAML");
        extVersion.setDescription("YAML Language Support");
        extVersion.setEngines(List.of("vscode@^1.31.0"));
        extVersion.setRepository("https://github.com/redhat-developer/vscode-yaml");
        extVersion.setDependencies(Collections.emptyList());
        extVersion.setBundledExtensions(Collections.emptyList());
        extVersion.setLocalizedLanguages(Collections.emptyList());
        extVersion.setExtension(extension);

        var keyPair = new SignatureKeyPair();
        keyPair.setPublicId("123-456-789");
        extVersion.setSignatureKeyPair(keyPair);

        return extVersion;
    }

    private FileResource mockFileResource(
            long id,
            ExtensionVersion extVersion,
            String name,
            String type,
            String storageType
    ) {
        var resource = new FileResource();
        resource.setId(id);
        resource.setExtension(extVersion);
        resource.setName(name);
        resource.setType(type);
        resource.setStorageType(storageType);

        return resource;
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public CdnServiceConfig cdnServiceConfiguration() {
            var config = new CdnServiceConfig();
            config.setEnabled(false);
            var services = new HashMap<String, String>();
            services.put("aws", "https://test.cloudfront.com/aws");
            services.put("azure", "https://test.cloudfront.com/azure");
            config.setServices(services);
            return config;
        }

        @Bean
        public AwsStorageService awsStorageService(
                FileCacheDurationConfig fileCacheDurationConfig,
                FilesCacheKeyGenerator filesCacheKeyGenerator
        ) {
            return new AwsStorageService(fileCacheDurationConfig, filesCacheKeyGenerator);
        }

        @Bean
        AzureBlobStorageService azureBlobStorageService(FilesCacheKeyGenerator filesCacheKeyGenerator) {
            return new AzureBlobStorageService(filesCacheKeyGenerator);
        }

        @Bean
        StorageUtilService storageUtilService(
                RepositoryService repositories,
                GoogleCloudStorageService googleStorage,
                AzureBlobStorageService azureStorage,
                LocalStorageService localStorage,
                AwsStorageService awsStorage,
                ObjectProvider<DownloadRecordSource> ingestionSources,
                DownloadIngestionProcessor ingestionProcessor,
                ExtensionDownloadMetrics downloadMetrics,
                SearchUtilService search,
                CacheService cache,
                EntityManager entityManager,
                FileCacheDurationConfig fileCacheDurationConfig,
                CdnServiceConfig cdnServiceConfig
        ) {
            return new StorageUtilService(
                    repositories,
                    googleStorage,
                    azureStorage,
                    localStorage,
                    awsStorage,
                    ingestionSources,
                    ingestionProcessor,
                    downloadMetrics,
                    search,
                    cache,
                    entityManager,
                    fileCacheDurationConfig,
                    cdnServiceConfig);
        }
    }
}
