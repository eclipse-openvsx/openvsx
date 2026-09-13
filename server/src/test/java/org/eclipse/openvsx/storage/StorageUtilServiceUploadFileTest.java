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

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.log.DownloadCountService;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    DownloadCountService downloadCountService;
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
    @Mock
    WebUiProperties webUi;

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

    private StorageUtilService newService() {
        return new StorageUtilService(
                repositories,
                googleStorage,
                azureStorage,
                localStorage,
                awsStorage,
                downloadCountService,
                downloadMetrics,
                search,
                cache,
                entityManager,
                fileCacheDurationConfig,
                cdnServiceConfig,
                webUi);
    }
}
