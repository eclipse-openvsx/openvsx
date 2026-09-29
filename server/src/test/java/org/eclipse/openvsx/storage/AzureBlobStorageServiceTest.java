/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@MockitoBean(types = { StorageUtilService.class })
class AzureBlobStorageServiceTest {

    @Autowired
    AzureBlobStorageService service;

    @Test
    void testGetLocation() {
        service.serviceEndpoint = "http://azure.blob.storage/";
        service.blobContainer = "blob-container";
        var namespace = new Namespace();
        namespace.setName("abelfubu");

        var extension = new Extension();
        extension.setName("abelfubu-dark");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setVersion("1.3.4");
        extVersion.setTargetPlatform("universal");
        extVersion.setExtension(extension);

        var resource = new FileResource();
        resource.setName("extension/themes/abelFubu Dark+-color-theme.json");
        resource.setExtension(extVersion);

        var uri = service.getLocation(resource);
        var expected = URI.create(
                "http://azure.blob.storage/blob-container/abelfubu/abelfubu-dark/1.3.4/extension/themes/abelFubu%20Dark+-color-theme.json");
        assertEquals(expected, uri);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        FilesCacheKeyGenerator filesCacheKeyGenerator() {
            return new FilesCacheKeyGenerator();
        }

        @Bean
        AzureBlobStorageService azureBlobStorageService(FilesCacheKeyGenerator filesCacheKeyGenerator) {
            return new AzureBlobStorageService(filesCacheKeyGenerator);
        }
    }
}
