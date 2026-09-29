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

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.ExtensionId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOB-OVSX-37: {@code findFirstUnresolvedDependency} joined the extension table by name only, not
 * constrained to the namespace resolved by the preceding join. A dependency naming an existing
 * namespace that does not contain the named extension, where that extension name exists under a
 * different namespace, was therefore incorrectly reported as resolved.
 */
@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExtensionJooqRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    ExtensionJooqRepository repo;

    @Autowired
    EntityManager em;

    @Test
    void reportsDependencyAsUnresolvedWhenExtensionNameExistsOnlyInAnotherNamespace() {
        persistExtension("trustednamespace", "unrelated-extension");
        persistExtension("otherns", "popular-extension");

        var dependency = new ExtensionId("trustednamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isEqualTo("trustednamespace.popular-extension");
    }

    @Test
    void reportsDependencyAsResolvedWhenNamespaceAndExtensionMatch() {
        persistExtension("trustednamespace", "popular-extension");

        var dependency = new ExtensionId("trustednamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isNull();
    }

    @Test
    void reportsDependencyAsUnresolvedWhenNamespaceDoesNotExist() {
        var dependency = new ExtensionId("unknownnamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isEqualTo("unknownnamespace.popular-extension");
    }

    @Test
    void reportsDependencyAsUnresolvedWhenExtensionDoesNotExistInAnExistingNamespace() {
        persistExtension("trustednamespace", "unrelated-extension");

        var dependency = new ExtensionId("trustednamespace", "popular-extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isEqualTo("trustednamespace.popular-extension");
    }

    @Test
    void matchesNamespaceAndExtensionNameIgnoringCase() {
        persistExtension("trustednamespace", "popular-extension");

        var dependency = new ExtensionId("TrustedNamespace", "Popular-Extension");
        var unresolved = repo.findFirstUnresolvedDependency(List.of(dependency));

        assertThat(unresolved).isNull();
    }

    private void persistExtension(String namespaceName, String extensionName) {
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        em.persist(namespace);

        var extension = new Extension();
        extension.setName(extensionName);
        extension.setNamespace(namespace);
        extension.setActive(true);
        extension.setDeprecated(false);
        extension.setDownloadable(true);
        extension.setPublishedDate(LocalDateTime.now());
        extension.setLastUpdatedDate(LocalDateTime.now());
        em.persist(extension);

        // ExtensionJooqRepository queries run over the transaction's raw JDBC connection, bypassing
        // the persistence context, so pending inserts must be flushed before they become visible to it.
        em.flush();
    }
}
