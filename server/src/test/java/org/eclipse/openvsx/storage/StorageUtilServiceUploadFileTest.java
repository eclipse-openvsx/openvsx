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
package org.eclipse.openvsx.storage;

import java.nio.file.Files;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionProcessor;
import org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TempFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorageUtilService#uploadFile(TempFile)} and
 * {@link StorageUtilService#getFileSize(FileResource)}.
 */
@ExtendWith(MockitoExtension.class)
class StorageUtilServiceUploadFileTest {

    @Mock
    RepositoryService repositories;
    @Mock
    GoogleCloudStorageService googleStorage;
    @Mock
    AzureBlobStorageService azureStorage;
    @Mock
    LocalStorageService localStorage;
    @Mock
    AwsStorageService awsStorage;
    @Mock
    ObjectProvider<DownloadRecordSource> ingestionSources;
    @Mock
    DownloadIngestionProcessor ingestionProcessor;
    @Mock
    ExtensionDownloadMetrics downloadMetrics;
    @Mock
    SearchUtilService search;
    @Mock
    org.eclipse.openvsx.cache.CacheService cache;
    @Mock
    EntityManager entityManager;
    @Mock
    FileCacheDurationConfig fileCacheDurationConfig;
    @Mock
    CdnServiceConfig cdnServiceConfig;

    @Test
    void uploadFile_recordsTheSizeOfTheUploadedBytes() throws Exception {
        when(localStorage.isEnabled()).thenReturn(true);

        var svc = newService();
        var resource = new FileResource();
        try (var tempFile = new TempFile("upload_", ".tmp")) {
            tempFile.setResource(resource);
            Files.writeString(tempFile.getPath(), "extension package bytes");

            svc.uploadFile(tempFile);

            assertThat(resource.getStorageType()).isEqualTo(FileResource.STORAGE_LOCAL);
            assertThat(resource.getSize()).isEqualTo(Files.size(tempFile.getPath()));
        }
    }

    @Test
    void getFileSize_delegatesToTheResourcesStorageBackend() throws Exception {
        var resource = new FileResource();
        resource.setStorageType(FileResource.STORAGE_AWS);
        when(awsStorage.getFileSize(resource)).thenReturn(1234L);

        assertThat(newService().getFileSize(resource)).isEqualTo(1234L);
    }

    @Test
    void getFileSize_failsFastForAnUnknownStorageType() {
        var resource = new FileResource();
        resource.setStorageType("not-a-real-storage-type");

        assertThatThrownBy(() -> newService().getFileSize(resource)).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void increaseDownloadCount_doesNotCountWhenAnEnabledSourceCoversTheDownload() {
        var resource = awsResource();

        newService(enabledSource(FileResource.STORAGE_AWS)).increaseDownloadCount(resource);

        // Ingestion will count this download from the access logs, so the request path must not.
        verify(entityManager, never()).find(any(), any());
        verify(ingestionProcessor, never()).captureDownload(any());
    }

    @Test
    void increaseDownloadCount_countsWhenTheSourceIsDisabled() {
        // The regression this guards: a source bean exists (its log bucket is configured) but is
        // disabled because the storage service behind it is not. Nothing ingests these downloads, so
        // suppressing them on the request path loses them entirely.
        var resource = awsResource();
        var extension = extensionBehind(resource);

        newService(disabledSource(FileResource.STORAGE_AWS)).increaseDownloadCount(resource);

        assertThat(extension.getDownloadCount()).isEqualTo(1);
        verify(ingestionProcessor).captureDownload(resource);
    }

    @Test
    void increaseDownloadCount_countsWhenNoSourceHandlesThatStorageType() {
        var resource = new FileResource();
        resource.setStorageType(FileResource.STORAGE_LOCAL);
        var extension = extensionBehind(resource);

        newService(enabledSource(FileResource.STORAGE_AWS)).increaseDownloadCount(resource);

        assertThat(extension.getDownloadCount()).isEqualTo(1);
    }

    @Test
    void initIngestionSources_rejectsTwoEnabledSourcesForOneStorageType() {
        // Which one answered would otherwise depend on bean ordering.
        assertThatThrownBy(
                () -> newService(
                        enabledSource(FileResource.STORAGE_AWS),
                        enabledSource(FileResource.STORAGE_AWS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FileResource.STORAGE_AWS);
    }

    private FileResource awsResource() {
        var resource = new FileResource();
        resource.setStorageType(FileResource.STORAGE_AWS);
        return resource;
    }

    /** Wires the entity graph increaseDownloadCount walks, and returns the extension it lands on. */
    private Extension extensionBehind(FileResource resource) {
        var extension = new Extension();
        var extensionVersion = new ExtensionVersion();
        extensionVersion.setExtension(extension);
        resource.setExtension(extensionVersion);
        when(entityManager.find(FileResource.class, resource.getId())).thenReturn(resource);
        return extension;
    }

    private DownloadRecordSource enabledSource(String storageType) {
        return source(storageType, true);
    }

    private DownloadRecordSource disabledSource(String storageType) {
        return source(storageType, false);
    }

    private DownloadRecordSource source(String storageType, boolean enabled) {
        var source = mock(DownloadRecordSource.class);
        when(source.isEnabled()).thenReturn(enabled);
        if (enabled) {
            when(source.getStorageType()).thenReturn(storageType);
            lenient().when(source.covers(any())).thenAnswer(
                    invocation -> storageType.equals(((FileResource) invocation.getArgument(0)).getStorageType()));
        }
        return source;
    }

    private StorageUtilService newService(DownloadRecordSource... sources) {
        when(ingestionSources.stream()).thenReturn(Stream.of(sources));
        var service = newServiceInstance();
        service.initIngestionSources();
        return service;
    }

    private StorageUtilService newServiceInstance() {
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
