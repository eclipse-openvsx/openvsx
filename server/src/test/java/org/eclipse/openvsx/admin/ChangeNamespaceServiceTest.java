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
package org.eclipse.openvsx.admin;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ExtensionVersionState;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangeNamespaceService}.
 * <p>
 * Mainly that renaming a namespace is reported on the registry changes feed: a moved version's old
 * (namespace, extension, version) tuple is withdrawn and, if still active, its new tuple is announced --
 * rather than the rename leaving the feed unaware of it and consumers never learning the version moved
 * (#2244).
 */
@ExtendWith(MockitoExtension.class)
class ChangeNamespaceServiceTest {

    @Mock
    RepositoryService repositories;

    @Mock
    EntityManager entityManager;

    @Mock
    CacheService cache;

    @Mock
    SearchUtilService search;

    @InjectMocks
    ChangeNamespaceService service;

    private long idSequence = 0;

    private Namespace namespace(String name) {
        var namespace = new Namespace();
        namespace.setId(++idSequence);
        namespace.setName(name);
        return namespace;
    }

    private Extension extension(Namespace namespace) {
        var extension = new Extension();
        extension.setId(++idSequence);
        extension.setName("ext");
        extension.setNamespace(namespace);
        return extension;
    }

    private ExtensionVersion version(Extension extension, boolean active) {
        var extVersion = new ExtensionVersion();
        extVersion.setId(++idSequence);
        extVersion.setExtension(extension);
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setActive(active);
        return extVersion;
    }

    private void stubRename(Namespace oldNamespace, Namespace newNamespace, Extension extension) {
        when(repositories.findExtensions(oldNamespace)).thenReturn(Streamable.of(extension));
        when(repositories.findMemberships(oldNamespace)).thenReturn(Streamable.empty());
        when(repositories.findMemberships(newNamespace)).thenReturn(Streamable.empty());
    }

    @Test
    void renamingReportsAnActiveVersionAsMovedToTheNewNamespace() {
        var oldNamespace = namespace("old");
        var newNamespace = namespace("new");
        var extension = extension(oldNamespace);
        var extVersion = version(extension, true);
        stubRename(oldNamespace, newNamespace, extension);
        when(repositories.findVersions(extension)).thenReturn(Streamable.of(extVersion));
        // the feed already told consumers this version is available
        when(repositories.wasReportedAsAvailable(extVersion)).thenReturn(true);

        // recordExtensionVersionChange takes no namespace argument -- it reads the live Extension
        // reference at call time, so capture that at each invocation to verify old-then-new, not just
        // that the two calls happen in that order regardless of when the swap happens.
        var namespacesAtCallTime = new ArrayList<String>();
        doAnswer(invocation -> {
            namespacesAtCallTime.add(extVersion.getExtension().getNamespace().getName());
            return null;
        }).when(repositories).recordExtensionVersionChange(eq(extVersion), any(), any());

        service.changeNamespaceInDatabase(newNamespace, oldNamespace, List.of(), true, false);

        assertThat(extension.getNamespace()).isEqualTo(newNamespace);
        assertThat(namespacesAtCallTime).containsExactly("old", "new");
        // the old tuple is withdrawn before the new one is announced, matching the order the move
        // actually happens in
        var order = inOrder(repositories);
        order.verify(repositories)
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.REMOVED), any());
        order.verify(repositories)
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.ACTIVE), any());
    }

    @Test
    void renamingDoesNotWithdrawAVersionTheFeedNeverReported() {
        var oldNamespace = namespace("old");
        var newNamespace = namespace("new");
        var extension = extension(oldNamespace);
        var extVersion = version(extension, true);
        stubRename(oldNamespace, newNamespace, extension);
        when(repositories.findVersions(extension)).thenReturn(Streamable.of(extVersion));
        // e.g. it predates the feed and was never publicly announced
        when(repositories.wasReportedAsAvailable(extVersion)).thenReturn(false);

        service.changeNamespaceInDatabase(newNamespace, oldNamespace, List.of(), true, false);

        // nothing to withdraw -- same rule as for a deletion or purge of a version the feed never reported
        verify(repositories, never())
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.REMOVED), any());
        // still active, so it is announced under the new namespace regardless
        verify(repositories)
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.ACTIVE), any());
    }

    @Test
    void renamingDoesNotAnnounceAnInactiveVersionUnderTheNewNamespace() {
        var oldNamespace = namespace("old");
        var newNamespace = namespace("new");
        var extension = extension(oldNamespace);
        var extVersion = version(extension, false);
        stubRename(oldNamespace, newNamespace, extension);
        when(repositories.findVersions(extension)).thenReturn(Streamable.of(extVersion));
        when(repositories.wasReportedAsAvailable(extVersion)).thenReturn(true);

        service.changeNamespaceInDatabase(newNamespace, oldNamespace, List.of(), true, false);

        // the previously reported old tuple is withdrawn ...
        verify(repositories)
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.REMOVED), any());
        // ... but an inactive version is not publicly available under the new name either, so there is
        // nothing to announce until it changes state on its own
        verify(repositories, never())
                .recordExtensionVersionChange(eq(extVersion), eq(ExtensionVersionState.ACTIVE), any());
    }
}
