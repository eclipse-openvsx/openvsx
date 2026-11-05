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
package org.eclipse.openvsx.publish;

import java.nio.file.Files;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionChange;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublishExtensionVersionService#activateExtension}.
 * <p>
 * Partly the soft-delete guard: a removed version is an immutable tombstone and must never be reactivated,
 * even though it is inactive and could otherwise be a reactivation candidate. This can happen when a
 * version is soft-deleted while an asynchronous scan for it is still in flight and the scan later completes
 * (or is allowed by an admin) and tries to activate it.
 * <p>
 * Partly the entry this appends to the changes feed log, which is where a version's publication enters the
 * feed -- including the instant it is reported at, as the feed is ordered by it.
 */
@ExtendWith(MockitoExtension.class)
class PublishExtensionVersionServiceTest {

    @Mock
    RepositoryService repositories;
    @Mock
    EntityManager entityManager;
    @Mock
    StorageUtilService storageUtil;
    @Mock
    ExtensionService extensions;

    private PublishExtensionVersionService svc;

    @BeforeEach
    void setUp() {
        svc = new PublishExtensionVersionService(repositories, entityManager, storageUtil);
    }

    @Test
    void activateExtension_activatesLiveVersion() {
        var extVersion = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        svc.activateExtension(extVersion, extensions);

        assertThat(extVersion.isActive()).isTrue();
        verify(extensions).updateExtension(extVersion.getExtension());
    }

    // The column answers "why is this version not active", so it has no business outliving the version
    // being active: an attempt that got there supersedes whatever an earlier one failed on.
    @Test
    void activateExtension_clearsAnEarlierPublishFailure() {
        var extVersion = version(1L, false);
        extVersion.setPublishError("java.lang.OutOfMemoryError: Java heap space");
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        svc.activateExtension(extVersion, extensions);

        assertThat(extVersion.getPublishError()).isNull();
    }

    @Test
    void recordPublishError_writesTheReasonOntoTheVersion() {
        var extVersion = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        svc.recordPublishError(extVersion, "java.lang.OutOfMemoryError: Java heap space");

        assertThat(extVersion.getPublishError()).isEqualTo("java.lang.OutOfMemoryError: Java heap space");
    }

    // The row can be purged while the attempt that failed is still unwinding; there is then nothing left
    // to annotate, and trying to would turn a failed publish into a second, unrelated failure.
    @Test
    void recordPublishError_ignoresAVersionThatIsGone() {
        var extVersion = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(null);

        assertThatCode(() -> svc.recordPublishError(extVersion, "boom")).doesNotThrowAnyException();
    }

    @Test
    void activateExtension_recordsThePublication() {
        var extVersion = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        svc.activateExtension(extVersion, extensions);

        // Becoming active is the transition the changes feed reports as the version's publication.
        verify(repositories)
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.ACTIVE), any());
    }

    @Test
    void activateExtension_recordsThePublicationAtTheInstantTheVersionBecomesVisible() {
        // Activation can wait a long time on a scan completing or on an admin releasing a quarantined
        // version, so the version's own timestamp is well in the past by the time it becomes visible.
        var extVersion = version(1L, false);
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        var before = TimeUtil.getCurrentUTC();
        svc.activateExtension(extVersion, extensions);

        var changedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repositories)
                .recordExtensionVersionChange(
                        eq(extVersion),
                        eq(ExtensionVersionState.ACTIVE),
                        changedAt.capture());
        // The feed is ordered by this instant, so it has to be when the version actually became
        // visible. Recording it at the version's older timestamp would sort the entry into a part of
        // the feed that consumers have already read past, and they would never see the publication.
        assertThat(changedAt.getValue())
                .as("the publication is reported when the version becomes visible, not when it was uploaded")
                .isAfterOrEqualTo(before);
    }

    @Test
    void activateExtension_doesNotRecordAnAlreadyActiveVersion() {
        var extVersion = version(1L, false);
        extVersion.setActive(true);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        svc.activateExtension(extVersion, extensions);

        // Nothing changed publicly, and the log is append-only: a second entry would report a
        // publication that never happened.
        verify(repositories, never()).recordExtensionVersionChange(any(), any(), any());
    }

    @Test
    void activateExtension_refusesRemovedVersion() {
        var extVersion = version(1L, true);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);

        svc.activateExtension(extVersion, extensions);

        assertThat(extVersion.isActive())
                .as("a soft-deleted tombstone must never be reactivated")
                .isFalse();
        verify(extensions, never()).updateExtension(any());
        // The version never became visible, so the feed has nothing to report about it.
        verify(repositories, never()).recordExtensionVersionChange(any(), any(), any());
    }

    @Test
    void activateExtension_refusesMissingVersion() {
        // The row was purged (hard-deleted) between fetch and activation.
        var extVersion = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(null);

        svc.activateExtension(extVersion, extensions);

        assertThat(extVersion.isActive()).isFalse();
        verify(extensions, never()).updateExtension(any());
        verify(repositories, never()).recordExtensionVersionChange(any(), any(), any());
    }

    @Test
    void mirrorResource_recordsTheSizeOfTheExtractedBytes() throws Exception {
        // Mirror mode doesn't upload the bytes to storage -- they're served on the fly from the
        // origin registry instead -- but the TempFile still holds real bytes extracted from the
        // mirrored package, so the size is there for the taking.
        var resource = new FileResource();
        try (var tempFile = new TempFile("mirror_", ".tmp")) {
            tempFile.setResource(resource);
            Files.writeString(tempFile.getPath(), "package contents");

            svc.mirrorResource(tempFile);

            assertThat(resource.getSize()).isEqualTo(Files.size(tempFile.getPath()));
        }
        verify(entityManager).persist(resource);
    }

    // #989: markExtensionAsPotentiallyMalicious used to merge the whole detached extVersion to set
    // one boolean, so any column that had moved since the caller loaded it was reverted - the same
    // failure mode that once left published extensions inactive. It now writes the flag on the
    // managed row, matching what activateExtension above already did.
    @Test
    void markExtensionAsPotentiallyMalicious_flagsTheManagedRow() {
        var stale = version(1L, false);
        var managed = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(managed);

        svc.markExtensionAsPotentiallyMalicious(stale);

        assertThat(managed.isPotentiallyMalicious()).isTrue();
        verify(entityManager, never()).merge(any());
    }

    @Test
    void markExtensionAsPotentiallyMalicious_leavesConcurrentChangesAlone() {
        // The caller's copy was loaded while the version was still inactive; it has since been
        // activated. Merging that stale copy back would have deactivated it again.
        var stale = version(1L, false);
        stale.setActive(false);
        var managed = version(1L, false);
        managed.setActive(true);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(managed);

        svc.markExtensionAsPotentiallyMalicious(stale);

        assertThat(managed.isPotentiallyMalicious()).isTrue();
        assertThat(managed.isActive()).as("a concurrent activation must survive the flag write").isTrue();
    }

    // The row can be purged between the caller loading it and this running; merge would have tried
    // to resurrect it rather than doing nothing.
    @Test
    void markExtensionAsPotentiallyMalicious_ignoresAVersionThatIsGone() {
        var stale = version(1L, false);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(null);

        svc.markExtensionAsPotentiallyMalicious(stale);

        verify(entityManager, never()).merge(any());
        verify(entityManager, never()).persist(any());
    }

    private ExtensionVersion version(long id, boolean removed) {
        var namespace = new Namespace();
        namespace.setName("redhat");

        var extension = new Extension();
        extension.setName("vscode-yaml");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setId(id);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setActive(false);
        extVersion.setRemoved(removed);
        extVersion.setExtension(extension);
        return extVersion;
    }
}
