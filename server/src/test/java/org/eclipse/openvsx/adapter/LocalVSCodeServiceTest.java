/********************************************************************************
 * Copyright (c) 2025 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.adapter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.*;
import org.eclipse.openvsx.util.VersionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(SpringExtension.class)
@MockitoBean(
    types = {
        VSCodeAPI.class,
        SimpleMeterRegistry.class,
        SearchUtilService.class,
        StorageUtilService.class,
        ExtensionVersionIntegrityService.class,
        WebResourceService.class,
        CacheService.class
    }
)
public class LocalVSCodeServiceTest {

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    VersionService versions;

    @Autowired
    LocalVSCodeService vsCodeService;

    @Test
    void testDuplicateExtensionsInSearch() {
        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "0.1.0", "linux");

        var criterion = new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1");
        var filter = new ExtensionQueryParam.Filter(List.of(criterion, criterion), 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), 0);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension, extension));
        Mockito.when(repositories.findActiveExtensionVersions(any(), any(), anyInt()))
                .thenReturn(List.of(extensionVersion));
        Mockito.when(repositories.findLatestVersions(any(), any())).thenReturn(List.of(extensionVersion));
        Mockito.when(versions.getLatest(anyList(), anyBoolean())).thenReturn(extensionVersion);

        var result = vsCodeService.extensionQuery(param, 10);
        assertThat(result.results()).hasSize(1);
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

    @TestConfiguration
    static class TestConfig {
        @Bean
        LocalVSCodeService vsCodeService(
                RepositoryService repositories,
                VersionService versions,
                SearchUtilService search,
                StorageUtilService storageUtil,
                ExtensionVersionIntegrityService integrityService,
                WebResourceService webResources,
                CacheService cache
        ) {
            return new LocalVSCodeService(
                    repositories,
                    versions,
                    search,
                    storageUtil,
                    integrityService,
                    webResources,
                    cache);
        }
    }

}
