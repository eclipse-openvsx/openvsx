/******************************************************************************
 * Copyright (c) 2025 Eclipse Foundation and others
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
package org.eclipse.openvsx.adapter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.eclipse.openvsx.search.SearchExplainService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.*;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionService;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(SpringExtension.class)
@MockitoBean(
    types = {
        VSCodeAPI.class,
        SimpleMeterRegistry.class,
        SearchUtilService.class,
        SearchExplainService.class,
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

    private int originalMaxPreReleaseVersions;

    @BeforeEach
    void captureMaxPreReleaseVersions() {
        originalMaxPreReleaseVersions = vsCodeService.maxPreReleaseVersions;
    }

    @AfterEach
    void resetMaxPreReleaseVersions() {
        vsCodeService.maxPreReleaseVersions = originalMaxPreReleaseVersions;
    }

    @Test
    void testExtensionQuery_noVersionFlags_skipsActiveVersionsFetch() {
        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "0.1.0", "linux");

        var criterion = new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1");
        var filter = new ExtensionQueryParam.Filter(List.of(criterion), 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), 0);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension));
        Mockito.when(repositories.findLatestVersions(any(), any())).thenReturn(List.of(extensionVersion));

        vsCodeService.extensionQuery(param, 10);

        Mockito.verify(repositories, Mockito.never()).findActiveExtensionVersions(any(), any(), anyInt());
        Mockito.verify(repositories, Mockito.times(1)).findLatestVersions(any(), any());
    }

    @Test
    void testExtensionQuery_versionsFlagUncapped_reusesFetchedListForLatest() {
        assertThat(vsCodeService.maxPreReleaseVersions).isNegative();

        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "0.1.0", "linux");

        var criterion = new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1");
        var filter = new ExtensionQueryParam.Filter(List.of(criterion), 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), FLAG_INCLUDE_VERSIONS);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension));
        Mockito.when(repositories.findActiveExtensionVersions(any(), any(), anyInt()))
                .thenReturn(List.of(extensionVersion));
        Mockito.when(versions.getLatest(anyList(), eq(false))).thenReturn(extensionVersion);

        vsCodeService.extensionQuery(param, 10);

        Mockito.verify(repositories, Mockito.times(1)).findActiveExtensionVersions(any(), any(), anyInt());
        Mockito.verify(repositories, Mockito.never()).findLatestVersions(any(), any());
    }

    @Test
    void testExtensionQuery_versionsFlagCapped_queriesLatestDirectly() {
        vsCodeService.maxPreReleaseVersions = 5;

        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "0.1.0", "linux");

        var criterion = new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1");
        var filter = new ExtensionQueryParam.Filter(List.of(criterion), 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), FLAG_INCLUDE_VERSIONS);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension));
        Mockito.when(repositories.findActiveExtensionVersions(any(), any(), anyInt()))
                .thenReturn(List.of(extensionVersion));
        Mockito.when(repositories.findLatestVersions(any(), any())).thenReturn(List.of(extensionVersion));

        vsCodeService.extensionQuery(param, 10);

        Mockito.verify(repositories, Mockito.times(1)).findActiveExtensionVersions(any(), any(), anyInt());
        Mockito.verify(repositories, Mockito.times(1)).findLatestVersions(any(), any());
    }

    @Test
    void testExtensionQuery_latestVersionOnlyWithTargetPlatform_skipsActiveVersionsFetch() {
        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "0.1.0", TargetPlatform.NAME_LINUX_X64);

        var criteria = List.of(
                new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1"),
                new ExtensionQueryParam.Criterion(Criterion.FILTER_TARGET, TargetPlatform.NAME_LINUX_X64));
        var filter = new ExtensionQueryParam.Filter(criteria, 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), FLAG_INCLUDE_LATEST_VERSION_ONLY);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension));
        Mockito.when(repositories.findLatestVersions(any(), eq(TargetPlatform.NAME_LINUX_X64)))
                .thenReturn(List.of(extensionVersion));

        var result = vsCodeService.extensionQuery(param, 10);

        Mockito.verify(repositories, Mockito.never()).findActiveExtensionVersions(any(), any(), anyInt());
        Mockito.verify(repositories, Mockito.times(1)).findLatestVersions(any(), eq(TargetPlatform.NAME_LINUX_X64));
        assertThat(result.results()).hasSize(1);
    }

    // Regression test: an extension that matches the query's id/name filter but has no active
    // version for the requested target platform must be omitted from the result, not NPE in
    // toQueryExtension via a null "latest" - see the `if (latest == null) continue;` guard.
    @Test
    void testExtensionQuery_extensionWithNoMatchingLatestForTargetPlatform_isSkippedNotThrown() {
        var extensionWithVersion = mockExtension();
        var extensionWithoutVersion = mockExtension(2, "test-2", "other-ext");
        var extensionVersion = mockExtensionVersion(
                extensionWithVersion,
                1,
                "0.1.0",
                TargetPlatform.NAME_LINUX_X64);

        var criteria = List.of(
                new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1"),
                new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-2"),
                new ExtensionQueryParam.Criterion(Criterion.FILTER_TARGET, TargetPlatform.NAME_LINUX_X64));
        var filter = new ExtensionQueryParam.Filter(criteria, 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), 0);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extensionWithVersion, extensionWithoutVersion));
        // extensionWithoutVersion has no active version on linux-x64, so the bulk "latest per
        // extension" query simply has no row for it - the real-world trigger for the null guard.
        Mockito.when(repositories.findLatestVersions(any(), eq(TargetPlatform.NAME_LINUX_X64)))
                .thenReturn(List.of(extensionVersion));

        var result = vsCodeService.extensionQuery(param, 10);

        var extensionIds = result.results().getFirst().extensions().stream()
                .map(ExtensionQueryResult.Extension::extensionId)
                .toList();
        assertThat(extensionIds).containsExactly("test-1");
    }

    // Regression test: the pre-release rank cap ranks across ALL target platforms combined (see
    // findAllActiveByExtensionIdAndTargetPlatform's Javadoc), so a platform whose only versions
    // rank outside the cap can come back empty from the capped active-version fetch even though
    // the extension does have a version for that platform. "latest" must still resolve correctly
    // via the direct findLatestVersions query rather than reusing the (here, empty) capped list.
    @Test
    void testExtensionQuery_versionsFlagCapped_resolvesLatestDirectlyEvenWhenCappedFetchIsEmpty() {
        vsCodeService.maxPreReleaseVersions = 2;

        var extension = mockExtension();
        var latestForPlatform = mockExtensionVersion(extension, 1, "0.1.0", TargetPlatform.NAME_LINUX_X64);

        var criteria = List.of(
                new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1"),
                new ExtensionQueryParam.Criterion(Criterion.FILTER_TARGET, TargetPlatform.NAME_LINUX_X64));
        var filter = new ExtensionQueryParam.Filter(criteria, 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), FLAG_INCLUDE_VERSIONS);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension));
        Mockito.when(repositories.findActiveExtensionVersions(any(), eq(TargetPlatform.NAME_LINUX_X64), eq(2)))
                .thenReturn(Collections.emptyList());
        Mockito.when(repositories.findLatestVersions(any(), eq(TargetPlatform.NAME_LINUX_X64)))
                .thenReturn(List.of(latestForPlatform));

        var result = vsCodeService.extensionQuery(param, 10);

        Mockito.verify(repositories, Mockito.times(1)).findLatestVersions(any(), eq(TargetPlatform.NAME_LINUX_X64));
        var queried = result.results().getFirst().extensions().getFirst();
        assertThat(queried.displayName()).isEqualTo(latestForPlatform.getDisplayName());
    }

    @Test
    void testExtensionQuery_latestVersionOnlyNoTargetPlatform_stillFetchesActiveVersions() {
        var extension = mockExtension();
        var extensionVersion = mockExtensionVersion(extension, 1, "0.1.0", "linux");

        var criterion = new ExtensionQueryParam.Criterion(Criterion.FILTER_EXTENSION_ID, "test-1");
        var filter = new ExtensionQueryParam.Filter(List.of(criterion), 0, 0, 0, 0);
        var param = new ExtensionQueryParam(List.of(filter), FLAG_INCLUDE_LATEST_VERSION_ONLY);

        Mockito.when(repositories.findActiveExtensionsByPublicId(any(), any()))
                .thenReturn(List.of(extension));
        Mockito.when(repositories.findActiveExtensionVersions(any(), any(), anyInt()))
                .thenReturn(List.of(extensionVersion));
        Mockito.when(versions.getLatest(anyList(), anyBoolean())).thenReturn(extensionVersion);

        vsCodeService.extensionQuery(param, 10);

        // Deliberate limitation, not an oversight: without a concrete target platform, the
        // fast path does not apply, so the full active-version fetch still runs.
        Mockito.verify(repositories, Mockito.times(1)).findActiveExtensionVersions(any(), any(), anyInt());
    }

    // ---------- UTILITY ----------//

    private Extension mockExtension() {
        return mockExtension(1, "test-1", "vscode-yaml");
    }

    private Extension mockExtension(long id, String publicId, String name) {
        var namespace = new Namespace();
        namespace.setId(2);
        namespace.setPublicId("test-2");
        namespace.setName("redhat");

        var extension = new Extension();
        extension.setId(id);
        extension.setPublicId(publicId);
        extension.setName(name);
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
                    cache,
                    new WebUiProperties());
        }
    }

}
