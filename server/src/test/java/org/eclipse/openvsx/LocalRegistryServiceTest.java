/******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.accesstoken.AccessTokenAction;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.search.SimilarityCheckService;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.VersionService;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;
import org.eclipse.openvsx.web.WebUiProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalRegistryServiceTest {

    @Mock
    EntityManager entityManager;

    @Mock
    RepositoryService repositories;

    @Mock
    ExtensionService extensions;

    @Mock
    VersionService versions;

    @Mock
    UserService users;

    @Mock
    AccessTokenService tokens;

    @Mock
    SearchUtilService searchUtilService;

    @Mock
    ExtensionValidator validator;

    @Mock
    StorageUtilService storageUtilService;

    @Mock
    EclipseService eclipse;

    @Mock
    CacheService cacheService;

    @Mock
    ExtensionVersionIntegrityService integrityService;

    @Mock
    SimilarityCheckService similarityCheckService;

    @Mock
    ExtensionSizeLimitService sizeLimits;

    private LocalRegistryService registryService;

    private TempFile tempFile;

    @BeforeEach
    void setUp() {
        registryService = new LocalRegistryService(
                entityManager,
                repositories,
                extensions,
                versions,
                users,
                tokens,
                searchUtilService,
                validator,
                storageUtilService,
                eclipse,
                cacheService,
                integrityService,
                similarityCheckService,
                sizeLimits,
                new TrustedPublishingConfig(),
                new WebUiProperties(),
                Duration.ofSeconds(30));

        // A permissive default for a void method rather than a per-test expectation: the tests of
        // visibleUntil exercise a pure function and touch no mock at all.
        lenient().doNothing().when(eclipse).checkPublisherAgreement(any());

        // Publish tests supply a token that is meant to be live; the ones about an unusable token
        // say so for their own value.
        lenient().when(tokens.isTokenLive(anyString())).thenReturn(true);

        // Behave like a registry with no override configured unless a test says otherwise.
        var configDefault = new PublishingConfig().getMaxContentSize();
        lenient().when(sizeLimits.getCeiling()).thenReturn(configDefault);
        lenient().when(sizeLimits.getDefaultLimit()).thenReturn(configDefault);
        lenient().when(sizeLimits.resolveLimit(anyString(), anyString())).thenReturn(configDefault);
    }

    /**
     * Regression test for a lost-update-style race: {@code ExtensionService.publishVersion(...)} hands
     * the temp file off to the {@code @Async} publish pipeline once metadata validation succeeds - that
     * pipeline reads the file (storage upload, signing, checksum) on a background thread and deletes it
     * itself once done. If {@code publish()} also closed it in its own try-with-resources, it would
     * delete the file out from under that background thread almost immediately, since publishVersion
     * returns as soon as the async task is fired, well before that thread is guaranteed to have even
     * started reading - producing an intermittent NoSuchFileException on the temp .vsix path.
     */
    @Test
    void shouldNotDeleteTempFileOnceOwnershipIsHandedToAsyncPublish() throws IOException {
        tempFile = new TempFile("extension_", ".vsix");
        Files.write(tempFile.getPath(), createExtensionPackage("bar", "1.0.0"));

        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);

        var namespace = new Namespace();
        namespace.setName("foo");
        var extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("bar");
        var extVersion = new ExtensionVersion();
        extVersion.setId(42L);
        extVersion.setExtension(extension);
        extVersion.setVersion("1.0.0");

        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);

        when(extensions.createExtensionFile(any())).thenReturn(tempFile);
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(extensions.publishVersion(any(ExtensionProcessor.class), eq(tau))).thenReturn(extVersion);
        when(storageUtilService.getFiles(any(), any(String[].class))).thenReturn(Map.of(42L, List.of()));

        registryService.publish(new ByteArrayInputStream(new byte[0]), "tok");

        assertThat(Files.exists(tempFile.getPath()))
                .as(
                        "ownership of the temp file was handed to the async publish pipeline; "
                                + "the request thread must not delete it")
                .isTrue();
    }

    /**
     * The counterpart of the above: when publish() rejects before ever calling
     * extensions.publishVersion(...) (so ownership was never handed off), it must still clean up the
     * temp file itself - nothing else will.
     */
    @Test
    void shouldDeleteTempFileWhenRejectedBeforeHandoff() throws IOException {
        tempFile = new TempFile("extension_", ".vsix");
        Files.write(tempFile.getPath(), createExtensionPackage("bar", "1.0.0"));

        when(tokens.isTokenLive("tok")).thenReturn(true);
        when(extensions.createExtensionFile(any())).thenReturn(tempFile);
        // live, but its scope does not cover this package - rejected after the parse, before handoff
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(null);

        assertThatThrownBy(() -> registryService.publish(new ByteArrayInputStream(new byte[0]), "tok"))
                .isInstanceOf(ErrorResultException.class);

        assertThat(Files.exists(tempFile.getPath()))
                .as(
                        "ownership was never handed off (publishVersion was never called), so publish() "
                                + "must clean up the temp file itself")
                .isFalse();
    }

    @Test
    void shouldRejectNamespaceWhenSimilarNameExists() {
        // Build request with a name that collides with an existing namespace.
        var json = new NamespaceJson();
        json.setName("new-space");
        var user = new UserData();

        when(validator.validateNamespace("new-space")).thenReturn(Optional.empty());
        when(repositories.findNamespaceName("new-space")).thenReturn(null);
        when(similarityCheckService.isEnabled()).thenReturn(true);
        when(similarityCheckService.findSimilarNamespacesForCreation("new-space", user))
                .thenReturn(List.of(buildNamespace("new-space-1")));

        assertThatThrownBy(() -> registryService.createNamespace(json, user))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("too similar to existing namespace");

        verify(entityManager, never()).persist(any(Namespace.class));
    }

    @Test
    void shouldRejectExistingNamespaceBeforeSimilarityCheck() {
        // If the namespace already exists, we should fail fast and avoid extra work.
        var json = new NamespaceJson();
        json.setName("duplicate");
        var user = new UserData();

        when(validator.validateNamespace("duplicate")).thenReturn(Optional.empty());
        when(repositories.findNamespaceName("duplicate")).thenReturn("duplicate");

        assertThatThrownBy(() -> registryService.createNamespace(json, user))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Namespace already exists: duplicate");

        // No persistence and no similarity checks should occur when we bail out early.
        verify(entityManager, never()).persist(any(Namespace.class));
        verify(similarityCheckService, never()).findSimilarNamespacesForCreation(any(), any());
    }

    @Test
    void shouldCreateNamespaceAndAssignContributorRole() {
        // Happy path: namespace is new and not similar, so we persist both entities.
        var json = new NamespaceJson();
        json.setName("clean-ns");
        var user = new UserData();

        when(validator.validateNamespace("clean-ns")).thenReturn(Optional.empty());
        when(repositories.findNamespaceName("clean-ns")).thenReturn(null);
        when(similarityCheckService.isEnabled()).thenReturn(true);
        when(similarityCheckService.findSimilarNamespacesForCreation("clean-ns", user)).thenReturn(List.of());

        registryService.createNamespace(json, user);

        // Capture persisted entities to verify they are wired as expected.
        var namespaceCaptor = ArgumentCaptor.forClass(Namespace.class);
        var membershipCaptor = ArgumentCaptor.forClass(NamespaceMembership.class);

        verify(entityManager).persist(namespaceCaptor.capture());
        verify(entityManager).persist(membershipCaptor.capture());

        var persistedNamespace = namespaceCaptor.getValue();
        var persistedMembership = membershipCaptor.getValue();

        assertThat(persistedNamespace.getName()).isEqualTo("clean-ns");
        assertThat(persistedMembership.getNamespace()).isSameAs(persistedNamespace);
        assertThat(persistedMembership.getUser()).isSameAs(user);
        assertThat(persistedMembership.getRole()).isEqualTo(NamespaceMembership.ROLE_CONTRIBUTOR);
    }

    @Test
    void shouldHoldBackTheMostRecentChanges() {
        // A request that reaches the present is clamped to the lag, so an entry whose transaction may
        // still be committing is not reported and cannot be passed over.
        var now = LocalDateTime.parse("2026-01-14T09:30:11");

        assertThat(LocalRegistryService.visibleUntil(null, now, Duration.ofSeconds(30)))
                .isEqualTo(LocalDateTime.parse("2026-01-14T09:29:41"));
    }

    @Test
    void shouldHoldBackAnUntilInsideTheLag() {
        var now = LocalDateTime.parse("2026-01-14T09:30:11");
        var until = LocalDateTime.parse("2026-01-14T09:30:00");

        // Asking for entries closer to the present than the lag reports nothing beyond it rather than
        // exposing them early.
        assertThat(LocalRegistryService.visibleUntil(until, now, Duration.ofSeconds(30)))
                .isEqualTo(LocalDateTime.parse("2026-01-14T09:29:41"));
    }

    @Test
    void shouldNotHoldBackAHistoricalUntil() {
        var now = LocalDateTime.parse("2026-01-14T09:30:11");
        var until = LocalDateTime.parse("2026-01-01T00:00");

        // Those entries have long been committed, so the caller's bound is the restrictive one and is
        // left alone.
        assertThat(LocalRegistryService.visibleUntil(until, now, Duration.ofSeconds(30))).isEqualTo(until);
    }

    @Test
    void shouldReportEverythingWithoutALag() {
        // A deployment that turns the lag off gets the whole log, which is what a registry with no
        // concurrent writers can afford.
        var now = LocalDateTime.parse("2026-01-14T09:30:11");

        assertThat(LocalRegistryService.visibleUntil(null, now, Duration.ZERO)).isEqualTo(now);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile.getPath());
        }
    }

    /**
     * Builds a minimal valid .vsix package, matching the fixture RegistryAPITest uses for the same
     * purpose, so ExtensionProcessor can genuinely parse the namespace/extension name out of it.
     */
    private byte[] createExtensionPackage(String name, String version) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var archive = new ZipOutputStream(bytes);
        archive.putNextEntry(new ZipEntry("extension.vsixmanifest"));
        var vsixmanifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<PackageManifest Version=\"2.0.0\" xmlns=\"http://schemas.microsoft.com/developer/vsx-schema/2011\">"
                + "<Metadata>"
                + "<Identity Language=\"en-US\" Id=\"" + name + "\" Version=\"" + version + "\" Publisher=\"foo\" />"
                + "<DisplayName>foo</DisplayName>"
                + "<Description xml:space=\"preserve\"></Description>"
                + "<Tags></Tags>"
                + "<Categories>Other</Categories>"
                + "<GalleryFlags>Public</GalleryFlags>"
                + "</Metadata>"
                + "<Installation>"
                + "<InstallationTarget Id=\"Microsoft.VisualStudio.Code\"/>"
                + "</Installation>"
                + "<Dependencies/>"
                + "<Assets>"
                + "<Asset Type=\"Microsoft.VisualStudio.Code.Manifest\" Path=\"extension/package.json\" "
                + "Addressable=\"true\" />"
                + "</Assets>"
                + "</PackageManifest>";
        archive.write(vsixmanifest.getBytes());
        archive.closeEntry();
        archive.putNextEntry(new ZipEntry("extension/package.json"));
        var packageJson = "{"
                + "\"publisher\": \"foo\","
                + "\"name\": \"" + name + "\","
                + "\"version\": \"" + version + "\","
                + "\"displayName\": \"foo\""
                + "}";
        archive.write(packageJson.getBytes());
        archive.closeEntry();
        archive.finish();
        return bytes.toByteArray();
    }

    private Namespace buildNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        return namespace;
    }

    private NamespaceMembership buildMembership(UserData user, String namespaceName) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        var membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        return membership;
    }

    /**
     * A token that is missing, unknown, expired or deactivated ends in this 401 either way, so the
     * package is not worth writing to disk and parsing first - that is a file of up to the ceiling
     * for a caller with nothing usable. Liveness is all that can be asked: every scope judges an
     * action against the namespace its token is bound to, and that is still inside the package.
     */
    @Test
    void shouldRefuseAPublishOnAnUnusableTokenBeforeWritingThePackage() {
        when(tokens.isTokenLive("bogus")).thenReturn(false);

        assertThatThrownBy(() -> registryService.publish(new ByteArrayInputStream(new byte[0]), "bogus"))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(extensions);
    }

    /**
     * The resolved limit is specific to the namespace, and permission to publish there is otherwise
     * not established until {@code PublishExtensionVersionHandler}. Anyone holding a valid token
     * could read a namespace's override off the 413 by publishing packages of varying size.
     */
    @Test
    void shouldRefuseBeforeDisclosingTheLimitToSomeoneWhoCannotPublishThere() throws IOException {
        tempFile = new TempFile("extension_", ".vsix");
        var content = createExtensionPackage("bar", "1.0.0");
        Files.write(tempFile.getPath(), content);

        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);
        var namespace = buildNamespace("foo");

        when(extensions.createExtensionFile(any())).thenReturn(tempFile);
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(users.hasPublishPermission(token.getUser(), namespace)).thenReturn(false);

        assertThatThrownBy(() -> registryService.publish(new ByteArrayInputStream(new byte[0]), "tok"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("Insufficient access rights")
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // the limit is never even looked up, so there is nothing to leak
        verify(sizeLimits, never()).resolveLimit(any(), any());
        verify(extensions, never()).publishVersion(any(ExtensionProcessor.class), any());
    }

    /**
     * Stage two of the size check: the request body is streamed against the global ceiling, because
     * the namespace is unknown until the manifest is parsed. Once it is known, the resolved limit for
     * that namespace/extension applies, and a package over it is rejected before being published.
     */
    @Test
    void shouldRejectAPackageOverTheLimitResolvedForItsNamespace() throws IOException {
        tempFile = new TempFile("extension_", ".vsix");
        var content = createExtensionPackage("bar", "1.0.0");
        Files.write(tempFile.getPath(), content);

        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        var tau = new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);

        when(extensions.createExtensionFile(any())).thenReturn(tempFile);
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tau);
        when(sizeLimits.resolveLimit("foo", "bar")).thenReturn((long) content.length - 1);

        assertThatThrownBy(() -> registryService.publish(new ByteArrayInputStream(new byte[0]), "tok"))
                .isInstanceOf(ErrorResultException.class)
                .hasMessageContaining("exceeds the size limit")
                .hasMessageContaining("foo.bar")
                // exact counts, or one byte over reads as "1 MB exceeds the size limit of 1 MB"
                .hasMessageContaining(content.length + " bytes")
                .hasMessageContaining((content.length - 1) + " bytes")
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

        verify(extensions, never()).publishVersion(any(ExtensionProcessor.class), any());
    }

    /**
     * The two size fields answer different questions and come from different sources: the default is
     * what applies without an override, the ceiling is the most any namespace could publish. Swapping
     * them would be invisible on a registry that has no overrides configured.
     */
    @Test
    void shouldReportBothTheDefaultLimitAndTheCeiling() {
        registryService.registryVersion = "1.3.0";
        when(sizeLimits.getDefaultLimit()).thenReturn(512L);
        when(sizeLimits.getCeiling()).thenReturn(2048L);

        var json = registryService.getRegistryVersion();

        assertThat(json.getMaxExtensionSize()).isEqualTo(512L);
        assertThat(json.getMaxExtensionSizeCeiling()).isEqualTo(2048L);
    }

    @Test
    void sizeLimitRejectsAnInvalidToken() {
        when(tokens.useAccessToken(eq("bad"), any())).thenReturn(null);

        assertThatThrownBy(() -> registryService.getSizeLimit("foo", "bar", "bad"))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * A first publish asks about a namespace that does not exist yet, so this cannot 404 or demand a
     * permission there is nobody to hold. The default is the honest answer.
     */
    @Test
    void sizeLimitReturnsTheDefaultForANamespaceThatDoesNotExistYet() {
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tokenAuth());
        when(repositories.findNamespace("new")).thenReturn(null);
        when(sizeLimits.resolveLimit("new", "bar")).thenReturn(4096L);

        assertThat(registryService.getSizeLimit("new", "bar", "tok").getMaxSize()).isEqualTo(4096L);
    }

    @Test
    void sizeLimitRefusesANamespaceTheTokenMayNotPublishTo() {
        var namespace = new Namespace();
        namespace.setName("foo");
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tokenAuth());
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(users.hasPublishPermission(any(), eq(namespace))).thenReturn(false);

        assertThatThrownBy(() -> registryService.getSizeLimit("foo", "bar", "tok"))
                .isInstanceOf(ErrorResultException.class)
                .extracting(exc -> ((ErrorResultException) exc).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sizeLimitReturnsTheResolvedLimitForAPermittedNamespace() {
        var namespace = new Namespace();
        namespace.setName("foo");
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tokenAuth());
        when(repositories.findNamespace("foo")).thenReturn(namespace);
        when(users.hasPublishPermission(any(), eq(namespace))).thenReturn(true);
        when(sizeLimits.resolveLimit("foo", "bar")).thenReturn(123L);

        assertThat(registryService.getSizeLimit("foo", "bar", "tok").getMaxSize()).isEqualTo(123L);
    }

    /**
     * The token must not be consumed: a one-time token checked here and then deleted would leave the
     * publish it was checked for unable to authenticate. It must still carry the namespace and
     * extension, or a scoped token - every trusted publishing token is one - fails the scope match.
     */
    @Test
    void sizeLimitChecksTheTokenWithoutUsingIt() {
        when(tokens.useAccessToken(eq("tok"), any())).thenReturn(tokenAuth());
        when(repositories.findNamespace("foo")).thenReturn(null);

        registryService.getSizeLimit("foo", "bar", "tok");

        var action = ArgumentCaptor.forClass(AccessTokenAction.class);
        verify(tokens).useAccessToken(eq("tok"), action.capture());
        assertThat(action.getValue().isUsing()).isFalse();
        assertThat(action.getValue().namespace()).contains("foo");
        assertThat(action.getValue().extension()).contains("bar");
    }

    private AccessTokenAuthentication tokenAuth() {
        var token = new PersonalAccessToken();
        token.setUser(new UserData());
        token.setType(PersonalAccessTokenType.LLT);
        return new AccessTokenAuthentication(token.getUser(), token.getType(), token.getId(), null);
    }
}
