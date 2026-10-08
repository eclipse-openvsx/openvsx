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
package org.eclipse.openvsx.migration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanNamespaceMigrationTest {

    @Mock
    EntityManager entityManager;
    @Mock
    RepositoryService repositories;
    @Mock
    ExtensionSizeLimitService sizeLimits;

    private OrphanNamespaceMigration migration() {
        return new OrphanNamespaceMigration(entityManager, repositories, sizeLimits);
    }

    // Deleting the namespace cascades its size override away, so a ceiling derived from that
    // override would otherwise stay cached above every limit still configured.
    @Test
    void dropsTheCachedCeilingAfterDeletingAnEmptyOrphanNamespace() {
        var namespace = new Namespace();
        when(repositories.findOrphanNamespaces()).thenReturn(Streamable.of(namespace));
        when(repositories.findExtensions(namespace)).thenReturn(Streamable.empty());

        migration().fixOrphanNamespaces();

        verify(entityManager).remove(namespace);
        verify(sizeLimits).invalidateCeiling();
    }

    @Test
    void leavesTheCachedCeilingAloneWhenNoNamespaceIsDeleted() {
        var namespace = new Namespace();
        var extension = new Extension();
        when(repositories.findOrphanNamespaces()).thenReturn(Streamable.of(namespace));
        when(repositories.findExtensions(namespace)).thenReturn(Streamable.of(extension));
        when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(new ExtensionVersion()));

        migration().fixOrphanNamespaces();

        verify(sizeLimits, Mockito.never()).invalidateCeiling();
    }
}
