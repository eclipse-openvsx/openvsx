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
package org.eclipse.openvsx.repositories;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class ExtensionSizeOverrideRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    ExtensionSizeOverrideRepository overrides;

    @Test
    void findByScopeReturnsNamespaceWideRowWhenExtensionIdIsNull() {
        var namespace = persistNamespace("foo");
        persistOverride(namespace, null, 100L);

        var found = overrides.findByScope(namespace.getId(), null);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getMaxSize()).isEqualTo(100L);
    }

    @Test
    void findByScopeReturnsBothNamespaceWideAndExtensionScopedRows() {
        var namespace = persistNamespace("bar");
        var extension = persistExtension(namespace, "baz");
        persistOverride(namespace, null, 100L);
        persistOverride(namespace, extension, 200L);

        var found = overrides.findByScope(namespace.getId(), extension.getId());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ExtensionSizeOverride::getMaxSize).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void findByScopeExcludesAnotherExtensionsOverride() {
        var namespace = persistNamespace("qux");
        var wanted = persistExtension(namespace, "wanted");
        var other = persistExtension(namespace, "other");
        persistOverride(namespace, other, 200L);

        var found = overrides.findByScope(namespace.getId(), wanted.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findHighestMaxSizeReturnsNullWhenNoOverridesExist() {
        assertThat(overrides.findHighestMaxSize()).isNull();
    }

    @Test
    void findHighestMaxSizeReturnsTheLargestAcrossNamespaces() {
        var one = persistNamespace("one");
        var two = persistNamespace("two");
        persistOverride(one, null, 100L);
        persistOverride(two, null, 900L);

        assertThat(overrides.findHighestMaxSize()).isEqualTo(900L);
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        namespace.setPublicId(UUID.randomUUID().toString());
        entityManager.persist(namespace);
        return namespace;
    }

    private Extension persistExtension(Namespace namespace, String name) {
        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setPublicId(UUID.randomUUID().toString());
        entityManager.persist(extension);
        return extension;
    }

    private ExtensionSizeOverride persistOverride(Namespace namespace, @Nullable Extension extension, long maxSize) {
        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        override.setScopeExtension(extension);
        override.setMaxSize(maxSize);
        entityManager.persist(override);
        entityManager.flush();
        return override;
    }
}
