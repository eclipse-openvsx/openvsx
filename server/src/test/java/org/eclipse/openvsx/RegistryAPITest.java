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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.ArrayUtils;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
import org.eclipse.openvsx.metrics.ExtensionDownloadMetrics;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.publish.PublishExtensionVersionHandler;
import org.eclipse.openvsx.publish.PublishExtensionVersionService;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.repositories.ChangesPage;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanPersistenceService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.search.*;
import org.eclipse.openvsx.security.OAuth2AttributesConfig;
import org.eclipse.openvsx.security.OAuth2UserServices;
import org.eclipse.openvsx.security.SecurityConfig;
import org.eclipse.openvsx.storage.*;
import org.eclipse.openvsx.storage.log.DownloadCountService;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;
import org.eclipse.openvsx.util.ChangesCursor;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UUIDService;
import org.eclipse.openvsx.util.VersionAlias;
import org.eclipse.openvsx.util.VersionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.entities.FileResource.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistryAPI.class)
@MockitoBean(
    types = {
        DSLContext.class,
        ClientRegistrationRepository.class,
        UpstreamRegistryService.class,
        GoogleCloudStorageService.class,
        AzureBlobStorageService.class,
        AwsStorageService.class,
        VSCodeIdService.class,
        DownloadCountService.class,
        ExtensionDownloadMetrics.class,
        CacheService.class,
        EclipseService.class,
        PublishExtensionVersionService.class,
        SimpleMeterRegistry.class,
        JobRequestScheduler.class,
        ExtensionControlService.class,
        FileCacheDurationConfig.class,
        CdnServiceConfig.class,
        ExtensionScanPersistenceService.class,
        LogService.class,
        MailService.class
    }
)
class RegistryAPITest {

    /**
     * How far behind the present the changes feed stops in these tests. The default value, so that the
     * bound the endpoint clamps to is the one a deployment would use.
     */
    private static final Duration CHANGES_FEED_LAG = Duration.ofSeconds(30);

    /** The size the mocked extension's .vsix reports, in bytes. */
    private static final long DOWNLOAD_SIZE = 1_234_567L;

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
    PublishingConfig publishingConfig;

    @Autowired
    ExtensionService extensionService;

    @Test
    void testPublicNamespace() throws Exception {
        var namespace = mockNamespace();
        Mockito.when(repositories.isVerified(namespace))
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
        Mockito.when(repositories.isVerified(namespace))
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

    // The size travels from file_resource.size to the JSON untouched, so that a client - the web UI's
    // More Info box among them - can show how big the download is without fetching it.
    @Test
    void testExtensionDownloadSize() throws Exception {
        var extVersion = mockExtension();
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);

        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadSize").value(DOWNLOAD_SIZE));
    }

    // Null for anything published before the column existed that the backfill has not reached yet.
    // Omitted rather than sent as 0, which a client would have no way to tell from a genuinely empty file.
    @Test
    void testExtensionDownloadSizeUnknown() throws Exception {
        var extVersion = mockExtension();
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.LATEST)).thenReturn(extVersion);
        repositories.findFilesByType(List.of(extVersion), List.of(DOWNLOAD)).forEach(file -> file.setSize(null));

        mockMvc.perform(get("/api/{namespace}/{extension}", "foo", "bar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadSize").doesNotExist());
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
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "linux-x64", VersionAlias.LATEST))
                .thenReturn(extVersion);

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
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true))
                .thenReturn(extVersion);

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
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true))
                .thenReturn(extVersion);

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
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "alpine-arm64", VersionAlias.LATEST))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), "alpine-arm64", false, true))
                .thenReturn(extVersion);

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
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.PRE_RELEASE))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, true, true))
                .thenReturn(extVersion);
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
        Mockito.when(repositories.findExtensionVersion("foo", "bar", null, VersionAlias.PRE_RELEASE))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, false, true))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), null, true, true))
                .thenReturn(extVersion);
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
        Mockito.when(repositories.findExtensionVersion("foo", "bar", "web", VersionAlias.PRE_RELEASE))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), "web", false, true))
                .thenReturn(extVersion);
        Mockito.when(repositories.findLatestVersionForAllUrls(extVersion.getExtension(), "web", true, true))
                .thenReturn(extVersion);
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
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Type", containsString("text/plain;charset=UTF-8")))
                .andExpect(
                        header().string(
                                "Content-Security-Policy",
                                containsString(
                                        "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox")))
                .andDo(_ -> Files.delete(filePath));
    }

    @Test
    void testHtmlResourceIsServedAsPlainText() throws Exception {
        // A resource whose filename resolves to a renderable type (e.g. text/html)
        // must come back with the hardened response headers.
        var filePath = mockReadme(TargetPlatform.NAME_UNIVERSAL, "readme.html", "<script>alert(1)</script>");
        mockMvc.perform(
                get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "readme.html"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Type", containsString("text/plain;charset=UTF-8")))
                .andExpect(
                        header().string(
                                "Content-Security-Policy",
                                containsString(
                                        "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox")))
                .andDo(_ -> Files.delete(filePath));
    }

    @Test
    void testReadmeWindowsTarget() throws Exception {
        var filePath = mockReadme("win32-x64");
        mockMvc.perform(
                get(
                        "/api/{namespace}/{extension}/{target}/{version}/file/{fileName}",
                        "foo",
                        "bar",
                        "win32-x64",
                        "1.0.0",
                        "README"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("Please read me"))
                .andDo(_ -> Files.delete(filePath));
    }

    @Test
    void testReadmeUnknownTarget() throws Exception {
        var filePath = mockReadme();
        mockMvc.perform(
                get(
                        "/api/{namespace}/{extension}/{target}/{version}/file/{fileName}",
                        "foo",
                        "bar",
                        "darwin-x64",
                        "1.0.0",
                        "README"))
                .andExpect(status().isNotFound())
                .andDo(_ -> Files.delete(filePath));
    }

    @Test
    void testChangelog() throws Exception {
        var filePath = mockChangelog();
        mockMvc.perform(
                get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "CHANGELOG"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("All notable changes is documented here"))
                .andDo(_ -> Files.delete(filePath));
    }

    @Test
    void testLicense() throws Exception {
        var filePath = mockLicense();
        mockMvc.perform(get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "LICENSE"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("I never broke the Law! I am the law!"))
                .andDo(_ -> Files.delete(filePath));
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
        mockMvc.perform(
                get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "1.0.0", "unknown.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testLatestFile() throws Exception {
        var filePath = mockLatest();
        mockMvc.perform(
                get("/api/{namespace}/{extension}/{version}/file/{fileName}", "foo", "bar", "latest", "DOWNLOAD"))
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult)
                .andExpect(status().isOk())
                .andExpect(content().string("latest download"))
                .andDo(_ -> Files.delete(filePath));
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
    void testInvalidSearch() throws Exception {
        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "foo", "-1", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "foo", "1001", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not exceed 1000"));

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "foo", "1", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
    }

    @Test
    void testSearch() throws Exception {
        var extVersions = mockSearch();
        extVersions.forEach(
                extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true))
                        .thenReturn(extVersion));
        Mockito.when(
                repositories.findLatestVersions(
                        extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
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
        extVersions.forEach(
                extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true))
                        .thenReturn(extVersion));
        Mockito.when(
                repositories.findLatestVersions(
                        extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
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
        extVersions.forEach(
                extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true))
                        .thenReturn(extVersion));
        Mockito.when(
                repositories.findLatestVersions(
                        extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
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
        extVersions.forEach(
                extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true))
                        .thenReturn(extVersion));
        Mockito.when(
                repositories.findLatestVersions(
                        extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
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
        extVersions.forEach(
                extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true))
                        .thenReturn(extVersion));
        Mockito.when(
                repositories.findLatestVersions(
                        extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(
                get("/api/-/search?query={query}&size={size}&offset={offset}", "bar publisher:foo code", "10", "0"))
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
        extVersions.forEach(
                extVersion -> Mockito.when(repositories.findLatestVersion(extVersion.getExtension(), null, false, true))
                        .thenReturn(extVersion));
        Mockito.when(
                repositories.findLatestVersions(
                        extVersions.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(extVersions);

        mockMvc.perform(
                get(
                        "/api/-/search?query={query}&size={size}&offset={offset}",
                        "publisher:bar publisher:foo",
                        "10",
                        "0"))
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
            extension.getVersions().getFirst().setActive(false);
        });
        Mockito.when(
                repositories.findLatestVersions(
                        extVersionsList.stream().map(ExtensionVersion::getExtension).map(Extension::getId).toList()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/-/search?query={query}&size={size}&offset={offset}", "foo", "10", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"offset\":0,\"totalSize\":1,\"extensions\":[]}"));
    }

    @Test
    void testInvalidQuery() throws Exception {
        mockMvc.perform(get("/api/-/query?size={size}&offset={offset}", "-1", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(get("/api/-/query?size={size}&offset={offset}", "1001", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not exceed 1000"));

        mockMvc.perform(get("/api/-/query?size={size}&offset={offset}", "100", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
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
                0);
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
    void testInvalidQueryV2() throws Exception {
        mockMvc.perform(get("/api/v2/-/query?size={size}&offset={offset}", "-1", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(get("/api/v2/-/query?size={size}&offset={offset}", "1001", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not exceed 1000"));

        mockMvc.perform(get("/api/v2/-/query?size={size}&offset={offset}", "100", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
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
        mockMvc.perform(
                get("/api/v2/-/query?extensionId={namespaceName}.{extensionName}", namespaceName, extensionName))
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
                0);
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "true"))
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
                0);
        versions = versions.stream().filter(ev -> ev.getVersion().equals("3.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "false"))
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
                0);
        versions = versions.stream().filter(ev -> ev.getVersion().equals("3.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "links"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("3.0.0");
                    e.setVersionAlias(List.of("latest"));
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setAllVersions(
                            Map.of(
                                    "latest",
                                    "http://localhost/api/foo/bar/latest",
                                    "3.0.0",
                                    "http://localhost/api/foo/bar/3.0.0",
                                    "2.0.0",
                                    "http://localhost/api/foo/bar/2.0.0",
                                    "1.0.0",
                                    "http://localhost/api/foo/bar/1.0.0"));
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
                0);
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "links"))
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
                    e.setAllVersions(
                            Map.of(
                                    "latest",
                                    "http://localhost/api/foo/bar/latest",
                                    "2.0.0",
                                    "http://localhost/api/foo/bar/2.0.0",
                                    "1.0.0",
                                    "http://localhost/api/foo/bar/1.0.0"));
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
                            e.setAllVersions(
                                    Map.of(
                                            "latest",
                                            "http://localhost/api/foo/bar/latest",
                                            "2.0.0",
                                            "http://localhost/api/foo/bar/2.0.0",
                                            "1.0.0",
                                            "http://localhost/api/foo/bar/1.0.0"));
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
                0);
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&targetPlatform={targetPlatform}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "linux-x64",
                        "true"))
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
                0);
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&extensionVersion={extensionVersion}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "2.0.0",
                        "true"))
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
                0);
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&extensionVersion={extensionVersion}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "2.0.0",
                        "false"))
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
                0);
        versions = versions.stream().filter(ev -> ev.getVersion().equals("2.0.0")).collect(Collectors.toList());
        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(versions, Pageable.ofSize(100), versions.size()));

        mockMvc.perform(
                get(
                        "/api/v2/-/query?extensionId={extensionId}&extensionVersion={extensionVersion}&includeAllVersions={includeAllVersions}",
                        "foo.bar",
                        "2.0.0",
                        "links"))
                .andExpect(status().isOk())
                .andExpect(content().json(queryResultJson(e -> {
                    e.setNamespace("foo");
                    e.setName("bar");
                    e.setVersion("2.0.0");
                    e.setVerified(false);
                    e.setTimestamp("2000-01-01T10:00Z");
                    e.setDisplayName("Foo Bar");
                    e.setAllVersions(
                            Map.of(
                                    "latest",
                                    "http://localhost/api/foo/bar/latest",
                                    "3.0.0",
                                    "http://localhost/api/foo/bar/3.0.0",
                                    "2.0.0",
                                    "http://localhost/api/foo/bar/2.0.0",
                                    "1.0.0",
                                    "http://localhost/api/foo/bar/1.0.0"));
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
                0);
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
        mockMvc.perform(
                post("/api/-/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"extensionName\": \"bar\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(
                        header().string(
                                "Location",
                                "http://localhost/api/-/query?extensionName=bar&includeAllVersions=false"));
    }

    @Test
    void testPostQueryNamespace() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(
                post("/api/-/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"namespaceName\": \"foo\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(
                        header().string(
                                "Location",
                                "http://localhost/api/-/query?namespaceName=foo&includeAllVersions=false"));
    }

    @Test
    void testPostQueryExtensionId() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(
                post("/api/-/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"extensionId\": \"foo.bar\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(
                        header().string(
                                "Location",
                                "http://localhost/api/-/query?extensionId=foo.bar&includeAllVersions=false"));
    }

    @Test
    void testPostQueryExtensionUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(
                post("/api/-/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"extensionUuid\": \"5678\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(
                        header().string(
                                "Location",
                                "http://localhost/api/-/query?extensionUuid=5678&includeAllVersions=false"));
    }

    @Test
    void testPostQueryNamespaceUuid() throws Exception {
        mockExtensionVersion();
        mockMvc.perform(
                post("/api/-/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"namespaceUuid\": \"1234\" }"))
                .andExpect(status().isMovedPermanently())
                .andExpect(
                        header().string(
                                "Location",
                                "http://localhost/api/-/query?namespaceUuid=1234&includeAllVersions=false"));
    }

    @Test
    void testCreateNamespace() throws Exception {
        var token = mockAccessToken();
        // Mock findMemberships(user) for similarity check during namespace creation
        Mockito.when(repositories.findMemberships(token.getUser()))
                .thenReturn(Streamable.empty());
        mockMvc.perform(
                post("/api/-/namespace/create?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namespaceJson(n -> {
                            n.setName("foobar");
                        })))
                .andExpect(status().isCreated())
                .andExpect(redirectedUrl("http://localhost/api/foobar"))
                .andExpect(content().json(successJson("Created namespace foobar")));
    }

    @Test
    void testCreateNamespaceNoName() throws Exception {
        mockAccessToken();
        mockMvc.perform(
                post("/api/-/namespace/create?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namespaceJson(n -> {
                        })))
                .andExpect(status().isOk())
                .andExpect(content().json(errorJson("Missing required property 'name'.")));
    }

    @Test
    void testCreateNamespaceInvalidName() throws Exception {
        mockAccessToken();
        mockMvc.perform(
                post("/api/-/namespace/create?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namespaceJson(n -> {
                            n.setName("foo.bar");
                        })))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid namespace name: foo.bar")));
    }

    @Test
    void testCreateNamespaceInactiveToken() throws Exception {
        var token = mockAccessToken();
        token.setActive(false);
        mockMvc.perform(
                post("/api/-/namespace/create?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namespaceJson(n -> {
                            n.setName("foobar");
                        })))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testCreateNamespaceExpiredToken() throws Exception {
        var token = mockAccessToken();
        token.setExpiresTimestamp(TimeUtil.getCurrentUTC().minusDays(1));
        mockMvc.perform(
                post("/api/-/namespace/create?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namespaceJson(n -> {
                            n.setName("foobar");
                        })))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testCreateExistingNamespace() throws Exception {
        mockAccessToken();
        Mockito.when(repositories.findNamespaceName("foobar"))
                .thenReturn("foobar");

        mockMvc.perform(
                post("/api/-/namespace/create?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(namespaceJson(n -> {
                            n.setName("foobar");
                        })))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Namespace already exists: foobar")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "owner", "contributor", "sole-contributor" })
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
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isForbidden());
    }

    @Test
    void testPublishOrphan() throws Exception {
        mockForPublish("orphan");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isForbidden())
                .andExpect(content().json(errorJson("Insufficient access rights for publisher: foo")));
    }

    @Test
    void testPublishRequireLicenseNone() throws Exception {
        var previousRequireLicense = publishingConfig.isRequireLicense();
        try {
            publishingConfig.setRequireLicense(true);
            mockForPublish("contributor");
            var bytes = createExtensionPackage("bar", "1.0.0", null);
            mockMvc.perform(
                    post("/api/-/publish?token={token}", "my_token")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(bytes))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            content().json(errorJson("This extension cannot be accepted because it has no license.")));
        } finally {
            publishingConfig.setRequireLicense(previousRequireLicense);
        }
    }

    @Test
    void testPublishRequireLicenseOk() throws Exception {
        var previousRequireLicense = publishingConfig.isRequireLicense();
        try {
            publishingConfig.setRequireLicense(true);
            mockForPublish("contributor");
            mockActiveVersion();
            var bytes = createExtensionPackage("bar", "1.0.0", "MIT");
            mockMvc.perform(
                    post("/api/-/publish?token={token}", "my_token")
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
            publishingConfig.setRequireLicense(previousRequireLicense);
        }
    }

    @Test
    void testPublishRejectsReadmeCollidingWithBinaryName() throws Exception {
        // Reproduces TOB-OVSX-15: object keys for a version share one flat namespace, so a README
        // declared under the derived name of the .vsix binary would silently overwrite it in storage
        // once uploaded. That name is attacker-controlled (it comes from the VSIX manifest), so
        // publication of a colliding package must be rejected before anything is stored.
        mockForPublish("contributor");
        var bytes = createExtensionPackageWithCollidingReadme("bar", "1.0.0");
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson(
                                        "Extension contains multiple files named 'foo.bar-1.0.0.vsix'"
                                                + " (case-insensitive). Rename the conflicting asset so that it does not"
                                                + " collide with another published file.")));
    }

    @Test
    void testPublishLimitsTags() throws Exception {
        var previousMaxTags = publishingConfig.getMaxTags();
        try {
            publishingConfig.setMaxTags(2);
            mockForPublish("contributor");
            mockActiveVersion();
            var bytes = createExtensionPackage("bar", "1.0.0", null, false, null, List.of("beta", "alpha", "gamma"));
            mockMvc.perform(
                    post("/api/-/publish?token={token}", "my_token")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(bytes))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(extensionJson(e -> {
                        e.setNamespace("foo");
                        e.setName("bar");
                        e.setVersion("1.0.0");
                        e.setDownloadable(true);
                        // The third declared tag is dropped, the kept ones are stored sorted.
                        e.setTags(List.of("alpha", "beta"));
                    })));
        } finally {
            publishingConfig.setMaxTags(previousMaxTags);
        }
    }

    @Test
    void testPublishLimitsInternalTagsSeparately() throws Exception {
        var previousMaxTags = publishingConfig.getMaxTags();
        var previousMaxInternalTags = publishingConfig.getMaxInternalTags();
        try {
            publishingConfig.setMaxTags(1);
            publishingConfig.setMaxInternalTags(2);
            mockForPublish("contributor");
            mockActiveVersion();
            var bytes = createExtensionPackage(
                    "bar",
                    "1.0.0",
                    null,
                    false,
                    null,
                    List.of("__ext_yml", "beta", "__ext_yaml", "alpha", "__web_extension"));
            mockMvc.perform(
                    post("/api/-/publish?token={token}", "my_token")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(bytes))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(extensionJson(e -> {
                        e.setNamespace("foo");
                        e.setName("bar");
                        e.setVersion("1.0.0");
                        e.setDownloadable(true);
                        // The generated tags don't take up a slot of the declared ones, both kinds are
                        // capped on their own.
                        e.setTags(List.of("__ext_yaml", "__ext_yml", "beta"));
                    })));
        } finally {
            publishingConfig.setMaxTags(previousMaxTags);
            publishingConfig.setMaxInternalTags(previousMaxInternalTags);
        }
    }

    @Test
    void testPublishInactiveToken() throws Exception {
        mockForPublish("invalid");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testPublishExpiredToken() throws Exception {
        var token = mockAccessToken();
        token.setExpiresTimestamp(TimeUtil.getCurrentUTC().minusDays(1));
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testPublishUnknownNamespace() throws Exception {
        mockAccessToken();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson(
                                        "Unknown publisher: foo"
                                                + "\nUse the 'create-namespace' command to create a namespace corresponding to your publisher name.")));
    }

    @Test
    void testPublishVerifiedOwner() throws Exception {
        mockForPublish("owner");
        mockActiveVersion();
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
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
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
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
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
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
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
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
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isForbidden())
                .andExpect(content().json(errorJson("Insufficient access rights for publisher: foo")));
    }

    @Test
    void testPublishExistingExtension() throws Exception {
        mockForPublish("existing");
        var bytes = createExtensionPackage("bar", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
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
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(
                        content().json(
                                warningJson(
                                        "A stable release already exists for foo.bar 1.0.0.\n" +
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
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isCreated())
                .andExpect(
                        content().json(
                                warningJson(
                                        "A pre-release already exists for foo.bar 1.5.0.\n" +
                                                "To prevent update conflicts, we recommend that this stable release uses 1.6.0 as its version instead.")));
    }

    @Test
    void testPublishInvalidName() throws Exception {
        mockForPublish("contributor");
        var bytes = createExtensionPackage("b.a.r", "1.0.0", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid extension name: b.a.r")));
    }

    @Test
    void testPublishInvalidVersion() throws Exception {
        mockForPublish("contributor");
        var bytes = createExtensionPackage("bar", "latest", null);
        mockMvc.perform(
                post("/api/-/publish?token={token}", "my_token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("The version string 'latest' is reserved.")));
    }

    @Test
    void testDeleteExtensionInvalidToken() throws Exception {
        var token = mockForDelete(true, true);
        token.setActive(false);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?allVersions=true&token={token}", "foo", "bar", "my_token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid access token.")));
    }

    @Test
    void testDeleteExtensionNotMember() throws Exception {
        mockForDelete(false, false);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?allVersions=true&token={token}", "foo", "bar", "my_token"))
                .andExpect(status().isForbidden())
                .andExpect(content().json(errorJson("Insufficient access rights for namespace: foo")));
    }

    @Test
    void testDeleteExtensionUnknownNamespace() throws Exception {
        mockForDelete(true, true);
        mockMvc.perform(
                post(
                        "/api/{namespace}/{extension}/delete?allVersions=true&token={token}",
                        "unknown",
                        "bar",
                        "my_token"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: unknown.bar")));
    }

    @Test
    void testDeleteExtensionAllVersions() throws Exception {
        // allVersions deletes the extension as a whole.
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?allVersions=true&token={token}", "foo", "bar", "my_token"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foo.bar 1.0.0\nDeleted foo.bar 2.0.0")));
    }

    @Test
    void testDeleteExtensionWithoutVersions() throws Exception {
        // Neither versions nor allVersions: deleting nothing is more likely a mistake than an intention.
        mockForDelete(true, true);
        mockMvc.perform(post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson(
                                        "No versions specified. Provide the versions to delete or set 'allVersions'.")));
    }

    @Test
    void testDeleteExtensionVersionsAndAllVersions() throws Exception {
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?allVersions=true&token={token}", "foo", "bar", "my_token")
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson("Specify either the versions to delete or 'allVersions', but not both.")));
    }

    @Test
    void testDeleteExtensionMissingVersion() throws Exception {
        // A target platform version entry without a version can't be resolved to anything: reject it
        // explicitly with a 400 instead of letting it fall through to a confusing 404.
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token")
                        .content("[{\"targetPlatform\":\"universal\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson("Missing 'version' for a requested target platform version to delete.")));
    }

    @Test
    void testDeleteExtensionEmptyBodyIsNoOp() throws Exception {
        // An explicit empty list names no version, so nothing is deleted.
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token")
                        .content("[]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Nothing was deleted, so the result reports neither a success nor an error.
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void testDeleteExtensionVersion() throws Exception {
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token")
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foo.bar 1.0.0")));
    }

    @Test
    void testDeleteExtensionVersionWithoutTargetPlatform() throws Exception {
        // Without target platform, all target platforms of the given version are deleted.
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token")
                        .content("[{\"version\":\"2.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foo.bar 2.0.0")));
    }

    @Test
    void testDeleteExtensionUnknownVersion() throws Exception {
        mockForDelete(true, true);
        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token")
                        .content("[{\"version\":\"3.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar 3.0.0")));
    }

    @Test
    void testDeleteExtensionAsMember() throws Exception {
        // A member who is not the namespace owner only deletes the versions they published themselves.
        var token = mockForDelete(false, true);
        var extension = repositories.findExtension("bar", repositories.findNamespace("foo"));
        var published = repositories.findTargetPlatformsGroupedByVersion(extension).stream()
                .filter(version -> version.version().equals("1.0.0"))
                .toList();
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension, token.getUser()))
                .thenReturn(published);

        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?allVersions=true&token={token}", "foo", "bar", "my_token"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foo.bar 1.0.0")));
    }

    @Test
    void testDeleteExtensionVersionNotPublishedByMember() throws Exception {
        // A member who is not the namespace owner may not delete a version published by someone else:
        // the version lookup is scoped to the caller, so it is not found.
        var token = mockForDelete(false, true);
        Mockito.when(
                repositories.findVersionPublishedByUser(
                        token.getUser(),
                        "1.0.0",
                        TargetPlatform.NAME_UNIVERSAL,
                        "bar",
                        "foo"))
                .thenReturn(null);

        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?token={token}", "foo", "bar", "my_token")
                        .content("[{\"targetPlatform\":\"universal\",\"version\":\"1.0.0\"}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Extension not found: foo.bar 1.0.0")));
    }

    @Test
    void testDeleteDependingExtension() throws Exception {
        mockForDelete(true, true);
        var extension = repositories.findExtension("bar", repositories.findNamespace("foo"));
        var dependant = new Extension();
        dependant.setName("dependant");
        dependant.setNamespace(extension.getNamespace());
        var dependantVersion = new ExtensionVersion();
        dependantVersion.setExtension(dependant);
        dependantVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        dependantVersion.setVersion("1.0.0");
        Mockito.when(repositories.findDependenciesReference(extension))
                .thenReturn(Streamable.of(dependantVersion));

        mockMvc.perform(
                post("/api/{namespace}/{extension}/delete?allVersions=true&token={token}", "foo", "bar", "my_token"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json(
                                errorJson(
                                        "The following extensions have a dependency on foo.bar: foo.dependant-1.0.0")));
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

        mockMvc.perform(
                post("/api/{namespace}/{extension}/review", "foo", "bar")
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
        mockMvc.perform(
                post("/api/{namespace}/{extension}/review", "foo", "bar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(r -> {
                            r.setRating(3);
                        })).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPostReviewInvalidRating() throws Exception {
        mockMvc.perform(
                post("/api/{namespace}/{extension}/review", "foo", "bar")
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
        mockMvc.perform(
                post("/api/{namespace}/{extension}/review", "foo", "bar")
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

        mockMvc.perform(
                post("/api/{namespace}/{extension}/review", "foo", "bar")
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

        mockMvc.perform(
                post("/api/{namespace}/{extension}/review/delete", "foo", "bar")
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
        mockMvc.perform(
                post("/api/{namespace}/{extension}/review/delete", "foo", "bar")
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

        mockMvc.perform(
                post("/api/{namespace}/{extension}/review/delete", "foo", "bar")
                        .with(user("test_user"))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("You have not submitted any review yet.")));
    }

    @Test
    void testGetChanges() throws Exception {
        var published = mockChangeEntry("1.0.0", ChangeEntryJson.STATE_ACTIVE, "2000-01-01T10:00Z");
        var removed = mockChangeEntry("0.9.0", ChangeEntryJson.STATE_REMOVED, "2000-02-01T10:00Z");
        Mockito.when(repositories.findChanges(Mockito.isNull(), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(changesPage(List.of(published, removed), false));

        mockMvc.perform(get("/api/-/version-changes"))
                .andExpect(status().isOk())
                .andExpect(content().json(changesJson(c -> c.setChanges(List.of(published, removed)))));
    }

    @Test
    void testGetChangesReportsEveryTransitionOfAVersion() throws Exception {
        // A version that is published, has its publisher's contributions revoked and is then reinstated
        // appears once per transition, in the order they happened, rather than as a single entry that
        // moves around. A consumer polling in between therefore never misses that it was withdrawn.
        var published = mockChangeEntry("1.0.0", ChangeEntryJson.STATE_ACTIVE, "2000-01-01T10:00Z");
        var deactivated = mockChangeEntry("1.0.0", ChangeEntryJson.STATE_INACTIVE, "2000-03-01T10:00Z");
        var reactivated = mockChangeEntry("1.0.0", ChangeEntryJson.STATE_ACTIVE, "2000-04-01T10:00Z");
        Mockito.when(repositories.findChanges(Mockito.isNull(), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(changesPage(List.of(published, deactivated, reactivated), false));

        mockMvc.perform(get("/api/-/version-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.changes[1].state").value("INACTIVE"))
                .andExpect(jsonPath("$.changes[2].state").value("ACTIVE"))
                // each entry is reported at the instant of its own transition, while they all keep
                // naming the version's publication timestamp
                .andExpect(jsonPath("$.changes[1].lastUpdated").value("2000-03-01T10:00Z"))
                .andExpect(jsonPath("$.changes[2].lastUpdated").value("2000-04-01T10:00Z"))
                .andExpect(jsonPath("$.changes[1].timestamp").value("2000-01-01T10:00Z"));
    }

    @Test
    void testGetChangesAddsTheVersionUrl() throws Exception {
        var entry = mockChangeEntry("1.0.0", ChangeEntryJson.STATE_ACTIVE, "2000-01-01T10:00Z");
        Mockito.when(repositories.findChanges(Mockito.isNull(), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(changesPage(List.of(entry), false));

        mockMvc.perform(get("/api/-/version-changes"))
                .andExpect(status().isOk())
                // the entry names one target platform, so the URL addresses exactly that version
                .andExpect(jsonPath("$.changes[0].url").value("http://localhost/api/foo/bar/universal/1.0.0"));
    }

    @Test
    void testGetChangesEmpty() throws Exception {
        Mockito.when(repositories.findChanges(Mockito.isNull(), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(new ChangesPage(Collections.emptyList(), null, false));

        mockMvc.perform(get("/api/-/version-changes"))
                .andExpect(status().isOk())
                .andExpect(content().json(changesJson(c -> c.setChanges(Collections.emptyList()))))
                // Nothing to continue from: no entry was returned and none was asked after, so the
                // consumer simply repeats this request rather than being handed a position.
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void testGetChangesReportsWhereToContinue() throws Exception {
        var entry = mockChangeEntry("1.0.0", ChangeEntryJson.STATE_ACTIVE, "2000-01-01T10:00Z");
        var cursor = new ChangesCursor(LocalDateTime.parse("2000-01-01T10:00"), 42L);
        Mockito.when(repositories.findChanges(Mockito.isNull(), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(new ChangesPage(List.of(entry), cursor, true));

        mockMvc.perform(get("/api/-/version-changes"))
                .andExpect(status().isOk())
                // The consumer stores this and passes it back as 'after'; 'hasMore' tells it to ask again
                // straight away instead of waiting for its next poll.
                .andExpect(jsonPath("$.nextCursor").value(cursor.encode()))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void testGetChangesContinuesAfterACursor() throws Exception {
        var cursor = new ChangesCursor(LocalDateTime.parse("2026-01-14T09:30:11"), 1234L);
        Mockito.when(repositories.findChanges(Mockito.isNull(), lagCutoff(), Mockito.eq(cursor), Mockito.eq(100)))
                .thenReturn(new ChangesPage(Collections.emptyList(), cursor, false));

        mockMvc.perform(get("/api/-/version-changes?after={after}", cursor.encode()))
                .andExpect(status().isOk())
                // an idle registry hands the position straight back, so the loop keeps a usable cursor
                .andExpect(jsonPath("$.nextCursor").value(cursor.encode()));

        // The instant alone would not say which of the transitions sharing it have been processed, so the
        // entry id has to survive the round trip through the parameter.
        Mockito.verify(repositories)
                .findChanges(Mockito.isNull(), lagCutoff(), Mockito.eq(cursor), Mockito.eq(100));
    }

    @Test
    void testGetChangesHoldsBackTheMostRecentTransitions() throws Exception {
        // A transition is stamped with the instant it happened before the transaction recording it
        // commits, so an entry can turn up after a consumer has read past the position it occupies. The
        // feed therefore stops short of the present, which is what a request reaching it is clamped to.
        Mockito.when(repositories.findChanges(Mockito.isNull(), Mockito.any(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(new ChangesPage(Collections.emptyList(), null, false));

        mockMvc.perform(get("/api/-/version-changes")).andExpect(status().isOk());

        var until = ArgumentCaptor.forClass(LocalDateTime.class);
        Mockito.verify(repositories)
                .findChanges(Mockito.isNull(), until.capture(), Mockito.isNull(), Mockito.eq(100));

        var now = TimeUtil.getCurrentUTC();
        assertThat(until.getValue())
                .isBefore(now.minus(CHANGES_FEED_LAG).plusSeconds(1))
                .isAfter(now.minus(CHANGES_FEED_LAG).minusMinutes(1));
    }

    @Test
    void testGetChangesDoesNotHoldBackAHistoricalWindow() throws Exception {
        // Those entries have long been committed, so the bound the caller asked for is the restrictive
        // one and reaches the repository unchanged.
        var since = LocalDateTime.parse("2020-01-01T00:00");
        var until = LocalDateTime.parse("2020-02-01T00:00");
        Mockito.when(repositories.findChanges(since, until, null, 100))
                .thenReturn(new ChangesPage(Collections.emptyList(), null, false));

        mockMvc.perform(
                get(
                        "/api/-/version-changes?since={since}&until={until}",
                        "2020-01-01T00:00:00Z",
                        "2020-02-01T00:00:00Z"))
                .andExpect(status().isOk());

        Mockito.verify(repositories).findChanges(since, until, null, 100);
    }

    @Test
    void testGetChangesWithinATimeWindow() throws Exception {
        var since = LocalDateTime.parse("2026-01-01T00:00");
        var until = LocalDateTime.parse("2026-02-01T00:00");
        Mockito.when(repositories.findChanges(since, until, null, 50))
                .thenReturn(new ChangesPage(Collections.emptyList(), null, false));

        mockMvc.perform(
                get(
                        "/api/-/version-changes?since={since}&until={until}&size={size}",
                        "2026-01-01T00:00:00Z",
                        "2026-02-01T00:00:00Z",
                        "50"))
                .andExpect(status().isOk())
                .andExpect(content().json(changesJson(c -> c.setChanges(Collections.emptyList()))));
    }

    @Test
    void testGetChangesCatchesUpToAFixedEnd() throws Exception {
        // 'until' bounds where a catch-up stops, which is the one range parameter that makes sense
        // together with a position to continue from.
        var cursor = new ChangesCursor(LocalDateTime.parse("2026-01-14T09:30:11"), 1234L);
        var until = LocalDateTime.parse("2026-02-01T00:00");
        Mockito.when(repositories.findChanges(null, until, cursor, 100))
                .thenReturn(new ChangesPage(Collections.emptyList(), cursor, false));

        mockMvc.perform(
                get(
                        "/api/-/version-changes?after={after}&until={until}",
                        cursor.encode(),
                        "2026-02-01T00:00:00Z"))
                .andExpect(status().isOk());

        Mockito.verify(repositories).findChanges(null, until, cursor, 100);
    }

    @Test
    void testGetChangesConvertsATimestampWithAnOffsetToUTC() throws Exception {
        var since = LocalDateTime.parse("2026-01-01T10:00");
        Mockito.when(repositories.findChanges(Mockito.eq(since), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(new ChangesPage(Collections.emptyList(), null, false));

        mockMvc.perform(get("/api/-/version-changes?since={since}", "2026-01-01T12:00:00+02:00"))
                .andExpect(status().isOk());

        Mockito.verify(repositories)
                .findChanges(Mockito.eq(since), lagCutoff(), Mockito.isNull(), Mockito.eq(100));
    }

    @Test
    void testGetChangesAcceptsATimestampWithoutAZone() throws Exception {
        var since = LocalDateTime.parse("2026-01-01T10:00");
        Mockito.when(repositories.findChanges(Mockito.eq(since), lagCutoff(), Mockito.isNull(), Mockito.eq(100)))
                .thenReturn(new ChangesPage(Collections.emptyList(), null, false));

        mockMvc.perform(get("/api/-/version-changes?since={since}", "2026-01-01T10:00:00"))
                .andExpect(status().isOk());

        Mockito.verify(repositories)
                .findChanges(Mockito.eq(since), lagCutoff(), Mockito.isNull(), Mockito.eq(100));
    }

    @Test
    void testInvalidGetChanges() throws Exception {
        mockMvc.perform(get("/api/-/version-changes?since={since}", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid 'since' parameter: yesterday")));

        mockMvc.perform(get("/api/-/version-changes?until={until}", "2026-13-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid 'until' parameter: 2026-13-01T00:00:00Z")));

        mockMvc.perform(get("/api/-/version-changes?size={size}", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must be positive"));

        mockMvc.perform(get("/api/-/version-changes?size={size}", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not exceed 1000"));

        // A cursor a consumer made up or truncated is rejected rather than resuming from somewhere else
        // in the feed, which would silently skip entries.
        mockMvc.perform(get("/api/-/version-changes?after={after}", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Invalid 'after' parameter: not-a-cursor")));

        // The two disagree about where the response starts, so neither is allowed to silently win.
        mockMvc.perform(
                get(
                        "/api/-/version-changes?after={after}&since={since}",
                        new ChangesCursor(LocalDateTime.parse("2026-01-01T10:00"), 1L).encode(),
                        "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("The 'after' and 'since' parameters cannot be combined")));

        Mockito.verify(repositories, never())
                .findChanges(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt());
    }

    @Test
    void testInvalidGetVersions() throws Exception {
        mockMvc.perform(
                get("/api/{namespace}/{extension}/versions?size={size}&offset={offset}", "foo", "bar", "-1", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(
                get("/api/{namespace}/{extension}/versions?size={size}&offset={offset}", "foo", "bar", "101", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not exceed 100"));

        mockMvc.perform(
                get("/api/{namespace}/{extension}/versions?size={size}&offset={offset}", "foo", "bar", "100", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
    }

    @Test
    void testInvalidGetVersionReferences() throws Exception {
        mockMvc.perform(
                get(
                        "/api/{namespace}/{extension}/version-references?size={size}&offset={offset}",
                        "foo",
                        "bar",
                        "-1",
                        "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not be negative"));

        mockMvc.perform(
                get(
                        "/api/{namespace}/{extension}/version-references?size={size}&offset={offset}",
                        "foo",
                        "bar",
                        "101",
                        "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size: parameter must not exceed 100"));

        mockMvc.perform(
                get(
                        "/api/{namespace}/{extension}/version-references?size={size}&offset={offset}",
                        "foo",
                        "bar",
                        "100",
                        "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offset: parameter must not be negative"));
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

    private String namespaceJson(Consumer<NamespaceJson> content) throws JacksonException {
        var json = new NamespaceJson();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
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
                0);

        Mockito.when(repositories.findActiveVersions(query))
                .thenReturn(new PageImpl<>(Collections.emptyList(), Pageable.ofSize(query.size()), 0));
    }

    private List<ExtensionVersion> mockExtensionVersionVersionsTargetPlatforms() {
        var values = List.of(
                "1.0.0@darwin-x64",
                "2.0.0@darwin-x64",
                "1.0.0@linux-x64",
                "2.0.0@linux-x64");

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

    private List<ExtensionVersion> mockExtensionVersions(
            String targetPlatform,
            List<String> values,
            BiFunction<ExtensionVersion, String, ExtensionVersion> setter
    ) {
        var namespace = new Namespace();
        namespace.setId(1L);
        namespace.setPublicId("1234");
        namespace.setName("foo");

        var extension = new Extension();
        extension.setId(2L);
        extension.setName("bar");
        extension.setNamespace(namespace);

        var versions = new ArrayList<ExtensionVersion>();
        for (var i = 0; i < values.size(); i++) {
            var extVersion = new ExtensionVersion();
            extVersion.setId(3 + i);
            extVersion = setter.apply(extVersion, values.get(i));
            extVersion.setExtension(extension);
            versions.add(extVersion);
        }

        Mockito
                .when(
                        repositories
                                .findActiveExtensionVersions(eq(Set.of(extension.getId())), isNull(), anyInt()))
                .thenReturn(versions);
        Mockito.when(repositories.findLatestVersionsIsPreview(Set.of(extension.getId())))
                .thenReturn(Map.of(extension.getId(), versions.getFirst().isPreview()));
        Mockito.when(repositories.findActiveVersionStringsSorted(Set.of(extension.getId()), null))
                .thenReturn(
                        versions.stream().collect(
                                Collectors.groupingBy(
                                        ev -> ev.getExtension().getId(),
                                        Collectors.mapping(ExtensionVersion::getVersion, Collectors.toList()))));
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

        Mockito
                .when(
                        repositories
                                .findActiveExtensionVersions(eq(Set.of(extension.getId())), isNull(), anyInt()))
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
        Mockito.when(repositories.isVerified(namespace))
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
        download.setSize(DOWNLOAD_SIZE);
        var signature = new FileResource();
        if (withSignature) {
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
            if (types.contains(DOWNLOAD) && download.getExtension().equals(extensionVersion)) {
                files.add(download);
            }
            if (withSignature && types.contains(DOWNLOAD_SIG) && signature.getExtension().equals(extensionVersion)) {
                files.add(signature);
            }

            return files;
        });

        return extVersion;
    }

    private String extensionJson(Consumer<ExtensionJson> content) throws JacksonException {
        var json = new ExtensionJson();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    @SafeVarargs
    private String queryResultJson(Consumer<ExtensionJson>... contents) throws JacksonException {
        var extensionJsons = new ArrayList<String>();
        for (var content : contents) {
            extensionJsons.add(extensionJson(content));
        }

        return "{\"extensions\":[" + String.join(",", extensionJsons) + "]}";
    }

    private Path mockReadme() throws IOException {
        return mockReadme(TargetPlatform.NAME_UNIVERSAL);
    }

    private Path mockReadme(String targetPlatform) throws IOException {
        return mockReadme(targetPlatform, "README", "Please read me");
    }

    private Path mockReadme(String targetPlatform, String fileName, String content) throws IOException {
        var extVersion = mockExtension(targetPlatform);
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName(fileName);
        resource.setType(FileResource.README);
        resource.setStorageType(STORAGE_LOCAL);
        Mockito.when(entityManager.find(FileResource.class, resource.getId())).thenReturn(resource);
        Mockito.when(repositories.findFileByType("foo", "bar", targetPlatform, "1.0.0", README)).thenReturn(resource);
        // Filenames that aren't well-known type aliases are looked up by name.
        Mockito.when(repositories.findFileByName("foo", "bar", targetPlatform, "1.0.0", fileName)).thenReturn(resource);

        var segments = new String[] { "foo", "bar" };
        if (!targetPlatform.equals(TargetPlatform.NAME_UNIVERSAL)) {
            segments = ArrayUtils.add(segments, targetPlatform);
        }

        segments = ArrayUtils.add(segments, "1.0.0");
        segments = ArrayUtils.add(segments, fileName);
        var path = Path.of("/tmp", segments);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
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

    private String reviewsJson(Consumer<ReviewListJson> content) throws JacksonException {
        var json = new ReviewListJson();
        json.setReviews(new ArrayList<>());
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private ChangeEntryJson mockChangeEntry(String version, String state, String lastUpdated) {
        var entry = new ChangeEntryJson();
        entry.setNamespace("foo");
        entry.setName("bar");
        entry.setVersion(version);
        entry.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        entry.setState(state);
        entry.setTimestamp("2000-01-01T10:00Z");
        entry.setLastUpdated(lastUpdated);
        return entry;
    }

    private String changesJson(Consumer<ChangesResultJson> content) throws JacksonException {
        var json = new ChangesResultJson();
        json.setChanges(new ArrayList<>());
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    /**
     * A non-empty page of the changes feed. Carries a cursor because the repository always reports one for
     * a page that returned entries, but its value does not matter to the tests using this.
     */
    private ChangesPage changesPage(List<ChangeEntryJson> changes, boolean hasMore) {
        return new ChangesPage(changes, new ChangesCursor(LocalDateTime.parse("2000-01-01T10:00"), 1L), hasMore);
    }

    /**
     * Matches the upper bound the endpoint clamps a request to when it reaches the present: the feed holds
     * back the most recent transitions, so that one still being committed cannot be passed over. The exact
     * instant moves with the clock, so the tests that are not about the lag itself match it loosely --
     * {@link #testGetChangesHoldsBackTheMostRecentTransitions()} is the one that pins it down.
     */
    private LocalDateTime lagCutoff() {
        var expected = TimeUtil.getCurrentUTC().minus(CHANGES_FEED_LAG);
        return Mockito.argThat(
                (LocalDateTime until) -> until != null
                        && until.isAfter(expected.minusMinutes(1))
                        && until.isBefore(expected.plusMinutes(1)));
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

        var publisherSearchOptions = new ISearchService.Options(
                "",
                null,
                null,
                10,
                0,
                "desc",
                SortBy.RELEVANCE,
                false,
                null,
                "foo");
        Mockito.when(search.search(publisherSearchOptions))
                .thenReturn(searchResult);

        var publisherWithQuerySearchOptions = new ISearchService.Options(
                "bar",
                null,
                null,
                10,
                0,
                "desc",
                SortBy.RELEVANCE,
                false,
                null,
                "foo");
        Mockito.when(search.search(publisherWithQuerySearchOptions))
                .thenReturn(searchResult);

        var publisherWithMoreQuerySearchOptions = new ISearchService.Options(
                "bar code",
                null,
                null,
                10,
                0,
                "desc",
                SortBy.RELEVANCE,
                false,
                null,
                "foo");
        Mockito.when(search.search(publisherWithMoreQuerySearchOptions))
                .thenReturn(searchResult);

        return List.of(extVersion);
    }

    private String searchJson(Consumer<SearchResultJson> content) throws JacksonException {
        var json = new SearchResultJson();
        json.setExtensions(new ArrayList<>());
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private PersonalAccessToken mockAccessToken() {
        var userData = new UserData();
        userData.setLoginName("test_user");
        var token = new PersonalAccessToken();
        token.setUser(userData);
        token.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token.setValue("my_token");
        token.setActive(true);
        token.setType(PersonalAccessTokenType.LLT);
        Mockito.when(repositories.findPersonalAccessToken("my_token"))
                .thenReturn(token);
        return token;
    }

    /**
     * Mocks the extension {@code foo.bar} with the universal versions 1.0.0 and 2.0.0, all of them
     * published by the user of the access token {@code my_token}.
     */
    private PersonalAccessToken mockForDelete(boolean isOwner, boolean isMember) {
        var token = mockAccessToken();
        var user = token.getUser();
        var namespace = new Namespace();
        namespace.setName("foo");
        Mockito.when(repositories.findNamespace("foo")).thenReturn(namespace);
        Mockito.when(repositories.isNamespaceOwner(user, namespace)).thenReturn(isOwner);
        Mockito.when(repositories.hasMembership(user, namespace)).thenReturn(isMember);

        var extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("bar");
        extension.setActive(true);
        Mockito.when(repositories.findExtension("bar", namespace)).thenReturn(extension);
        Mockito.when(repositories.findExtensionForUpdateNoWait("bar", "foo")).thenReturn(extension);

        var versionNames = List.of("1.0.0", "2.0.0");
        var versions = new ArrayList<ExtensionVersion>(versionNames.size());
        for (var versionName : versionNames) {
            var extVersion = new ExtensionVersion();
            extVersion.setExtension(extension);
            extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            extVersion.setVersion(versionName);
            extVersion.setActive(true);
            versions.add(extVersion);

            Mockito.when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());
            Mockito.when(repositories.findVersion(versionName, TargetPlatform.NAME_UNIVERSAL, "bar", "foo"))
                    .thenReturn(extVersion);
            Mockito.when(
                    repositories.findVersionPublishedByUser(
                            user,
                            versionName,
                            TargetPlatform.NAME_UNIVERSAL,
                            "bar",
                            "foo"))
                    .thenReturn(extVersion);
        }
        extension.getVersions().addAll(versions);

        var targetPlatforms = List.of(new TargetPlatformActiveJson(TargetPlatform.NAME_UNIVERSAL, true, false));
        var groupedVersions = versions.stream()
                .map(extVersion -> new VersionTargetPlatformsJson(extVersion.getVersion(), targetPlatforms))
                .toList();
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension)).thenReturn(groupedVersions);
        Mockito.when(repositories.findTargetPlatformsGroupedByVersion(extension, user)).thenReturn(groupedVersions);
        Mockito.when(repositories.isDeleteAllActiveVersions(eq("foo"), eq("bar"), any(TargetPlatformVersion[].class)))
                .then(
                        (Answer<Boolean>) invocation -> ((TargetPlatformVersion[]) invocation
                                .getRawArguments()[2]).length == versions.size());
        Mockito.when(repositories.findDependenciesReference(extension)).thenReturn(Streamable.empty());
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
            Mockito.when(repositories.findExtensionForUpdate("bar", "foo"))
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
            Mockito.when(repositories.isVerified(namespace))
                    .thenReturn(true);
            Mockito.when(repositories.canPublishInNamespace(token.getUser(), namespace))
                    .thenReturn(true);
            Mockito.when(repositories.isVerifiedPublisher(any(ExtensionVersion.class)))
                    .thenReturn(true);
            // Mock findMemberships(user) for similarity check
            Mockito.when(repositories.findMemberships(token.getUser()))
                    .thenReturn(Streamable.of(ownerMem));
        } else if (mode.equals("contributor") || mode.equals("sole-contributor") || mode.equals("existing")) {
            Mockito.when(repositories.canPublishInNamespace(token.getUser(), namespace))
                    .thenReturn(true);
            Mockito.when(repositories.isVerifiedPublisher(any(ExtensionVersion.class)))
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
                Mockito.when(repositories.isVerifiedPublisher(any(ExtensionVersion.class)))
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
                Mockito.when(repositories.isVerifiedPublisher(any(ExtensionVersion.class)))
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
            Mockito.when(repositories.isVerified(namespace))
                    .thenReturn(true);
            if (mode.equals("privileged")) {
                token.getUser().setRole(UserData.Role.PRIVILEGED);
                // A privileged user bypasses per-namespace verification (RepositoryService.isVerifiedPublisher),
                // regardless of namespace membership.
                Mockito.when(repositories.isVerifiedPublisher(any(ExtensionVersion.class)))
                        .thenReturn(true);
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
            Mockito.when(repositories.isVerified(namespace))
                    .thenReturn(false);
            // Mock findMemberships(user) for similarity check - default to empty
            Mockito.when(repositories.findMemberships(token.getUser()))
                    .thenReturn(Streamable.empty());
        }

        Mockito.when(entityManager.merge(any(Extension.class)))
                .then((Answer<Extension>) invocation -> invocation.getArgument(0, Extension.class));
    }

    private String reviewJson(Consumer<ReviewJson> content) throws JacksonException {
        var json = new ReviewJson();
        content.accept(json);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private UserData mockUserData() {
        var userData = new UserData();
        userData.setLoginName("test_user");
        userData.setFullName("Test User");
        userData.setProviderUrl("http://example.com/test");
        Mockito.doReturn(userData).when(users).findLoggedInUser();
        return userData;
    }

    private String successJson(String message) throws JacksonException {
        var json = ResultJson.success(message);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private String errorJson(String message) throws JacksonException {
        var json = ResultJson.error(message);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private String warningJson(String message) throws JacksonException {
        var json = ResultJson.warning(message);
        return JsonMapper.shared().writeValueAsString(json);
    }

    private byte[] createExtensionPackage(String name, String version, String license) throws IOException {
        return createExtensionPackage(name, version, license, false, null);
    }

    private byte[] createExtensionPackage(
            String name,
            String version,
            String license,
            boolean preRelease,
            String targetPlatform
    ) throws IOException {
        return createExtensionPackage(name, version, license, preRelease, targetPlatform, List.of());
    }

    private byte[] createExtensionPackage(
            String name,
            String version,
            String license,
            boolean preRelease,
            String targetPlatform,
            List<String> tags
    ) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var archive = new ZipOutputStream(bytes);
        archive.putNextEntry(new ZipEntry("extension.vsixmanifest"));
        var vsixmanifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<PackageManifest Version=\"2.0.0\" xmlns=\"http://schemas.microsoft.com/developer/vsx-schema/2011\" xmlns:d=\"http://schemas.microsoft.com/developer/vsx-schema-design/2011\">"
                +
                "<Metadata>" +
                "<Identity Language=\"en-US\" Id=\"" + name + "\" Version=\"" + version + "\" Publisher=\"foo\" "
                + (targetPlatform != null ? "TargetPlatform=\"" + targetPlatform + "\"" : "") + " />" +
                "<DisplayName>foo</DisplayName>" +
                "<Description xml:space=\"preserve\"></Description>" +
                "<Tags>" + String.join(",", tags) + "</Tags>" +
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
                "<Asset Type=\"Microsoft.VisualStudio.Code.Manifest\" Path=\"extension/package.json\" Addressable=\"true\" />"
                +
                "</Assets>" +
                "</PackageManifest>";
        archive.write(vsixmanifest.getBytes());
        archive.closeEntry();
        archive.putNextEntry(new ZipEntry("extension/package.json"));
        var packageJson = "{" +
                "\"publisher\": \"foo\"," +
                "\"name\": \"" + name + "\"," +
                "\"version\": \"" + version + "\"," +
                "\"displayName\": \"foo\"" +
                (license == null ? "" : ",\"license\": \"" + license + "\"") +
                "}";
        archive.write(packageJson.getBytes());
        archive.closeEntry();
        archive.finish();
        return bytes.toByteArray();
    }

    /**
     * Builds a package whose README asset resolves to the derived name of the .vsix binary
     * ("foo.&lt;name&gt;-&lt;version&gt;.vsix"), reproducing TOB-OVSX-15.
     */
    private byte[] createExtensionPackageWithCollidingReadme(String name, String version) throws IOException {
        var maliciousPath = "extension/"
                + NamingUtil.toFileFormat("foo", name, TargetPlatform.NAME_UNIVERSAL, version, ".vsix");
        var bytes = new ByteArrayOutputStream();
        var archive = new ZipOutputStream(bytes);
        archive.putNextEntry(new ZipEntry("extension.vsixmanifest"));
        var vsixmanifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<PackageManifest Version=\"2.0.0\" xmlns=\"http://schemas.microsoft.com/developer/vsx-schema/2011\" xmlns:d=\"http://schemas.microsoft.com/developer/vsx-schema-design/2011\">"
                +
                "<Metadata>" +
                "<Identity Language=\"en-US\" Id=\"" + name + "\" Version=\"" + version + "\" Publisher=\"foo\" />" +
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
                "</Properties>" +
                "</Metadata>" +
                "<Installation>" +
                "<InstallationTarget Id=\"Microsoft.VisualStudio.Code\"/>" +
                "</Installation>" +
                "<Dependencies/>" +
                "<Assets>" +
                "<Asset Type=\"Microsoft.VisualStudio.Code.Manifest\" Path=\"extension/package.json\" Addressable=\"true\" />"
                +
                "<Asset Type=\"Microsoft.VisualStudio.Services.Content.Details\" Path=\"" + maliciousPath
                + "\" Addressable=\"true\" />" +
                "</Assets>" +
                "</PackageManifest>";
        archive.write(vsixmanifest.getBytes());
        archive.closeEntry();
        archive.putNextEntry(new ZipEntry("extension/package.json"));
        var packageJson = "{" +
                "\"publisher\": \"foo\"," +
                "\"name\": \"" + name + "\"," +
                "\"version\": \"" + version + "\"," +
                "\"displayName\": \"foo\"" +
                "}";
        archive.write(packageJson.getBytes());
        archive.closeEntry();
        archive.putNextEntry(new ZipEntry(maliciousPath));
        archive.write("DATA FROM THE ARCHIVE".getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
        archive.finish();
        return bytes.toByteArray();
    }

    @TestConfiguration
    @Import({ SecurityConfig.class, MockMvcAsyncConfig.class })
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
            return new UserService(
                    entityManager,
                    repositories,
                    storageUtil,
                    cache,
                    validator,
                    clientRegistrationRepository,
                    attributesConfig);
        }

        @Bean
        UUIDService uuidService() {
            return new UUIDService();
        }

        @Bean
        AccessTokenConfig tokenConfig() {
            return new AccessTokenConfig();
        }

        @Bean
        AccessTokenService tokenService(
                AccessTokenConfig config,
                EntityManager entityManager,
                RepositoryService repositories,
                MailService mailService,
                DSLContext dsl
        ) {
            return new AccessTokenService(config, entityManager, repositories, mailService, dsl);
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
                SimilarityCheckService similarityCheckService,
                PublishingConfig publishingConfig,
                TrustedPublishingConfig trustedPublishingConfig
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
                    similarityCheckService,
                    publishingConfig,
                    trustedPublishingConfig,
                    CHANGES_FEED_LAG);
        }

        @Bean
        PublishingConfig publishingConfig() {
            return new PublishingConfig();
        }

        @Bean
        TrustedPublishingConfig trustedPublishingConfig() {
            return new TrustedPublishingConfig();
        }

        @Bean
        PublishExtensionVersionHandler publishExtensionVersionHandler(
                PublishingConfig publishingConfig,
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
                    publishingConfig,
                    service,
                    integrityService,
                    entityManager,
                    repositories,
                    scheduler,
                    users,
                    validator,
                    extensionControl,
                    extensionScanService);
        }

        @Bean
        ExtensionService extensionService(
                PublishingConfig publishingConfig,
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
                    publishingConfig,
                    entityManager,
                    repositories,
                    search,
                    cache,
                    logs,
                    publishHandler,
                    scheduler,
                    extensionScanService,
                    scanPersistenceService);
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
                    cdnServiceConfig);
        }

        @Bean
        LocalStorageService localStorageService() {
            return new LocalStorageService();
        }

        @Bean
        ExtensionJsonCacheKeyGenerator extensionJsonCacheKeyGenerator() {
            return new ExtensionJsonCacheKeyGenerator();
        }

        @Bean
        VersionService versionService(RepositoryService repositoryService) {
            return new VersionService(repositoryService);
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
