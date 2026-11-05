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

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.NameSimilarityAllow;
import org.eclipse.openvsx.entities.NameSimilarityField;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class NameSimilarityAllowRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager em;

    @Autowired
    NameSimilarityAllowRepository allows;

    @Test
    void findsThePairWhicheverWayRoundItIsAsked() {
        var lookalike = persistExtension();
        var original = persistExtension();
        var allow = allows.save(allow(lookalike, original, null));

        assertThat(allows.findPair(lookalike.getId(), original.getId())).contains(allow);
        assertThat(allows.findPair(original.getId(), lookalike.getId())).contains(allow);
        assertThat(allows.findPair(lookalike.getId(), persistExtension().getId())).isEmpty();
    }

    @Test
    void storesAPairOnlyOnceWhicheverWayRoundItIsAllowed() {
        var lookalike = persistExtension();
        var original = persistExtension();
        allows.save(allow(lookalike, original, null));
        em.flush();

        allows.save(allow(original, lookalike, null));
        assertThatThrownBy(em::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void findsAllowsInvolvingAnExtensionOnEitherSide() {
        var subject = persistExtension();
        var asLookalike = allows.save(allow(subject, persistExtension(), null));
        var asOriginal = allows.save(allow(persistExtension(), subject, null));
        allows.save(allow(persistExtension(), persistExtension(), null));

        assertThat(allows.findInvolving(List.of(subject.getId()))).containsExactlyInAnyOrder(asLookalike, asOriginal);
    }

    @Test
    void goesAwayWithEitherExtension() {
        var lookalike = persistExtension();
        var original = persistExtension();
        var allow = allows.save(allow(lookalike, original, null));
        em.flush();

        em.createNativeQuery("DELETE FROM extension WHERE id = :id").setParameter("id", original.getId())
                .executeUpdate();
        em.clear();

        assertThat(allows.findById(allow.getId())).isEmpty();
    }

    @Test
    void outlivesTheAdminWhoAllowedIt() {
        var admin = new UserData();
        admin.setLoginName("name-similarity-admin-" + UUID.randomUUID());
        admin.setProvider("github");
        em.persist(admin);
        var allow = allows.save(allow(persistExtension(), persistExtension(), admin.getId()));
        em.flush();

        em.createNativeQuery("DELETE FROM user_data WHERE id = :id").setParameter("id", admin.getId()).executeUpdate();
        em.clear();

        assertThat(allows.findById(allow.getId()))
                .hasValueSatisfying(kept -> assertThat(kept.getDecidedById()).isNull());
    }

    private static NameSimilarityAllow allow(Extension lookalike, Extension original, @Nullable Long decidedById) {
        var allow = new NameSimilarityAllow();
        allow.setExtensionId(lookalike.getId());
        allow.setOtherExtensionId(original.getId());
        allow.setField(NameSimilarityField.DISPLAY_NAME);
        allow.setSimilarity(0.8f);
        allow.setExtensionValue("Python Tools");
        allow.setOtherValue("Python");
        allow.setReason("Official fork");
        allow.setDecidedById(decidedById);
        allow.setDecidedAt(TimeUtil.getCurrentUTC());
        return allow;
    }

    /** Unique per call: every context shares one database. */
    private Extension persistExtension() {
        var namespace = new Namespace();
        namespace.setName("allow-ns-" + UUID.randomUUID());
        namespace.setPublicId(UUID.randomUUID().toString());
        em.persist(namespace);

        var extension = new Extension();
        extension.setName("allow-ext");
        extension.setNamespace(namespace);
        extension.setPublicId(UUID.randomUUID().toString());
        em.persist(extension);
        return extension;
    }
}
