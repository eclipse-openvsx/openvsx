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
package org.eclipse.openvsx.settings;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.ExtensionSizeOverride;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.ExtensionSizeOverrideRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the service's write methods actually leave in the database.
 * <p>
 * The service is tested elsewhere against a mocked repository, which cannot see any of this: with the
 * repository mocked, an entity is never managed, so a write that relies on dirty checking and one
 * that relies on a {@code save} call are indistinguishable. These need a real persistence context.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class ExtensionSizeLimitServicePersistenceTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    ExtensionSizeOverrideRepository overrides;

    @Autowired
    ExtensionSizeLimitService limits;

    /**
     * The point of giving the write a transaction: the row is loaded managed, so the setter is what
     * persists it and no detached copy is ever merged back over the stored row.
     */
    @Test
    void updateWritesTheNewSizeWithoutSavingADetachedCopy() {
        var override = persistOverride(persistNamespace("foo"), 100L);

        var updated = limits.updateOverride(override.getId(), 4096L);

        assertThat(updated.previousMaxSize()).isEqualTo(100L);
        // round-trip through the database rather than reading the instance back
        entityManager.flush();
        entityManager.clear();
        assertThat(overrides.findById(override.getId()))
                .get()
                .extracting(ExtensionSizeOverride::getMaxSize)
                .isEqualTo(4096L);
    }

    /** The scope the override was created for is untouched by a change of size. */
    @Test
    void updateLeavesTheScopeAlone() {
        var namespace = persistNamespace("foo");
        var override = persistOverride(namespace, 100L);

        limits.updateOverride(override.getId(), 4096L);

        entityManager.flush();
        entityManager.clear();
        assertThat(overrides.findById(override.getId()))
                .get()
                .extracting(stored -> stored.getScopeNamespace().getId())
                .isEqualTo(namespace.getId());
    }

    @Test
    void deleteRemovesTheRow() {
        var override = persistOverride(persistNamespace("foo"), 100L);

        limits.deleteOverride(override.getId());

        entityManager.flush();
        entityManager.clear();
        assertThat(overrides.findById(override.getId())).isEmpty();
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name + '-' + UUID.randomUUID());
        namespace.setPublicId(UUID.randomUUID().toString());
        entityManager.persist(namespace);
        return namespace;
    }

    private ExtensionSizeOverride persistOverride(Namespace namespace, long maxSize) {
        var override = new ExtensionSizeOverride();
        override.setScopeNamespace(namespace);
        override.setMaxSize(maxSize);
        entityManager.persist(override);
        entityManager.flush();
        return override;
    }
}
