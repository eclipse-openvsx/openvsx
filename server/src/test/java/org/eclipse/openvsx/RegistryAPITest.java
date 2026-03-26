/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.openvsx.accesstoken.AccessTokenConfig;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.adapter.VSCodeIdService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.ExtensionJsonCacheKeyGenerator;
import org.eclipse.openvsx.cache.LatestExtensionVersionCacheKeyGenerator;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.eclipse.EclipseTokenService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.extension_control.ExtensionControlService;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.publish.PublishExtensionVersionHandler;
import org.eclipse.openvsx.publish.PublishExtensionVersionService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanPersistenceService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.search.*;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.security.OAuth2UserServices;
import org.eclipse.openvsx.security.SecurityConfig;
import org.eclipse.openvsx.storage.*;
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.storage.log.DownloadCountService;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;
import org.eclipse.openvsx.util.VersionService;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Streamable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.eclipse.openvsx.entities.FileResource.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistryAPI.class)
@AutoConfigureWebClient
@MockitoBean(types = {
    ClientRegistrationRepository.class, UpstreamRegistryService.class, GoogleCloudStorageService.class,
    AzureBlobStorageService.class, AwsStorageService.class, VSCodeIdService.class, DownloadCountService.class, ExtensionDownloadMetrics.class,
    CacheService.class, EclipseService.class, PublishExtensionVersionService.class, SimpleMeterRegistry.class,
    JobRequestScheduler.class, ExtensionControlService.class, FileCacheDurationConfig.class, CdnServiceConfig.class,
    ExtensionScanPersistenceService.class, LogService.class, AccessTokenConfig.class, MailService.class
})
class RegistryAPITest {

    @MockitoSpyBean
    UserService users;

    @MockitoBean
    RepositoryService repositories;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    ExtensionVersionIntegrityService integrityService;

    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    ExtensionScanService extensionScanService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ExtensionService extensionService;

    @Test
    void testPublicNamespace() throws Exception {
        var namespace = mockNamespace();
        Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                .thenReturn(false);

        mockMvc.perform(get("/api/{namespace}", "foobar"))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceJson(n -> {
                    n.setName("foobar");
                    n.setVerified(false);
                })));
    }

    @Test
    void testVerifiedNamespace() throws Exception {
        var namespace = mockNamespace();
        var user = new UserData();
        user.setLoginName("test_user");
        Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                .thenReturn(true);

        mockMvc.perform(get("/api/{namespace}", "foobar"))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceJson(n -> {
                    n.setName("foobar");
                    n.setVerified(true);
                })));
    }

    @Test
    void testUnknownNamespace() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/api/{namespace}", "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Namespace not found: unknown")));
    }

    @Test
    void testExtension() throws Exception {
        var extVersion = mockExtension();
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testExtensionWithPublicKey() throws Exception {
        Mockito.when(integrityService.isEnabled()).thenReturn(true);
        var extVersion = mockExtensionWithSignature();
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);

        var keyPair = new SignatureKeyPair();
        keyPair.setPublicId("123-456-7890");
        extVersion.setSignatureKeyPair(keyPair);
        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setFiles(Map.of("publicKey", "http://localhost/api/-/public-key/" + keyPair.getPublicId()));
                })));
    }

    @Test
    void testExtensionNonDefaultTarget() throws Exception {
        var extVersion = mockExtension("alpine-x64");
        extVersion.setDisplayName("Foo Bar (alpine x64)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (alpine x64)");
                    e.setTargetPlatform("alpine-x64");
                })));
    }

    @Test
    void testExtensionLinuxTarget() throws Exception {
        var extVersion = mockExtension("linux-x64");
        extVersion.setDisplayName("Foo Bar (linux x64)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "linux-x64", VersionAlias.LATEST)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{target}", "foo", "bar", "linux-x64"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (linux x64)");
                    e.setTargetPlatform("linux-x64");
                })));
    }

    @Test
    void testInactiveExtension() throws Exception {
        var extVersion = mockExtension();
        extVersion.setActive(false);
        extVersion.getExtension().setActive(false);

        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "bar"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar")));
    }

    @Test
    void testUnknownExtension() throws Exception {
        mockExtension();
        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "baz"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.baz")));
    }

    @Test
    void testUnknownExtensionTarget() throws Exception {
        mockExtension();
        mockMvc.perform(get("/api/{namespace}/{extension}/{target}", "foo", "bar", "win32-ia32"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar (win32-ia32)")));
    }

    @Test
    void testExtensionVersion() throws Exception {
        var extVersion = mockExtension();
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, "1.0.0")).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testExtensionVersionNonDefaultTarget() throws Exception {
        var extVersion = mockExtension("darwin-arm64");
        extVersion.setDisplayName("Foo Bar (darwin arm64)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, "1.0.0")).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (darwin arm64)");
                    e.setTargetPlatform("darwin-arm64");
                })));
    }

    @Test
    void testExtensionVersionMacOSXTarget() throws Exception {
        var extVersion = mockExtension("darwin-arm64");
        extVersion.setDisplayName("Foo Bar (darwin arm64)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "darwin-arm64", "1.0.0")).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{target}/{version}", "foo", "bar", "darwin-arm64", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (darwin arm64)");
                    e.setTargetPlatform("darwin-arm64");
                })));
    }

    @Test
    void testLatestExtensionVersion() throws Exception {
        var extVersion = mockExtension();
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "latest"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setVersionAlias(List.of("latest"));
                })));
    }

    @Test
    void testLatestExtensionVersionNonDefaultTarget() throws Exception {
        var extVersion = mockExtension("alpine-arm64");
        extVersion.setDisplayName("Foo Bar (alpine arm64)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "latest"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (alpine arm64)");
                    e.setTargetPlatform("alpine-arm64");
                    e.setVersionAlias(List.of("latest"));
                })));
    }

    @Test
    void testLatestExtensionVersionAlpineLinuxTarget() throws Exception {
        var extVersion = mockExtension("alpine-arm64");
        extVersion.setDisplayName("Foo Bar (alpine arm64)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "alpine-arm64", VersionAlias.LATEST)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), "alpine-arm64", false, true)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}/{target}/{version}", "foo", "bar", "alpine-arm64", "latest"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (alpine arm64)");
                    e.setTargetPlatform("alpine-arm64");
                    e.setVersionAlias(List.of("latest"));
                })));
    }

    @Test
    void testPreReleaseExtensionVersion() throws Exception {
        var extVersion = mockExtension();
        extVersion.setPreRelease(true);
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.PRE_RELEASE)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, true, true)).thenReturn(extVersion);
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "pre-release"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setVersionAlias(List.of("pre-release", "latest"));
                    e.setPreRelease(true);
                })));
    }

    @Test
    void testPreReleaseExtensionVersionNonDefaultTarget() throws Exception {
        var extVersion = mockExtension("web");
        extVersion.setPreRelease(true);
        extVersion.setDisplayName("Foo Bar (web)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.PRE_RELEASE)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, true, true)).thenReturn(extVersion);
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "pre-release"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (web)");
                    e.setTargetPlatform("web");
                    e.setVersionAlias(List.of("pre-release", "latest"));
                    e.setPreRelease(true);
                })));
    }

    @Test
    void testPreReleaseExtensionVersionWebTarget() throws Exception {
        var extVersion = mockExtension("web");
        extVersion.setPreRelease(true);
        extVersion.setDisplayName("Foo Bar (web)");
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "web", VersionAlias.PRE_RELEASE)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), "web", false, true)).thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), "web", true, true)).thenReturn(extVersion);
        mockMvc.perform(get("/api/{namespace}/{extension}/{target}/{version}", "foo", "bar", "web", "pre-release"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar (web)");
                    e.setTargetPlatform("web");
                    e.setVersionAlias(List.of("pre-release", "latest"));
                    e.setPreRelease(true);
                })));
    }

    @Test
    void testInactiveExtensionVersion() throws Exception {
        var extVersion = mockExtension();
        extVersion.setActive(false);

        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "1.0.0"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar 1.0.0")));
    }

    @Test
    void testUnknownExtensionVersion() throws Exception {
        mockExtension();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}", "foo", "bar", "2.0.0"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar 2.0.0")));
    }

    @Test
    void testUnknownExtensionVersionTarget() throws Exception {
        mockExtension();
        mockMvc.perform(get("/api/{namespace}/{extension}/{target}/{version}", "foo", "bar", "linux-armhf", "1.0.0"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar 1.0.0 (linux-armhf)")));
    }

    @Test
    void testReadmeUniversalTarget() throws Exception {
        var filePath = mockReadme();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "README"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("Please read me"))
                .andDo(result -> Files.delete(filePath));
    }

    @Test
    void testReadmeWindowsTarget() throws Exception {
        var filePath = mockReadme("win32-x64");
        mockMvc.perform(get("/api/{namespace}/{extension}/{target}/{version}/file/{fileName}", "foo", "bar", "win32-x64", "1.0.0", "README"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("Please read me"))
                .andDo(result -> Files.delete(filePath));
    }

    @Test
    void testReadmeUnknownTarget() throws Exception {
        var filePath = mockReadme();
        mockMvc.perform(get("/api/{namespace}/{extension}/{target}/{version}/file/{fileName}", "foo", "bar", "darwin-x64", "1.0.0", "README"))
                .andExpect(status().isNotFound())
                .andDo(result -> Files.delete(filePath));
    }

    @Test
    void testChangelog() throws Exception {
        var filePath = mockChangelog();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "CHANGELOG"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("All notable changes is documented here"))
                .andDo(result -> Files.delete(filePath));
    }

    @Test
    void testLicense() throws Exception {
        var filePath = mockLicense();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "LICENSE"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("I never broke the Law! I am the law!"))
                .andDo(result -> Files.delete(filePath));
    }

    @Test
    void testInactiveFile() throws Exception {
        var extVersion = mockExtension();
        extVersion.setActive(false);

        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "README"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUnknownFile() throws Exception {
        mockExtension();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "unknown.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testLatestFile() throws Exception {
        var filePath = mockLatest();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "latest", "DOWNLOAD"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("latest download"))
                .andDo(result -> Files.delete(filePath));
    }

    @Test
    void testReviews() throws Exception {
        mockReviews();
        mockMvc.perform(get("/api/{namespace}/{extension}/reviews", "foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(reviewsJson(rs -> {
                    var u1 = new UserJson();
                    u1.setLoginName("user1");
                    var r1 = new ReviewJson();
                    r1.setUser(u1);
                    r1.setRating(3);
                    r1.setComment("Somewhat ok");
                    r1.setTimestamp("2000-01-01T10:00Z");
                    rs.getReviews().add(r1);
                    var u2 = new UserJson();
                    u2.setLoginName("user2");
                    var r2 = new ReviewJson();
                    r2.setUser(u2);
                    r2.setRating(4);
                    r2.setComment("Quite good");
                    r2.setTimestamp("2000-01-01T10:00Z");
                    rs.getReviews().add(r2);
                })));
    }

    @Test
    void testSearch() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true)).thenReturn(extVersion));
        Mockito.when(repositories.findLatestVersions(extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "foo", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json(searchJson(s -> {
                    s.setOffset(0);
                    s.setTotalSize(1);
                    var e1 = new SearchEntryJson();
                    e1.setNamespace("foo");
                    e1.setName("bar");
                    e1.setVersion("1.0.0");
                    e1.setTimestamp("2000-01-01T10:00Z");
                    e1.setDisplayName("Foo Bar");
                    s.getExtensions().add(e1);
                })));
    }

    @Test
    void testSearchPublisher() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true)).thenReturn(extVersion));
        Mockito.when(repositories.findLatestVersions(extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "publisher:foo", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json(searchJson(s -> {
                    s.setOffset(0);
                    s.setTotalSize(1);
                    var e1 = new SearchEntryJson();
                    e1.setNamespace("foo");
                    e1.setName("bar");
                    e1.setVersion("1.0.0");
                    e1.setTimestamp("2000-01-01T10:00Z");
                    e1.setDisplayName("Foo Bar");
                    s.getExtensions().add(e1);
                })));
    }

    @Test
    void testSearchPublisherWithQueryLast() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true)).thenReturn(extVersion));
        Mockito.when(repositories.findLatestVersions(extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "publisher:foo bar", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json(searchJson(s -> {
                    s.setOffset(0);
                    s.setTotalSize(1);
                    var e1 = new SearchEntryJson();
                    e1.setNamespace("foo");
                    e1.setName("bar");
                    e1.setVersion("1.0.0");
                    e1.setTimestamp("2000-01-01T10:00Z");
                    e1.setDisplayName("Foo Bar");
                    s.getExtensions().add(e1);
                })));
    }

    @Test
    void testSearchPublisherWithQueryFirst() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true)).thenReturn(extVersion));
        Mockito.when(repositories.findLatestVersions(extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "bar publisher:foo", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json(searchJson(s -> {
                    s.setOffset(0);
                    s.setTotalSize(1);
                    var e1 = new SearchEntryJson();
                    e1.setNamespace("foo");
                    e1.setName("bar");
                    e1.setVersion("1.0.0");
                    e1.setTimestamp("2000-01-01T10:00Z");
                    e1.setDisplayName("Foo Bar");
                    s.getExtensions().add(e1);
                })));
    }

    @Test
    void testSearchPublisherWithMoreQuery() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true)).thenReturn(extVersion));
        Mockito.when(repositories.findLatestVersions(extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "bar publisher:foo code", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json(searchJson(s -> {
                    s.setOffset(0);
                    s.setTotalSize(1);
                    var e1 = new SearchEntryJson();
                    e1.setNamespace("foo");
                    e1.setName("bar");
                    e1.setVersion("1.0.0");
                    e1.setTimestamp("2000-01-01T10:00Z");
                    e1.setDisplayName("Foo Bar");
                    s.getExtensions().add(e1);
                })));
    }

    @Test
    void testSearchMultiplePublishers() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true)).thenReturn(extVersion));
        Mockito.when(repositories.findLatestVersions(extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "publisher:bar publisher:foo", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json(searchJson(s -> {
                    s.setOffset(0);
                    s.setTotalSize(0);
                })));
    }

    @Test
    void testSearchInactive() throws Exception {
        var extVersionsList = mockSearch();
        extVersionsList.forEach(extVersion -> {
            var extension = extVersion.getExtension();
            extension.setActive(false);
            extension.getVersions().get(0).setActive(false);
        });
        Mockito.when(repositories.findLatestVersions(extVersionsList.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "foo", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"offset\":0,\"totalSize\":1,\"extensions\":[]}"));
    }

    @Test
    void testGetQueryExtensionName() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?extensionName={extensionName}", "bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryNamespace() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?namespaceName={namespaceName}", "foo"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryUnknownExtension() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?extensionName={extensionName}", "baz"))
                .andExpect(status().isOk())
                .andExpect(content().json("{ \"extensions\": [] }"));
    }

    @Test
    void testGetQueryInactiveExtension() throws Exception {
        var namespaceName = "foo";
        var extensionName = "bar";

        mockInactiveExtensionVersion(namespaceName, extensionName);
        mockMvc.perform(get("/api/-/query?extensionId={namespaceName}.{extensionName}", namespaceName, extensionName))
                .andExpect(status().isOk())
                .andExpect(content().json("{ \"extensions\": [] }"));
    }

    @Test
    void testGetQueryExtensionId() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?extensionId={extensionId}", "foo.bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryExtensionVersion() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?extensionId={id}&extensionVersion={version}", "foo.bar", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryExtensionUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?extensionUuid={extensionUuid}", "5678"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryNamespaceUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/-/query?namespaceUuid={namespaceUuid}", "1234"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryMultipleTargets() throws Exception {
        var versions = mockExtensionVersionTargetPlatforms();
        var query = new QueryRequest(
                null,
                null,
                null,
                null,
                null,
                "1234",
                false,
                null,
                100,
                0
        );
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/-/query?namespaceUuid={namespaceUuid}", "1234"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("darwin-x64");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("linux-x64");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("alpine-arm64");
                })));
    }

    @Test
    void testGetQueryV2ExtensionName() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/v2/-/query?extensionName={extensionName}", "bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryV2Namespace() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/v2/-/query?namespaceName={namespaceName}", "foo"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryV2UnknownExtension() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/v2/-/query?extensionName={extensionName}", "baz"))
                .andExpect(status().isOk())
                .andExpect(content().json("{ \"extensions\": [] }"));
    }

    @Test
    void testGetQueryV2InactiveExtension() throws Exception {
        var namespaceName = "foo";
        var extensionName = "bar";

        mockInactiveExtensionVersion(namespaceName, extensionName);
        mockMvc.perform(get("/api/v2/-/query?extensionId={namespaceName}.{extensionName}", namespaceName, extensionName))
                .andExpect(status().isOk())
                .andExpect(content().json("{ \"extensions\": [] }"));
    }

    @Test
    void testGetQueryV2ExtensionId() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}", "foo.bar"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryV2IncludeAllVersionsTrue() throws Exception {
        var versions = mockExtensionVersionVersionsTargetPlatforms();
        var query = new QueryRequest(
                "foo",
                "bar",
                null,
                null,
                null,
                null,
                true,
                null,
                100,
                0
        );
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}", "foo.bar", "true"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("darwin-x64");
                    e.setUrl("http://localhost/api/foo/bar/darwin-x64/1.0.0");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("darwin-x64");
                    e.setUrl("http://localhost/api/foo/bar/darwin-x64/2.0.0");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("linux-x64");
                    e.setUrl("http://localhost/api/foo/bar/linux-x64/1.0.0");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("linux-x64");
                    e.setUrl("http://localhost/api/foo/bar/linux-x64/2.0.0");
                })));
    }

    @Test
    void testGetQueryV2IncludeAllVersionsFalse() throws Exception {
        var versions = mockExtensionVersionVersions();
        var query = new QueryRequest(
                "foo",
                "bar",
                null,
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );
        versions = versions.stream().filter(ev -> ev.getVersion().equals("3.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}", "foo.bar", "false"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("3.0.0");
                    e.setVersionAlias(List.of("latest"));
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setUrl("http://localhost/api/foo/bar/universal/3.0.0");
                })));
    }

    @Test
    void testGetQueryV2IncludeAllVersionsLinks() throws Exception {
        var versions = mockExtensionVersionVersions();
        var query = new QueryRequest(
                "foo",
                "bar",
                null,
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );
        versions = versions.stream().filter(ev -> ev.getVersion().equals("3.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}", "foo.bar", "links"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("3.0.0");
                    e.setVersionAlias(List.of("latest"));
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setAllVersions(Map.of(
                            "latest", "http://localhost/api/foo/bar/latest",
                            "3.0.0", "http://localhost/api/foo/bar/3.0.0",
                            "2.0.0", "http://localhost/api/foo/bar/2.0.0",
                            "1.0.0", "http://localhost/api/foo/bar/1.0.0"
                    ));
                    e.setUrl("http://localhost/api/foo/bar/universal/3.0.0");
                })));
    }

    @Test
    void testGetQueryV2MultipleTargetsIncludeAllVersionsLinks() throws Exception {
        var versions = mockExtensionVersionVersionsTargetPlatforms();
        var query = new QueryRequest(
                "foo",
                "bar",
                null,
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}", "foo.bar", "links"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setTargetPlatform("darwin-x64");
                    e.setVersionAlias(List.of("latest"));
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setAllVersions(Map.of(
                            "latest", "http://localhost/api/foo/bar/latest",
                            "2.0.0", "http://localhost/api/foo/bar/2.0.0",
                            "1.0.0", "http://localhost/api/foo/bar/1.0.0"
                    ));
                    e.setUrl("http://localhost/api/foo/bar/darwin-x64/2.0.0");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setTargetPlatform("linux-x64");
                    e.setVersionAlias(List.of("latest"));
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setAllVersions(Map.of(
                            "latest", "http://localhost/api/foo/bar/latest",
                            "2.0.0", "http://localhost/api/foo/bar/2.0.0",
                            "1.0.0", "http://localhost/api/foo/bar/1.0.0"
                    ));
                    e.setUrl("http://localhost/api/foo/bar/linux-x64/2.0.0");
                })));
    }

    @Test
    void testGetQueryV2TargetPlatformIncludeAllVersionsTrue() throws Exception {
        var versions = mockExtensionVersionVersionsTargetPlatforms("linux-x64");
        var query = new QueryRequest(
                "foo",
                "bar",
                null,
                null,
                null,
                null,
                true,
                "linux-x64",
                100,
                0
        );
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&targetPlatform={targetPlatform}&includeAllVersions={includeAllVersions}", "foo.bar", "linux-x64", "true"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setTargetPlatform("linux-x64");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setUrl("http://localhost/api/foo/bar/linux-x64/1.0.0");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setTargetPlatform("linux-x64");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setUrl("http://localhost/api/foo/bar/linux-x64/2.0.0");
                })));
    }

    @Test
    void testGetQueryV2ExtensionVersionIncludeAllVersionsTrue() throws Exception {
        var versions = mockExtensionVersionVersionsTargetPlatforms();
        var query = new QueryRequest(
                "foo",
                "bar",
                "2.0.0",
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&extensionVersion={extensionVersion}&includeAllVersions={includeAllVersions}", "foo.bar", "2.0.0", "true"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setTargetPlatform("darwin-x64");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setUrl("http://localhost/api/foo/bar/darwin-x64/2.0.0");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setTargetPlatform("linux-x64");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setUrl("http://localhost/api/foo/bar/linux-x64/2.0.0");
                })));
    }

    @Test
    void testGetQueryV2ExtensionVersionIncludeAllVersionsFalse() throws Exception {
        var versions = mockExtensionVersionVersions();
        var query = new QueryRequest(
                "foo",
                "bar",
                "2.0.0",
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&extensionVersion={extensionVersion}&includeAllVersions={includeAllVersions}", "foo.bar", "2.0.0", "false"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setUrl("http://localhost/api/foo/bar/universal/2.0.0");
                })));
    }

    @Test
    void testGetQueryV2ExtensionVersionIncludeAllVersionsLinks() throws Exception {
        var versions = mockExtensionVersionVersions();
        var query = new QueryRequest(
                "foo",
                "bar",
                "2.0.0",
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?extensionId={extensionId}&extensionVersion={extensionVersion}&includeAllVersions={includeAllVersions}", "foo.bar", "2.0.0", "links"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setAllVersions(Map.of(
                            "latest", "http://localhost/api/foo/bar/latest",
                            "3.0.0", "http://localhost/api/foo/bar/3.0.0",
                            "2.0.0", "http://localhost/api/foo/bar/2.0.0",
                            "1.0.0", "http://localhost/api/foo/bar/1.0.0"
                    ));
                    e.setUrl("http://localhost/api/foo/bar/universal/2.0.0");
                })));
    }

    @Test
    void testGetQueryV2ExtensionUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/v2/-/query?extensionUuid={extensionUuid}", "5678"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryV2NamespaceUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(get("/api/v2/-/query?namespaceUuid={namespaceUuid}", "1234"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                })));
    }

    @Test
    void testGetQueryV2MultipleTargets() throws Exception {
        var versions = mockExtensionVersionTargetPlatforms();
        var query = new QueryRequest(
                null,
                null,
                null,
                null,
                null,
                "1234",
                false,
                null,
                100,
                0
        );
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(get("/api/v2/-/query?namespaceUuid={namespaceUuid}", "1234"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("darwin-x64");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("linux-x64");
                },
                e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setTargetPlatform("alpine-arm64");
                })));
    }

    @Test
    void testPostQueryExtensionName() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(post("/api/-/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"extensionName\": \"bar\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://localhost/api/-/query?extensionName=bar&includeAllVersions=false"));
    }

    @Test
    void testPostQueryNamespace() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(post("/api/-/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"namespaceName\": \"foo\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://localhost/api/-/query?namespaceName=foo&includeAllVersions=false"));
    }

    @Test
    void testPostQueryExtensionId() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(post("/api/-/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"extensionId\": \"foo.bar\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://localhost/api/-/query?extensionId=foo.bar&includeAllVersions=false"));
    }

    @Test
    void testPostQueryExtensionUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(post("/api/-/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"extensionUuid\": \"5678\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://localhost/api/-/query?extensionUuid=5678&includeAllVersions=false"));
    }

    @Test
    void testPostQueryNamespaceUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(post("/api/-/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"namespaceUuid\": \"1234\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "http://localhost/api/-/query?namespaceUuid=1234&includeAllVersions=false"));
    }

    @Test
    void testCreateNamespace() throws Exception {
        var token = mockAccessToken();
        // Mock findMemberships(user) for similarity check during namespace creation
        Mockito.when(repositories.findMemberships(token.getUser()))
                .thenReturn(Streamable.empty());
        mockMvc.perform(post("/api/-/namespace/create?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); })))
                .andExpect(status().isCreated())
                .andExpect(redirectedUrl("http://localhost/api/foobar"))
                .andExpect(content().json(successJson("Created namespace foobar")));
    }

    @Test
    void testCreateNamespaceNoName() throws Exception {
        mockAccessToken();
        mockMvc.perform(post("/api/-/namespace/create?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> {})))
                .andExpect(status().isOk())
                .andExpect(content().json(errorJson("Missing required property 'name'.")));
    }

    @Test
    void testCreateNamespaceInvalidName() throws Exception {
        mockAccessToken();
        mockMvc.perform(post("/api/-/namespace/create?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foo.bar"); })))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid namespace name: foo.bar")));
    }

    @Test
    void testCreateNamespaceInactiveToken() throws Exception {
        var token = mockAccessToken();
        token.setActive(false);
        mockMvc.perform(post("/api/-/namespace/create?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); })))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testCreateExistingNamespace() throws Exception {
        mockAccessToken();
        Mockito.when(repositories.findNamespaceName("foobar"))
                .thenReturn("foobar");

        mockMvc.perform(post("/api/-/namespace/create?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.setName("foobar"); })))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Namespace already exists: foobar")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "contributor", "sole-contributor"})
    void testVerifyToken(String mode) throws Exception {
        mockForPublish(mode);

        mockMvc.perform(get("/api/{namespace}/verify-pat?token={token}", "foo", "my_token"))
                .andExpect(status().isOk());
    }

    @Test
    void testVerifyTokenNoNamespace() throws Exception {
        mockAccessToken();

        mockMvc.perform(get("/api/{namespace}/verify-pat?token={token}", "unexistingnamespace", "my_token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testVerifyTokenInvalid() throws Exception {
        mockForPublish("invalid");

        mockMvc.perform(get("/api/{namespace}/verify-pat?token={token}", "foo", "my_token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testVerifyTokenNoToken() throws Exception {
        mockAccessToken();
        mockNamespace();

        mockMvc.perform(get("/api/{namespace}/verify-pat", "foobar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testVerifyTokenNoPermission() throws Exception {
        mockAccessToken();
        mockNamespace();

        mockMvc.perform(get("/api/{namespace}/verify-pat?token={token}", "foobar", "my_token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPublishOrphan() throws Exception {
        mockForPublish("orphan");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Insufficient access rights for publisher: foo")));
    }

    @Test
    void testPublishRequireLicenseNone() throws Exception {
        var previousRequireLicense = extensionService.isLicenseRequired();
        try {
            extensionService.setLicenseRequired(true);
            mockForPublish("contributor");
            var bytes = createExtensionPackage("bar", "1.0.0", null);
            mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(bytes))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json(errorJson("This extension cannot be accepted because it has no license.")));
        } finally {
            extensionService.setLicenseRequired(previousRequireLicense);
        }
    }

    @Test
    void testPublishRequireLicenseOk() throws Exception {
        var previousRequireLicense = extensionService.isLicenseRequired();
        try {
            extensionService.setLicenseRequired(true);
            mockForPublish("contributor");
            mockActiveVersion();
            var bytes = createExtensionPackage("bar", "1.0.0", "MIT");
            mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(bytes))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(extensionJson(e -> {
                        e.setNamespace("foo");
                        e.setName("bar");
                        e.setVersion("1.0.0");
                        var u = new UserJson();
                        u.setLoginName("test_user");
                        e.setPublishedBy(u);
                        e.setVerified(true);
                        e.setDownloadable(true);
                    })));
        } finally {
            extensionService.setLicenseRequired(previousRequireLicense);
        }
    }

    @Test
    void testPublishInactiveToken() throws Exception {
        mockForPublish("invalid");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testPublishUnknownNamespace() throws Exception {
        mockAccessToken();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Unknown publisher: foo"
                        + "\nUse the 'create-namespace' command to create a namespace corresponding to your publisher name.")));
    }

    @Test
    void testPublishVerifiedOwner() throws Exception {
        mockForPublish("owner");
        mockActiveVersion();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    var u = new UserJson();
                    u.setLoginName("test_user");
                    e.setPublishedBy(u);
                    e.setVerified(true);
                    e.setDownloadable(true);
                })));
    }

    @Test
    void testPublishVerifiedContributor() throws Exception {
        mockForPublish("contributor");
        mockActiveVersion();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    var u = new UserJson();
                    u.setLoginName("test_user");
                    e.setPublishedBy(u);
                    e.setVerified(true);
                    e.setDownloadable(true);
                })));
    }

    @Test
    void testPublishSoleContributor() throws Exception {
        mockForPublish("sole-contributor");
        mockActiveVersion();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    var u = new UserJson();
                    u.setLoginName("test_user");
                    e.setPublishedBy(u);
                    e.setVerified(false);
                    e.setDownloadable(true);
                })));
    }

    @Test
    void testPublishRestrictedPrivileged() throws Exception {
        mockForPublish("privileged");
        mockActiveVersion();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(content().json(extensionJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("1.0.0");
                    var u = new UserJson();
                    u.setLoginName("test_user");
                    e.setPublishedBy(u);
                    e.setVerified(true);
                    e.setDownloadable(true);
                })));
    }

    @Test
    void testPublishRestrictedUnrelated() throws Exception {
        mockForPublish("unrelated");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Insufficient access rights for publisher: foo")));
    }

    @Test
    void testPublishExistingExtension() throws Exception {
        mockForPublish("existing");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Extension foo.bar 1.0.0 is already published.")));
    }

    @Test
    void testPublishSameVersionDifferentTargetPlatformPreRelease() throws Exception {
        var extVersion = mockExtension(TargetPlatform.NAME_WIN32_X64);
        extVersion.setVersion("1.0.0");
        extVersion.setPreRelease(false);

        mockForPublish("contributor");
        Mockito.when(repositories.hasSameVersion(any(ExtensionVersion.class)))
                .thenAnswer((Answer<Boolean>) invocation -> {
                    var extensionVersion = invocation.<ExtensionVersion>getArgument(0);
                    return extensionVersion.getVersion().equals(extVersion.getVersion());
                });

        var bytes = createExtensionPackage("bar", "1.0.0", null, true, TargetPlatform.NAME_LINUX_X64);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(content().json(warningJson("A stable release already exists for foo.bar 1.0.0.\n" +
                        "To prevent update conflicts, we recommend that this pre-release uses 1.1.0 as its version instead.")));
    }

    @Test
    void testPublishSameVersionDifferentTargetPlatformStableRelease() throws Exception {
        var extVersion = mockExtension(TargetPlatform.NAME_DARWIN_ARM64);
        extVersion.setVersion("1.5.0");
        extVersion.setPreRelease(true);

        mockForPublish("contributor");
        Mockito.when(repositories.hasSameVersion(any(ExtensionVersion.class)))
                .thenAnswer((Answer<Boolean>) invocation -> {
                    var extensionVersion = invocation.<ExtensionVersion>getArgument(0);
                    return extensionVersion.getVersion().equals(extVersion.getVersion());
                });

        var bytes = createExtensionPackage("bar", "1.5.0", null, false, TargetPlatform.NAME_ALPINE_ARM64);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(content().json(warningJson("A pre-release already exists for foo.bar 1.5.0.\n" +
                        "To prevent update conflicts, we recommend that this stable release uses 1.6.0 as its version instead.")));
    }

    @Test
    void testPublishInvalidName() throws Exception {
        mockForPublish("contributor");
        var bytes = createExtensionPackage("b.a.r", "1.0.0", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid extension name: b.a.r")));
    }

    @Test
    void testPublishInvalidVersion() throws Exception {
        mockForPublish("contributor");
        var bytes = createExtensionPackage("bar", "latest", null);
        mockMvc.perform(post("/api/-/publish?token={token}", "my_token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("The version string 'latest' is reserved.")));
    }

    @Test
    void testPostReview() throws Exception {
        var user = mockUserData();
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        Mockito.when(repositories.findExtension("bar", "foo"))
                .thenReturn(extension);
        Mockito.when(repositories.findActiveReviews(extension, user))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.findActiveReviews(extension))
                .thenReturn(Streamable.empty());

        mockMvc.perform(post("/api/{namespace}/{extension}/review", "foo", "bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson(r -> {
                    r.setRating(3);
                }))
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(content().json(successJson("Added review for foo.bar")));
    }

    @Test
    void testPostReviewNotLoggedIn() throws Exception {
        mockMvc.perform(post("/api/{namespace}/{extension}/review", "foo", "bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson(r -> {
                    r.setRating(3);
                })).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPostReviewInvalidRating() throws Exception {
        mockMvc.perform(post("/api/{namespace}/{extension}/review", "foo", "bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson(r -> {
                    r.setRating(100);
                }))
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("The rating must be an integer number between 0 and 5.")));
    }

    @Test
    void testPostReviewUnknownExtension() throws Exception {
        mockUserData();
        mockMvc.perform(post("/api/{namespace}/{extension}/review", "foo", "bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson(r -> {
                    r.setRating(3);
                }))
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Extension not found: foo.bar")));
    }

    @Test
    void testPostExistingReview() throws Exception {
        var user = mockUserData();
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        Mockito.when(repositories.findExtension("bar", "foo"))
                .thenReturn(extension);
        Mockito.when(repositories.hasActiveReview(extension, user))
                .thenReturn(true);

        mockMvc.perform(post("/api/{namespace}/{extension}/review", "foo", "bar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson(r -> {
                    r.setRating(3);
                }))
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("You must not submit more than one review for an extension.")));
    }

    @Test
    void testDeleteReview() throws Exception {
        var user = mockUserData();
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        Mockito.when(repositories.findExtension("bar", "foo"))
                .thenReturn(extension);
        var review = new ExtensionReview();
        review.setExtension(extension);
        review.setUser(user);
        review.setActive(true);
        Mockito.when(repositories.findActiveReviews(extension, user))
                .thenReturn(Streamable.of(review));
        Mockito.when(repositories.findActiveReviews(extension))
                .thenReturn(Streamable.empty());

        mockMvc.perform(post("/api/{namespace}/{extension}/review/delete", "foo", "bar")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted review for foo.bar")));
    }

    @Test
    void testDeleteReviewNotLoggedIn() throws Exception {
        mockMvc.perform(post("/api/{namespace}/{extension}/review/delete", "foo", "bar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteReviewUnknownExtension() throws Exception {
        mockUserData();
        mockMvc.perform(post("/api/{namespace}/{extension}/review/delete", "foo", "bar")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Extension not found: foo.bar")));
    }

    @Test
    void testDeleteNonExistingReview() throws Exception {
        var user = mockUserData();
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        Mockito.when(repositories.findExtension("bar", "foo"))
                .thenReturn(extension);
        Mockito.when(repositories.findActiveReviews(extension, user))
                .thenReturn(Streamable.empty());

        mockMvc.perform(post("/api/{namespace}/{extension}/review/delete", "foo", "bar")
                .with(user("test_user"))
                .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("You have not submitted any review yet.")));
    }


    //---------- UTILITY ----------//

    private void mockActiveVersion() {
        var namespace = new Namespace();
        namespace.setName("foo");
        var extension = new Extension();
        extension.setId(1);
        extension.setName("bar");
        extension.setActive(true);
        extension.setNamespace(namespace);
        var extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setActive(true);
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);
        extension.getVersions().add(extVersion);
    }

    private Namespace mockNamespace() {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.findActiveExtensions(namespace))
                .thenReturn(Streamable.empty());
        return namespace;
    }

    private String namespaceJson(Consumer<NamespaceJson> content) throws JsonProcessingException {
        var json = new NamespaceJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private void mockInactiveExtensionVersion(String namespaceName, String extensionName) {
        var query = new QueryRequest(
                namespaceName,
                extensionName,
                null,
                null,
                null,
                null,
                false,
                null,
                100,
                0
        );

        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(Collections.emptyList(), Pageable.ofSize(query.size()), 0));
    }

    private List<ExtensionVersion> mockExtensionVersionVersionsTargetPlatforms() {
        var values = List.of(
                "1.0.0@darwin-x64", "2.0.0@darwin-x64",
                "1.0.0@linux-x64", "2.0.0@linux-x64"
        );

        return mockExtensionVersions(null, values, (ev, value) -> {
            var pieces = value.split("@");
            ev.setVersion(pieces[0]);
            ev.setTargetPlatform(pieces[1]);
            ev.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
            ev.setDisplayName("Foo Bar");
            return ev;
        });
    }

    private List<ExtensionVersion> mockExtensionVersionVersionsTargetPlatforms(String targetPlatform) {
        var versions = List.of("1.0.0", "2.0.0");
        var values = versions.stream().map(version -> version + "@" + targetPlatform).collect(Collectors.toList());
        return mockExtensionVersions(targetPlatform, values, (ev, value) -> {
            var pieces = value.split("@");
            ev.setVersion(pieces[0]);
            ev.setTargetPlatform(pieces[1]);
            ev.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
            ev.setDisplayName("Foo Bar");
            return ev;
        });
    }

    private List<ExtensionVersion> mockExtensionVersionVersions() {
        var versions = List.of("1.0.0", "2.0.0", "3.0.0");
        return mockExtensionVersions(null, versions, (ev, version) -> {
            ev.setVersion(version);
            ev.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            ev.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
            ev.setDisplayName("Foo Bar");
            return ev;
        });
    }

    private List<ExtensionVersion> mockExtensionVersionTargetPlatforms() {
        var targetPlatforms = List.of("darwin-x64", "linux-x64", "alpine-arm64");
        return mockExtensionVersions(null, targetPlatforms, (ev, targetPlatform) -> {
            ev.setVersion("1.0.0");
            ev.setTargetPlatform(targetPlatform);
            ev.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
            ev.setDisplayName("Foo Bar");
            return ev;
        });
    }

    private List<ExtensionVersion> mockExtensionVersions(String targetPlatform, List<String> values, BiFunction<ExtensionVersion, String, ExtensionVersion> setter) {
        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setPublicId("1234");
        namespace.setName("foo");

        var extension = new Extension();
        extension.setId(2L);
        extension.setName("bar");
        extension.setNamespace(namespace);

        var versions = new ArrayList<ExtensionVersion>();
        for(var i = 0; i < values.size(); i++) {
            var extVersion = new ExtensionVersion();
            extVersion.setId(3 + i);
            extVersion = setter.apply(extVersion, values.get(i));
            extVersion.setExtension(extension);
            versions.add(extVersion);
        }

        Mockito.when(repositories.findActiveExtensionVersions(Set.of(extension.getId()), null))
                .thenReturn(versions);
        Mockito.when(repositories.findLatestVersionsIsPreview(Set.of(extension.getId())))
                .thenReturn(Map.of(extension.getId(), versions.get(0).isPreview()));
        Mockito.when(repositories.findActiveVersionStringsSorted(Set.of(extension.getId()), null))
                .thenReturn(versions.stream().collect(Collectors.groupingBy(ev -> ev.getExtension().getId(), Collectors.mapping(ev -> ev.getVersion(), Collectors.toList()))));
        Mockito.when(repositories.findVersionStringsSorted(extension, targetPlatform, true))
                .thenReturn(versions.stream().map(ExtensionVersion::getVersion).collect(Collectors.toList()));

        var fileTypes = List.of(DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG);
        Mockito.when(repositories.findFileResourcesByExtensionVersionIdAndType(List.of(3L), fileTypes))
                .thenReturn(Collections.emptyList());
        Mockito.when(repositories.findNamespaceMemberships(List.of(namespace.getId())))
                .thenReturn(Collections.emptyList());

        return versions;
    }

    private void mockExtensionVersion() {
        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setPublicId("1234");
        namespace.setName("foo");

        var extension = new Extension();
        extension.setId(2L);
        extension.setPublicId("5678");
        extension.setName("bar");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setId(3L);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        extVersion.setDisplayName("Foo Bar");
        extVersion.setExtension(extension);

        Mockito.when(repositories.findActiveExtensionVersions(Set.of(extension.getId()), null))
                .thenReturn(List.of(extVersion));

        Mockito.when(repositories.findLatestVersionsIsPreview(Set.of(extension.getId())))
                .thenReturn(Map.of(extension.getId(), extVersion.isPreview()));

        Mockito.when(repositories.findActiveVersions(any(QueryRequest.class)))
                .then((Answer<Page<ExtensionVersion>>) invocation -> {
                    var request = invocation.getArgument(0, QueryRequest.class);
                    var versions = namespace.getPublicId().equals(request.namespaceUuid())
                            || namespace.getName().equals(request.namespaceName())
                            || extension.getPublicId().equals(request.extensionUuid())
                            || extension.getName().equals(request.extensionName())
                            ? List.of(extVersion)
                            : Collections.<ExtensionVersion>emptyList();

                    return new PageImpl<>(versions, Pageable.ofSize(100), versions.size());
                });

        var fileTypes = List.of(DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG);
        Mockito.when(repositories.findFileResourcesByExtensionVersionIdAndType(Set.of(extVersion.getId()), fileTypes))
                .thenReturn(Collections.emptyList());
        Mockito.when(repositories.findNamespaceMemberships(List.of(namespace.getId())))
                .thenReturn(Collections.emptyList());
    }

    private ExtensionVersion mockExtensionWithSignature() {
        return mockExtension(TargetPlatform.NAME_UNIVERSAL, true);
    }

    private ExtensionVersion mockExtension() {
        return mockExtension(TargetPlatform.NAME_UNIVERSAL);
    }

    private ExtensionVersion mockExtension(String targetPlatform) {
        return mockExtension(targetPlatform, false);
    }

    private ExtensionVersion mockExtension(String targetPlatform, boolean withSignature) {
        var namespace = new Namespace();
        namespace.setName("foo");
        namespace.setPublicId("1234");
        var extension = new Extension();
        extension.setName("bar");
        extension.setId(extension.getName().hashCode());
        extension.setNamespace(namespace);
        extension.setPublicId("5678");
        extension.setActive(true);
        var extVersion = new ExtensionVersion();
        extVersion.setTargetPlatform(targetPlatform);
        extVersion.setVersion("1.0.0");
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        extVersion.setActive(true);
        extVersion.setDisplayName("Foo Bar");
        extVersion.setExtension(extension);
        extension.getVersions().add(extVersion);
        Mockito.when(entityManager.merge(extension)).thenReturn(extension);
        Mockito.when(repositories.findExtension("bar", "foo"))
                .thenReturn(extension);
        Mockito.when(repositories.findVersion("1.0.0", targetPlatform, "bar", "foo"))
                .thenReturn(extVersion);
        Mockito.when(repositories.findVersions(extension))
                .thenReturn(Streamable.of(extVersion));
        Mockito.when(repositories.findActiveExtensions(namespace))
                .thenReturn(Streamable.of(extension));
        Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                .thenReturn(false);
        Mockito.when(repositories.countActiveReviews(extension))
                .thenReturn(0L);
        Mockito.when(repositories.findNamespace("foo"))
                .thenReturn(namespace);

        var download = new FileResource();
        download.setExtension(extVersion);
        download.setType(DOWNLOAD);
        download.setStorageType(STORAGE_LOCAL);
        download.setName("extension-1.0.0.vsix");
        var signature = new FileResource();
        if(withSignature) {
            signature.setExtension(extVersion);
            signature.setType(DOWNLOAD_SIG);
            signature.setStorageType(STORAGE_LOCAL);
            signature.setName("extension-1.0.0.sigzip");
        }
        Mockito.when(entityManager.merge(download)).thenReturn(download);
        Mockito.when(repositories.findFilesByType(anyCollection(), anyCollection())).thenAnswer(invocation -> {
            Collection<ExtensionVersion> extVersions = invocation.getArgument(0);
            Collection<String> types = invocation.getArgument(1);
            var extensionVersion = extVersions.iterator().hasNext()
                    ? extVersions.iterator().next()
                    : null;

            var files = new ArrayList<>();
            if(types.contains(DOWNLOAD) && download.getExtension().equals(extensionVersion)) {
                files.add(download);
            }
            if(withSignature && types.contains(DOWNLOAD_SIG) && signature.getExtension().equals(extensionVersion)) {
                files.add(signature);
            }

            return files;
        });

        return extVersion;
    }

    private String extensionJson(Consumer<ExtensionJson> content) throws JsonProcessingException {
        var json = new ExtensionJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String queryResultJson(Consumer<ExtensionJson>... contents) throws JsonProcessingException {
        var extensionJsons = new ArrayList<String>();
        for(var content : contents) {
            extensionJsons.add(extensionJson(content));
        }

        return "{\"extensions\":[" + String.join(",", extensionJsons) + "]}";
    }

    private Path mockReadme() throws IOException {
        return mockReadme(TargetPlatform.NAME_UNIVERSAL);
    }

    private Path mockReadme(String targetPlatform) throws IOException {
        var extVersion = mockExtension(targetPlatform);
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName("README");
        resource.setType(FileResource.README);
        resource.setStorageType(STORAGE_LOCAL);
        Mockito.when(entityManager.find(FileResource.class, resource.getId())).thenReturn(resource);
        Mockito.when(repositories.findFileByType("foo", "bar", targetPlatform, "1.0.0", README)).thenReturn(resource);

        var segments = new String[]{ "foo", "bar" };
        if(!targetPlatform.equals(TargetPlatform.NAME_UNIVERSAL)) {
            segments = ArrayUtils.add(segments, targetPlatform);
        }

        segments = ArrayUtils.add(segments, "1.0.0");
        segments = ArrayUtils.add(segments, "README");
        var path = Path.of("/tmp", segments);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "Please read me");
        return path;
    }

    private Path mockChangelog() throws IOException {
        var extVersion = mockExtension();
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName("CHANGELOG");
        resource.setType(FileResource.CHANGELOG);
        resource.setStorageType(FileResource.STORAGE_LOCAL);
        Mockito.when(entityManager.find(FileResource.class, resource.getId())).thenReturn(resource);
        Mockito.when(repositories.findFileByType("foo", "bar", "universal", "1.0.0", CHANGELOG)).thenReturn(resource);

        var path = Path.of("/tmp", "foo", "bar", "1.0.0", "CHANGELOG");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "All notable changes is documented here");
        return path;
    }

    private Path mockLicense() throws IOException {
        var extVersion = mockExtension();
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName("LICENSE");
        resource.setType(FileResource.LICENSE);
        resource.setStorageType(FileResource.STORAGE_LOCAL);
        Mockito.when(entityManager.find(FileResource.class, resource.getId())).thenReturn(resource);
        Mockito.when(repositories.findFileByType("foo", "bar", "universal", "1.0.0", LICENSE)).thenReturn(resource);

        var path = Path.of("/tmp", "foo", "bar", "1.0.0", "LICENSE");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "I never broke the Law! I am the law!");
        return path;
    }

    private Path mockLatest() throws IOException {
        var extVersion = mockExtension();
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName("DOWNLOAD");
        resource.setType(FileResource.DOWNLOAD);
        resource.setStorageType(STORAGE_LOCAL);
        Mockito.when(entityManager.find(FileResource.class, resource.getId())).thenReturn(resource);
        Mockito.when(repositories.findFileByType("foo", "bar", "universal", "latest", FileResource.DOWNLOAD))
                .thenReturn(resource);

        var path = Path.of("/tmp", "foo", "bar", "1.0.0", "DOWNLOAD");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "latest download");
        return path;
    }

    private void mockReviews() {
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        var user1 = new UserData();
        user1.setLoginName("user1");
        var review1 = new ExtensionReview();
        review1.setExtension(extension);
        review1.setUser(user1);
        review1.setRating(3);
        review1.setComment("Somewhat ok");
        review1.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        review1.setActive(true);
        var user2 = new UserData();
        user2.setLoginName("user2");
        var review2 = new ExtensionReview();
        review2.setExtension(extension);
        review2.setUser(user2);
        review2.setRating(4);
        review2.setComment("Quite good");
        review2.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        review2.setActive(true);
        Mockito.when(repositories.findActiveReviews(extension))
                .thenReturn(Streamable.of(review1, review2));
    }

    private String reviewsJson(Consumer<ReviewListJson> content) throws JsonProcessingException {
        var json = new ReviewListJson();
        json.setReviews(new ArrayList<>());
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private List<ExtensionVersion> mockSearch() {
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        extension.setId(1L);
        var entry1 = new ExtensionSearch();
        entry1.setId(1);
        Mockito.when(search.isEnabled())
                .thenReturn(true);
        var searchResult = new SearchResult(1, List.of(entry1));
        var searchOptions = new ISearchService.Options("foo", null, null, 10, 0, "desc", SortBy.RELEVANCE, false, null);
        Mockito.when(search.search(searchOptions))
                .thenReturn(searchResult);

        var publisherSearchOptions = new ISearchService.Options("", null, null, 10, 0, "desc", SortBy.RELEVANCE, false, null, "foo");
        Mockito.when(search.search(publisherSearchOptions))
                .thenReturn(searchResult);

        var publisherWithQuerySearchOptions = new ISearchService.Options("bar", null, null, 10, 0, "desc", SortBy.RELEVANCE, false, null, "foo");
        Mockito.when(search.search(publisherWithQuerySearchOptions))
                .thenReturn(searchResult);

        var publisherWithMoreQuerySearchOptions = new ISearchService.Options("bar code", null, null, 10, 0, "desc", SortBy.RELEVANCE, false, null, "foo");
        Mockito.when(search.search(publisherWithMoreQuerySearchOptions))
                .thenReturn(searchResult);

        return List.of(extVersion);
    }

    private String searchJson(Consumer<SearchResultJson> content) throws JsonProcessingException {
        var json = new SearchResultJson();
        json.setExtensions(new ArrayList<>());
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private PersonalAccessToken mockAccessToken() {
        var userData = new UserData();
        userData.setLoginName("test_user");
        var token = new PersonalAccessToken();
        token.setUser(userData);
        token.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token.setValue("my_token");
        token.setActive(true);
        Mockito.when(repositories.findAccessToken("my_token"))
                .thenReturn(token);
        return token;
    }

    private void mockForPublish(String mode) {
        var token = mockAccessToken();
        if (mode.equals("invalid")) {
            token.setActive(false);
        }
        var namespace = new Namespace();
        namespace.setName("foo");
        Mockito.when(repositories.findNamespace("foo"))
                .thenReturn(namespace);
        if (mode.equals("existing")) {
            var extension = new Extension();
            extension.setName("bar");
            var extVersion = new ExtensionVersion();
            extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            extVersion.setVersion("1.0.0");
            extVersion.setActive(true);
            Mockito.when(repositories.findExtension("bar", namespace))
                    .thenReturn(extension);
            Mockito.when(repositories.findVersion("1.0.0", TargetPlatform.NAME_UNIVERSAL, extension))
                    .thenReturn(extVersion);
        }
        Mockito.when(repositories.countActiveReviews(any(Extension.class)))
                .thenReturn(0L);
        Mockito.when(repositories.findVersions(any(Extension.class)))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.findFilesByType(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());
        if (mode.equals("owner")) {
            var ownerMem = new NamespaceMembership();
            ownerMem.setUser(token.getUser());
            ownerMem.setNamespace(namespace);
            ownerMem.setRole(NamespaceMembership.ROLE_OWNER);
            Mockito.when(repositories.findMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(Streamable.of(ownerMem));
            Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(true);
            Mockito.when(repositories.canPublishInNamespace(token.getUser(), namespace))
                    .thenReturn(true);
            Mockito.when(repositories.isVerified(namespace, token.getUser()))
                    .thenReturn(true);
            // Mock findMemberships(user) for similarity check
            Mockito.when(repositories.findMemberships(token.getUser()))
                    .thenReturn(Streamable.of(ownerMem));
        } else if (mode.equals("contributor") || mode.equals("sole-contributor") || mode.equals("existing")) {
            Mockito.when(repositories.canPublishInNamespace(token.getUser(), namespace))
                    .thenReturn(true);
            Mockito.when(repositories.isVerified(namespace, token.getUser()))
                    .thenReturn(true);
            if (mode.equals("contributor")) {
                var otherUser = new UserData();
                otherUser.setLoginName("other_user");
                var ownerMem = new NamespaceMembership();
                ownerMem.setUser(otherUser);
                ownerMem.setNamespace(namespace);
                ownerMem.setRole(NamespaceMembership.ROLE_OWNER);
                Mockito.when(repositories.findMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                        .thenReturn(Streamable.of(ownerMem));
                Mockito.when(repositories.isVerified(namespace, token.getUser()))
                        .thenReturn(true);
                // Mock findMemberships(user) for similarity check - user is a contributor
                var contributorMem = new NamespaceMembership();
                contributorMem.setUser(token.getUser());
                contributorMem.setNamespace(namespace);
                contributorMem.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
                Mockito.when(repositories.findMemberships(token.getUser()))
                        .thenReturn(Streamable.of(contributorMem));
            } else {
                Mockito.when(repositories.findMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(Streamable.empty());
                Mockito.when(repositories.isVerified(namespace, token.getUser()))
                        .thenReturn(false);
                // Mock findMemberships(user) for similarity check - user might be sole contributor
                var contributorMem = new NamespaceMembership();
                contributorMem.setUser(token.getUser());
                contributorMem.setNamespace(namespace);
                contributorMem.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
                Mockito.when(repositories.findMemberships(token.getUser()))
                        .thenReturn(Streamable.of(contributorMem));
            }
        } else if (mode.equals("privileged") || mode.equals("unrelated")) {
            var otherUser = new UserData();
            otherUser.setLoginName("other_user");
            var ownerMem = new NamespaceMembership();
            ownerMem.setUser(otherUser);
            ownerMem.setNamespace(namespace);
            ownerMem.setRole(NamespaceMembership.ROLE_OWNER);
            Mockito.when(repositories.findMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(Streamable.of(ownerMem));
            Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(true);
            if (mode.equals("privileged")) {
                token.getUser().setRole(UserData.ROLE_PRIVILEGED);
                // Mock findMemberships(user) for similarity check - privileged user might have memberships
                Mockito.when(repositories.findMemberships(token.getUser()))
                        .thenReturn(Streamable.empty());
            } else {
                // Mock findMemberships(user) for similarity check - unrelated user has no memberships
                Mockito.when(repositories.findMemberships(token.getUser()))
                        .thenReturn(Streamable.empty());
            }
        } else {
            Mockito.when(repositories.findMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(Streamable.empty());
            Mockito.when(repositories.hasMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                    .thenReturn(false);
            // Mock findMemberships(user) for similarity check - default to empty
            Mockito.when(repositories.findMemberships(token.getUser()))
                    .thenReturn(Streamable.empty());
        }

        Mockito.when(entityManager.merge(any(Extension.class)))
                .then((Answer<Extension>) invocation -> invocation.getArgument(0, Extension.class));
    }

    private String reviewJson(Consumer<ReviewJson> content) throws JsonProcessingException {
        var json = new ReviewJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private UserData mockUserData() {
        var userData = new UserData();
        userData.setLoginName("test_user");
        userData.setFullName("Test User");
        userData.setProviderUrl("http://example.com/test");
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    private String successJson(String message) throws JsonProcessingException {
        var json = ResultJson.success(message);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String errorJson(String message) throws JsonProcessingException {
        var json = ResultJson.error(message);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String warningJson(String message) throws JsonProcessingException {
        var json = ResultJson.warning(message);
        return new ObjectMapper().writeValueAsString(json);
    }

    private byte[] createExtensionPackage(String name, String version, String license) throws IOException {
        return createExtensionPackage(name, version, license, false, null);
    }

    private byte[] createExtensionPackage(String name, String version, String license, boolean preRelease, String targetPlatform) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var archive = new ZipOutputStream(bytes);
        archive.putNextEntry(new ZipEntry("extension.vsixmanifest"));
        var vsixmanifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<PackageManifest Version=\"2.0.0\" xmlns=\"http://schemas.microsoft.com/developer/vsx-schema/2011\" xmlns:d=\"http://schemas.microsoft.com/developer/vsx-schema-design/2011\">" +
            "<Metadata>" +
            "<Identity Language=\"en-US\" Id=\""+ name +"\" Version=\"" + version + "\" Publisher=\"foo\" " + (targetPlatform != null ? "TargetPlatform=\"" + targetPlatform + "\"" : "")  + " />" +
            "<DisplayName>foo</DisplayName>" +
            "<Description xml:space=\"preserve\"></Description>" +
            "<Tags></Tags>" +
            "<Categories>Other</Categories>" +
            "<GalleryFlags>Public</GalleryFlags>" +
            "<Badges></Badges>" +
            "<Properties>" +
            "<Property Id=\"Microsoft.VisualStudio.Code.Engine\" Value=\"^1.57.0\" />" +
            "<Property Id=\"Microsoft.VisualStudio.Code.ExtensionDependencies\" Value=\"\" />" +
            "<Property Id=\"Microsoft.VisualStudio.Code.ExtensionPack\" Value=\"\" />" +
            "<Property Id=\"Microsoft.VisualStudio.Code.ExtensionKind\" Value=\"ui,web,workspace\" />" +
            "<Property Id=\"Microsoft.VisualStudio.Code.LocalizedLanguages\" Value=\"\" />" +
            "<Property Id=\"Microsoft.VisualStudio.Services.GitHubFlavoredMarkdown\" Value=\"true\" />" +
            (preRelease ? "<Property Id=\"Microsoft.VisualStudio.Code.PreRelease\" Value=\"true\" />" : "") +
            "</Properties>" +
            "</Metadata>" +
            "<Installation>" +
            "<InstallationTarget Id=\"Microsoft.VisualStudio.Code\"/>" +
            "</Installation>" +
            "<Dependencies/>" +
            "<Assets>" +
            "<Asset Type=\"Microsoft.VisualStudio.Code.Manifest\" Path=\"extension/package.json\" Addressable=\"true\" />" +
            "</Assets>" +
            "</PackageManifest>";
        archive.write(vsixmanifest.getBytes());
        archive.closeEntry();
        archive.putNextEntry(new ZipEntry("extension/package.json"));
        var packageJson = "{" +
                "\"publisher\": \"foo\"," +
                "\"name\": \"" + name + "\"," +
                "\"version\": \"" + version + "\"" +
                (license == null ? "" : ",\"license\": \"" + license + "\"" ) +
            "}";
        archive.write(packageJson.getBytes());
        archive.closeEntry();
        archive.finish();
        return bytes.toByteArray();
    }

    @TestConfiguration
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate() {
            return new MockTransactionTemplate();
        }

        @Bean
        UserService userService(
                EntityManager entityManager,
                RepositoryService repositories,
                StorageUtilService storageUtil,
                CacheService cache,
                ExtensionValidator validator,
                @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository,
                OAuth2AttributesConfig attributesConfig
        ) {
            return new UserService(entityManager, repositories, storageUtil, cache, validator, clientRegistrationRepository, attributesConfig);
        }

        @Bean
        AccessTokenService tokenService(
                AccessTokenConfig config,
                EntityManager entityManager,
                RepositoryService repositories,
                MailService mailService
        ) {
            return new AccessTokenService(config, entityManager, repositories, mailService);
        }

        @Bean
        OAuth2UserServices oauth2UserServices(
                UserService users,
                EclipseTokenService eclipseTokenService,
                EntityManager entityManager,
                EclipseService eclipse,
                OAuth2AttributesConfig attributesConfig
        ) {
            return new OAuth2UserServices(users, eclipseTokenService, entityManager, eclipse, attributesConfig);
        }

        @Bean
        EclipseTokenService eclipseTokenService(
                TransactionTemplate transactions,
                EntityManager entityManager,
                ClientRegistrationRepository clientRegistrationRepository
        ) {
            return new EclipseTokenService(transactions, entityManager, clientRegistrationRepository);
        }

        @Bean
        LocalRegistryService localRegistryService(
                EntityManager entityManager,
                RepositoryService repositories,
                ExtensionService extensions,
                VersionService versionService,
                UserService users,
                AccessTokenService tokenService,
                SearchUtilService search,
                ExtensionValidator validator,
                StorageUtilService storageUtil,
                EclipseService eclipse,
                CacheService cache,
                ExtensionVersionIntegrityService integrityService,
                SimilarityCheckService similarityCheckService
        ) {
            return new LocalRegistryService(
                    entityManager,
                    repositories,
                    extensions,
                    versionService,
                    users,
                    tokenService,
                    search,
                    validator,
                    storageUtil,
                    eclipse,
                    cache,
                    integrityService,
                    similarityCheckService
            );
        }

        @Bean
        PublishExtensionVersionHandler publishExtensionVersionHandler(
                PublishExtensionVersionService service,
                ExtensionVersionIntegrityService integrityService,
                EntityManager entityManager,
                RepositoryService repositories,
                JobRequestScheduler scheduler,
                UserService users,
                ExtensionValidator validator,
                ExtensionControlService extensionControl,
                ExtensionScanService extensionScanService
        ) {
            return new PublishExtensionVersionHandler(
                    service,
                    integrityService,
                    entityManager,
                    repositories,
                    scheduler,
                    users,
                    validator,
                    extensionControl,
                    extensionScanService
            );
        }

        @Bean
        ExtensionService extensionService(
                EntityManager entityManager,
                RepositoryService repositories,
                SearchUtilService search,
                CacheService cache,
                LogService logs,
                PublishExtensionVersionHandler publishHandler,
                JobRequestScheduler scheduler,
                ExtensionScanService extensionScanService,
                ExtensionScanPersistenceService scanPersistenceService
        ) {
            return new ExtensionService(
                    entityManager,
                    repositories,
                    search,
                    cache,
                    logs,
                    publishHandler,
                    scheduler,
                    extensionScanService,
                    scanPersistenceService
            );
        }

        @Bean
        ExtensionValidator extensionValidator() {
            return new ExtensionValidator();
        }

        @Bean
        StorageUtilService storageUtilService(
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
                    cdnServiceConfig
            );
        }

        @Bean
        LocalStorageService localStorageService() {
            return new LocalStorageService();
        }

        @Bean
        ExtensionJsonCacheKeyGenerator extensionJsonCacheKeyGenerator() { return new ExtensionJsonCacheKeyGenerator(); }

        @Bean
        VersionService versionService() {
            return new VersionService();
        }

        @Bean
        LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKeyGenerator() {
            return new LatestExtensionVersionCacheKeyGenerator();
        }

        @Bean
        SimilarityConfig similarityConfig() {
            return new SimilarityConfig();
        }

        @Bean
        SimilarityService similarityService(RepositoryService repositories) {
            return new SimilarityService(repositories);
        }

        @Bean
        SimilarityCheckService similarityCheckService(
                SimilarityConfig config,
                SimilarityService similarityService,
                RepositoryService repositories
        ) {
            return new SimilarityCheckService(config, similarityService, repositories);
        }
    }
}
