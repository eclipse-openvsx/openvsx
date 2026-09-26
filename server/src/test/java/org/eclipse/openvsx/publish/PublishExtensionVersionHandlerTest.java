/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.publish;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.extension_control.ExtensionControlService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;
import org.eclipse.openvsx.util.auth.LoggedInAuthentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishExtensionVersionHandlerTest {

    @Mock
    PublishExtensionVersionService publishService;

    @Mock
    ExtensionVersionIntegrityService integrityService;

    @Mock
    EntityManager entityManager;

    @Mock
    RepositoryService repositories;

    @Mock
    JobRequestScheduler scheduler;

    @Mock
    UserService users;

    @Mock
    ExtensionValidator validator;

    @Mock
    ExtensionControlService extensionControl;

    @Mock
    ExtensionScanService scanService;

    private PublishingConfig config;

    private PublishExtensionVersionHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        config = new PublishingConfig();

        handler = new PublishExtensionVersionHandler(
                config,
                publishService,
                integrityService,
                entityManager,
                repositories,
                scheduler,
                users,
                validator,
                extensionControl,
                scanService,
                false);

        // Lenient: not all tests need this mock
        org.mockito.Mockito.lenient()
                .when(extensionControl.getMaliciousExtensionIds())
                .thenReturn(Collections.emptyList());

        // Lenient: only the tests that reach the creation of an extension look the publisher's
        // namespaces up, and the display name conflict lookup answers "unused" unless stubbed.
        lenient().when(repositories.findMemberships(any(UserData.class))).thenReturn(Streamable.empty());
    }

    @Test
    void shouldCreateExtensionWhenNamespaceExists() throws IOException {
        // Happy path: extension version gets persisted.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            when(processor.getExtensionDependencies()).thenReturn(List.of());
            when(processor.getBundledExtensions()).thenReturn(List.of());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "demo",
                            "2.0.0",
                            "Demo OK"));

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(validator.validateMetadata(metadata)).thenReturn(List.of());
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);

            var capturedExtension = ArgumentCaptor.forClass(Extension.class);

            var result = handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);

            verify(entityManager).persist(capturedExtension.capture());
            verify(entityManager).persist(metadata);
            assertThat(result).isSameAs(metadata);
            assertThat(result.getPublishedBy()).isEqualTo(user);
            assertThat(result.getExtension()).isSameAs(capturedExtension.getValue());
            assertThat(result.getExtension().getNamespace()).isSameAs(namespace);
        }
    }

    @Test
    void shouldFailWhenMaliciousListRefreshFailsButLastKnownListFlagsIt() throws IOException {
        // getMaliciousExtensionIds() throws on a fetch failure rather than inventing a return value
        // (see ExtensionControlService); isMalicious() must still catch that and fall back to the last
        // known list rather than silently treating the extension as clean.
        when(extensionControl.getMaliciousExtensionIds()).thenThrow(new IOException("connection reset"));
        when(extensionControl.getLastKnownMaliciousExtensionIds())
                .thenReturn(List.of(NamingUtil.toExtensionId("publisher", "demo")));

        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("known malicious extension");
        }
    }

    @Test
    void shouldFailWhenMaliciousListLookupThrowsUncheckedExceptionButLastKnownListFlagsIt() throws IOException {
        // getMaliciousExtensionIds() can also fail with a plain, unanticipated RuntimeException (not
        // just its own IOException/JacksonException) - isMalicious()'s catch must be broad enough to
        // fall back on that too, not escape and break the publish request outright.
        when(extensionControl.getMaliciousExtensionIds())
                .thenThrow(new RuntimeException("Redis connection refused"));
        when(extensionControl.getLastKnownMaliciousExtensionIds())
                .thenReturn(List.of(NamingUtil.toExtensionId("publisher", "demo")));

        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("known malicious extension");
        }
    }

    @Test
    void shouldRecordTheTokenTypeUsedToPublish() throws IOException {
        // The trusted-publisher badge (ExtensionVersion#toExtensionVersionJson) is driven off
        // publishedWithTt, so it must actually be set from whatever token type authenticated the
        // publish request, not silently dropped.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            when(processor.getExtensionDependencies()).thenReturn(List.of());
            when(processor.getBundledExtensions()).thenReturn(List.of());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "demo",
                            "2.0.0",
                            "Demo OK"));

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var ata = new AccessTokenAuthentication(user, PersonalAccessTokenType.TPT, 1L, null);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(validator.validateMetadata(metadata)).thenReturn(List.of());
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);

            var result = handler.createExtensionVersion(processor, ata, LocalDateTime.now(), false);

            assertThat(result.getPublishedWithTt()).isEqualTo(PersonalAccessTokenType.TPT);
            // A one-time token is deleted as it is used, before this row is written, so referencing it
            // makes the insert fail on extension_version_published_with_id_fkey. The publish is
            // identified by the workflow claims in published_provenance instead.
            assertThat(result.getPublishedWithId()).isNull();
        }
    }

    // A long-lived token is only ever deactivated, never deleted, so the reference is safe to record and
    // is what answers "what did this credential publish" after it leaks.
    @Test
    void recordsTheTokenALongLivedPublishUsed() throws IOException {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            when(processor.getExtensionDependencies()).thenReturn(List.of());
            when(processor.getBundledExtensions()).thenReturn(List.of());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "demo",
                            "2.0.0",
                            "Demo OK"));

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var ata = new AccessTokenAuthentication(user, PersonalAccessTokenType.LLT, 42L, null);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(validator.validateMetadata(metadata)).thenReturn(List.of());
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);

            var result = handler.createExtensionVersion(processor, ata, LocalDateTime.now(), false);

            assertThat(result.getPublishedWithId()).isEqualTo(42L);
        }
    }

    @Test
    void shouldFailWhenFileResourceCollidesWithDerivedDownloadName() throws IOException {
        // TOB-OVSX-15: object keys for a version share one flat namespace, so a README (or any other
        // asset) declared under the binary's derived name would silently overwrite it in storage once
        // uploaded. That resource name is attacker-controlled (it comes from the VSIX manifest), so
        // publication of a colliding package must be rejected before anything is stored.
        try (
                var processor = org.mockito.Mockito.mock(ExtensionProcessor.class);
                var readme = new TempFile("readme_", ".md")
        ) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));

            // Matches what createExtensionVersion will derive for the download once extVersion is wired
            // up with its extension/namespace, which happens after this point in the real flow.
            var maliciousName = NamingUtil.toFileFormat("publisher", "demo", "any", "2.0.0", ".vsix");
            var readmeResource = new FileResource();
            readmeResource.setName(maliciousName);
            readme.setResource(readmeResource);

            doAnswer(invocation -> {
                Consumer<TempFile> consumer = invocation.getArgument(1);
                consumer.accept(readme);
                return null;
            }).when(processor).getFileResources(any(), any());

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining(maliciousName);

            // the version must not be persisted once its file resources are rejected
            org.mockito.Mockito.verify(entityManager, never()).persist(metadata);
        }
    }

    @Test
    void shouldFailWhenTwoFileResourcesHaveTheSameName() throws IOException {
        try (
                var processor = org.mockito.Mockito.mock(ExtensionProcessor.class);
                var changelog = new TempFile("changelog_", ".md");
                var license = new TempFile("license_", ".md")
        ) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));

            var changelogResource = new FileResource();
            changelogResource.setName("CHANGELOG.md");
            changelog.setResource(changelogResource);

            // The license is repointed at the same file as the changelog, so both resolve to the same
            // name under different resource types.
            var licenseResource = new FileResource();
            licenseResource.setName("CHANGELOG.md");
            license.setResource(licenseResource);

            doAnswer(invocation -> {
                Consumer<TempFile> consumer = invocation.getArgument(1);
                consumer.accept(changelog);
                consumer.accept(license);
                return null;
            }).when(processor).getFileResources(any(), any());

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("CHANGELOG.md");
        }
    }

    @Test
    void shouldReportAllFileResourceCollisionsAtOnce() throws IOException {
        // Reports every colliding name in one error instead of failing on the first, so a publisher
        // does not have to republish repeatedly just to discover the next collision.
        try (
                var processor = org.mockito.Mockito.mock(ExtensionProcessor.class);
                var readme = new TempFile("readme_", ".md");
                var changelog = new TempFile("changelog_", ".md");
                var license = new TempFile("license_", ".md")
        ) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));

            var maliciousName = NamingUtil.toFileFormat("publisher", "demo", "any", "2.0.0", ".vsix");
            var readmeResource = new FileResource();
            readmeResource.setName(maliciousName);
            readme.setResource(readmeResource);

            var changelogResource = new FileResource();
            changelogResource.setName("CHANGELOG.md");
            changelog.setResource(changelogResource);

            // A second, independent collision alongside the readme/binary one above.
            var licenseResource = new FileResource();
            licenseResource.setName("CHANGELOG.md");
            license.setResource(licenseResource);

            doAnswer(invocation -> {
                Consumer<TempFile> consumer = invocation.getArgument(1);
                consumer.accept(readme);
                consumer.accept(changelog);
                consumer.accept(license);
                return null;
            }).when(processor).getFileResources(any(), any());

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Multiple file name collisions")
                    .hasMessageContaining(maliciousName)
                    .hasMessageContaining("CHANGELOG.md");
        }
    }

    @Test
    void shouldFailWhenNamespaceDoesNotExist() {
        // When namespace doesn't exist, handler should throw an error.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            when(processor.getNamespace()).thenReturn("unknown");

            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("unknown")).thenReturn(null);

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Unknown publisher");
        }
    }

    @Test
    void shouldFailWhenImageFormatIsDisallowed() throws IOException {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", "test.svg", processor);

            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "demo",
                            "2.0.0",
                            "Demo OK"));

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("uses an unsupported icon format");
        }
    }

    @Test
    void shouldSucceedWhenImageFormatIsAllowed() throws IOException {
        var previousUnsupportedIconFormats = config.getUnsupportedIconFormats();
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            config.setUnsupportedIconFormats(List.of());

            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", "test.svg", processor);

            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "demo",
                            "2.0.0",
                            "Demo OK"));

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion(metadata.getVersion())).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());

            var ev = handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);
            assertThat(ev).isNotNull();
        } finally {
            config.setUnsupportedIconFormats(previousUnsupportedIconFormats);
        }
    }

    @Test
    void shouldFailWhenPackageJsonDoesNotMatchManifest() throws IOException {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion(metadata.getVersion())).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());

            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher1",
                            "demo",
                            "2.0.0",
                            "Demo OK"));

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Publisher in extension.vsixmanifest");

            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "DemO1",
                            "2.0.0",
                            "Demo OK"));

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Extension name in extension.vsixmanifest");

            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata(
                            "publisher",
                            "demo",
                            "9.9.9",
                            "Demo OK"));

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Extension version in extension.vsixmanifest");
        }
    }

    @Test
    void shouldPassPreconditionsForUnpublishedVersion() {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            when(processor.getNamespace()).thenReturn("publisher");
            when(processor.getExtensionName()).thenReturn("demo");
            when(processor.getVersion()).thenReturn("2.0.0");
            when(processor.getTargetPlatform()).thenReturn(TargetPlatform.NAME_UNIVERSAL);
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(repositories.findVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, "demo", "publisher"))
                    .thenReturn(null);

            assertThatCode(() -> handler.checkPublishPreconditions(processor, user)).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldFailPreconditionsWithoutPublishPermission() {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            when(processor.getNamespace()).thenReturn("publisher");
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(false);

            assertThatThrownBy(() -> handler.checkPublishPreconditions(processor, user))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Insufficient access rights for publisher: publisher");

            // the package is not inspected any further once the access rights are missing
            verify(repositories, never()).findVersion(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Test
    void shouldFailPreconditionsWithUnknownNamespace() {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var user = new UserData();

            when(processor.getNamespace()).thenReturn("unknown");
            when(repositories.findNamespace("unknown")).thenReturn(null);

            assertThatThrownBy(() -> handler.checkPublishPreconditions(processor, user))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Unknown publisher: unknown");
        }
    }

    @Test
    void shouldFailPreconditionsForAlreadyPublishedVersion() {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            var existing = new ExtensionVersion();
            existing.setVersion("2.0.0");
            existing.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            existing.setActive(true);

            when(processor.getNamespace()).thenReturn("publisher");
            when(processor.getExtensionName()).thenReturn("demo");
            when(processor.getVersion()).thenReturn("2.0.0");
            when(processor.getTargetPlatform()).thenReturn(TargetPlatform.NAME_UNIVERSAL);
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(repositories.findVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, "demo", "publisher"))
                    .thenReturn(existing);

            assertThatThrownBy(() -> handler.checkPublishPreconditions(processor, user))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("is already published.");
        }
    }

    @Test
    void shouldFailPreconditionsForRemovedVersion() {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            var tombstone = new ExtensionVersion();
            tombstone.setVersion("2.0.0");
            tombstone.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            tombstone.setActive(false);
            tombstone.setRemoved(true);

            when(processor.getNamespace()).thenReturn("publisher");
            when(processor.getExtensionName()).thenReturn("demo");
            when(processor.getVersion()).thenReturn("2.0.0");
            when(processor.getTargetPlatform()).thenReturn(TargetPlatform.NAME_UNIVERSAL);
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(repositories.findVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, "demo", "publisher"))
                    .thenReturn(tombstone);

            assertThatThrownBy(() -> handler.checkPublishPreconditions(processor, user))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("stays permanently reserved");
        }
    }

    @Test
    void shouldRejectNewExtensionUsingTheDisplayNameOfAnotherExtension() throws IOException {
        // Publishing under the exact display name of an existing extension is the cheapest way to be
        // taken for it, so the package is rejected before any part of it is persisted.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);
            when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any()))
                    .thenReturn(buildExtension("otherpublisher", "other-demo"));

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Display name 'Demo OK' is already used by")
                    .hasMessageContaining("otherpublisher.other-demo");

            verify(entityManager, never()).persist(metadata);
            verify(entityManager, never()).persist(any(Extension.class));
        }
    }

    @Test
    void shouldNotTreatTheOwnNamespacesOfThePublisherAsADisplayNameConflict() throws IOException {
        // A publisher shipping two extensions under one display name impersonates nobody, so neither
        // the namespace published to nor any other namespace they belong to can conflict.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            var membership = new NamespaceMembership();
            membership.setUser(user);
            membership.setNamespace(buildNamespace("other-namespace-of-the-publisher"));
            membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);
            when(repositories.findMemberships(user)).thenReturn(Streamable.of(membership));

            handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);

            var excludedNamespaces = ArgumentCaptor.forClass(Collection.class);
            verify(repositories).findActiveExtensionByDisplayName(eq("Demo OK"), excludedNamespaces.capture());
            assertThat(excludedNamespaces.getValue())
                    .containsExactlyInAnyOrder("publisher", "other-namespace-of-the-publisher");
        }
    }

    @Test
    void shouldNotCheckTheDisplayNameOfAVersionKeepingTheNameTheExtensionAlreadyShows() throws IOException {
        // A routine version bump carries the name its extension already shows, so it adopts nothing and
        // is not checked. This is what grandfathers the extensions that already share a display name
        // with another -- the registry holds many such pairs, predating this check -- which would
        // otherwise be left unable to publish anything at all.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            var existingExtension = buildExtension("publisher", "demo");

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(existingExtension);
            when(repositories.findLatestVersion(existingExtension, null, false, true))
                    .thenReturn(buildVersionShowing("Demo OK"));

            handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);

            verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
        }
    }

    @Test
    void shouldRejectAVersionRenamingAnExistingExtensionOntoTheDisplayNameOfAnother() throws IOException {
        // The escalation the new-extension check alone would leave open: enter the registry under a name
        // of one's own, pass, then take the display name of a popular extension in the next version --
        // the manifest being the source of truth for the name the registry goes on to show.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            var existingExtension = buildExtension("publisher", "demo");

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(existingExtension);
            when(repositories.findLatestVersion(existingExtension, null, false, true))
                    .thenReturn(buildVersionShowing("Some Name Of Its Own"));
            when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any()))
                    .thenReturn(buildExtension("otherpublisher", "other-demo"));

            assertThatThrownBy(() -> handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Display name 'Demo OK' is already used by")
                    .hasMessageContaining("otherpublisher.other-demo");

            verify(entityManager, never()).persist(metadata);
        }
    }

    @Test
    void shouldAllowAVersionRenamingAnExistingExtensionToADisplayNameNobodyHolds() throws IOException {
        // Renaming is legitimate; it is only rejected when the name being taken is another extension's.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            var existingExtension = buildExtension("publisher", "demo");

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(existingExtension);
            when(repositories.findLatestVersion(existingExtension, null, false, true))
                    .thenReturn(buildVersionShowing("Some Name Of Its Own"));
            when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any())).thenReturn(null);

            handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);

            // The rename was checked rather than waved through, and nothing held the name.
            verify(repositories).findActiveExtensionByDisplayName(eq("Demo OK"), any());
        }
    }

    @Test
    void shouldNotTreatACasingOrWhitespaceOnlyDifferenceAsRenamingTheExtension() throws IOException {
        // The conflict lookup normalises casing and surrounding whitespace away, so a version differing
        // only there shows the name its extension already holds. Counting that as a rename would let a
        // stray space break a publisher the unchanged-name path had grandfathered in.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var metadata = mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);
            metadata.setDisplayName("  demo ok  ");

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            var existingExtension = buildExtension("publisher", "demo");

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(existingExtension);
            when(repositories.findLatestVersion(existingExtension, null, false, true))
                    .thenReturn(buildVersionShowing("Demo OK"));

            handler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);

            verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
        }
    }

    @Test
    void shouldNotCheckTheDisplayNameWhenMirroringAnotherRegistry() throws IOException {
        // A mirror has to end up with what its upstream holds, duplicate display names included:
        // rejecting one protects nobody from an extension that is published upstream anyway, and would
        // leave the mirror permanently missing it.
        var mirroringHandler = new PublishExtensionVersionHandler(
                config,
                publishService,
                integrityService,
                entityManager,
                repositories,
                scheduler,
                users,
                validator,
                extensionControl,
                scanService,
                true);

        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            mockExtensionVersion("publisher", "demo", "2.0.0", null, processor);

            var namespace = buildNamespace("publisher");
            var user = new UserData();
            var liu = new LoggedInAuthentication(user);

            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(validator.validateExtensionVersion("2.0.0")).thenReturn(Optional.empty());
            when(validator.validateExtensionName("demo")).thenReturn(Optional.empty());
            when(processor.getPackageMetadata()).thenReturn(
                    new ExtensionProcessor.PackageMetadata("publisher", "demo", "2.0.0", "Demo OK"));
            when(repositories.findExtensionForUpdate("demo", "publisher")).thenReturn(null);

            mirroringHandler.createExtensionVersion(processor, liu, LocalDateTime.now(), false);

            verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
        }
    }

    @Test
    void shouldFailPreconditionsWhenTheDisplayNameIsTakenByAnotherExtension() {
        // Rejected up front rather than after scanning, so a package that cannot be published in the
        // first place does not occupy the scanners.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            when(processor.getNamespace()).thenReturn("publisher");
            when(processor.getExtensionName()).thenReturn("demo");
            when(processor.getVersion()).thenReturn("2.0.0");
            when(processor.getTargetPlatform()).thenReturn(TargetPlatform.NAME_UNIVERSAL);
            when(processor.getDisplayName()).thenReturn("Demo OK");
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(repositories.findVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, "demo", "publisher"))
                    .thenReturn(null);
            when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any()))
                    .thenReturn(buildExtension("otherpublisher", "other-demo"));

            assertThatThrownBy(() -> handler.checkPublishPreconditions(processor, user))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Display name 'Demo OK' is already used by");
        }
    }

    @Test
    void shouldPassPreconditionsForAFurtherVersionKeepingTheNameTheExtensionAlreadyShows() {
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            when(processor.getNamespace()).thenReturn("publisher");
            when(processor.getExtensionName()).thenReturn("demo");
            when(processor.getVersion()).thenReturn("2.0.0");
            when(processor.getTargetPlatform()).thenReturn(TargetPlatform.NAME_UNIVERSAL);
            when(processor.getDisplayName()).thenReturn("Demo OK");
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(repositories.findVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, "demo", "publisher"))
                    .thenReturn(null);
            when(repositories.findLatestVersion("publisher", "demo", null, false, true))
                    .thenReturn(buildVersionShowing("Demo OK"));

            assertThatCode(() -> handler.checkPublishPreconditions(processor, user)).doesNotThrowAnyException();

            verify(repositories, never()).findActiveExtensionByDisplayName(anyString(), any());
        }
    }

    @Test
    void shouldFailPreconditionsWhenAVersionRenamesAnExistingExtensionOntoATakenDisplayName() {
        // The escalation path is rejected up front too, so a renaming package that cannot be published
        // does not occupy the scanners either.
        try (var processor = org.mockito.Mockito.mock(ExtensionProcessor.class)) {
            var namespace = buildNamespace("publisher");
            var user = new UserData();

            when(processor.getNamespace()).thenReturn("publisher");
            when(processor.getExtensionName()).thenReturn("demo");
            when(processor.getVersion()).thenReturn("2.0.0");
            when(processor.getTargetPlatform()).thenReturn(TargetPlatform.NAME_UNIVERSAL);
            when(processor.getDisplayName()).thenReturn("Demo OK");
            when(repositories.findNamespace("publisher")).thenReturn(namespace);
            when(users.hasPublishPermission(user, namespace)).thenReturn(true);
            when(repositories.findVersion("2.0.0", TargetPlatform.NAME_UNIVERSAL, "demo", "publisher"))
                    .thenReturn(null);
            when(repositories.findLatestVersion("publisher", "demo", null, false, true))
                    .thenReturn(buildVersionShowing("Some Name Of Its Own"));
            when(repositories.findActiveExtensionByDisplayName(eq("Demo OK"), any()))
                    .thenReturn(buildExtension("otherpublisher", "other-demo"));

            assertThatThrownBy(() -> handler.checkPublishPreconditions(processor, user))
                    .isInstanceOf(ErrorResultException.class)
                    .hasMessageContaining("Display name 'Demo OK' is already used by")
                    .hasMessageContaining("otherpublisher.other-demo");
        }
    }

    private ExtensionVersion mockExtensionVersion(
            String namespace,
            String name,
            String version,
            String iconPath,
            ExtensionProcessor processor
    ) throws IOException {
        when(processor.getNamespace()).thenReturn(namespace);
        when(processor.getExtensionName()).thenReturn(name);
        when(processor.getVersion()).thenReturn(version);
        if (iconPath != null) {
            when(processor.getIconPath()).thenReturn(iconPath);
        }

        // Lenient: the tests that fail before an extension row is reached never read it
        lenient().when(processor.getDisplayName()).thenReturn("Demo OK");

        var ev = new ExtensionVersion();
        ev.setDisplayName("Demo OK");
        ev.setVersion("2.0.0");
        ev.setTargetPlatform("any");
        when(processor.getMetadata(anyInt(), anyInt())).thenReturn(ev);

        return ev;
    }

    /** The version the registry currently shows for an extension, for the rename comparison. */
    private ExtensionVersion buildVersionShowing(String displayName) {
        var extVersion = new ExtensionVersion();
        extVersion.setDisplayName(displayName);
        return extVersion;
    }

    private Extension buildExtension(String namespaceName, String extensionName) {
        var extension = new Extension();
        extension.setName(extensionName);
        extension.setNamespace(buildNamespace(namespaceName));
        return extension;
    }

    private Namespace buildNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        return namespace;
    }
}
