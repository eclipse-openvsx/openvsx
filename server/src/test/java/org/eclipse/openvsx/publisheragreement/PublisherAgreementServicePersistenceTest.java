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
package org.eclipse.openvsx.publisheragreement;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.PublisherAgreementEntryJson;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service and the default provider against the real table. Their unit tests mock the repository, which
 * cannot see what the event table and its queries actually do.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class PublisherAgreementServicePersistenceTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    RepositoryService repositories;

    @Autowired
    ExtensionService extensions;

    @Autowired
    PlatformTransactionManager transactionManager;

    private PublisherAgreementService service(String version) {
        return new PublisherAgreementService(
                new SimplePublisherAgreementProvider(repositories, version),
                repositories,
                extensions,
                new TransactionTemplate(transactionManager),
                new PublisherAgreementVersions(version, List.of()));
    }

    private UserData persistUser(String name) {
        var user = new UserData();
        user.setLoginName(name + "-" + UUID.randomUUID());
        user.setProvider("github");
        entityManager.persist(user);
        return user;
    }

    /** The earlier signing stays in the history next to the new one. */
    @Test
    void signingANewMajorVersionKeepsTheOutdatedSigningInTheHistory() {
        var user = persistUser("signer");
        assertThat(service("1.0.0").sign(user, new UserJson())).isTrue();
        entityManager.flush();

        var later = service("2.0.0");
        assertThat(later.mayPublish(user)).isFalse();
        assertThat(later.sign(user, new UserJson())).isTrue();
        entityManager.flush();

        assertThat(later.mayPublish(user)).isTrue();
        assertThat(later.history(user))
                .extracting(PublisherAgreementEntryJson::version)
                .containsExactly("2.0.0", "1.0.0");
    }

    @Test
    void signingTwiceRecordsOnceAndChangesNothingTheSecondTime() {
        var user = persistUser("double-click");
        var service = service("1");

        assertThat(service.sign(user, new UserJson())).isTrue();
        entityManager.flush();
        assertThat(service.sign(user, new UserJson())).isFalse();
        entityManager.flush();

        assertThat(service.history(user)).hasSize(1);
    }

    @Test
    void aRevocationBlocksUntilAnAdminReinstatesAndTheUserSignsAgain() {
        var user = persistUser("revoked");
        var admin = persistUser("admin");
        var service = service("2.0.0");
        service.sign(user, new UserJson());
        entityManager.flush();

        service.revoke(user, admin);
        entityManager.flush();
        assertThat(service.mayPublish(user)).isFalse();
        var blocked = new UserJson();
        service.enrichUserJson(blocked, user);
        assertThat(blocked.getPublisherAgreement().getStatus()).isEqualTo("revoked");

        service.reinstate(user, admin);
        entityManager.flush();
        assertThat(service.mayPublish(user)).isFalse();
        assertThat(service.sign(user, new UserJson())).isTrue();
        entityManager.flush();

        assertThat(service.mayPublish(user)).isTrue();
        assertThat(service.history(user))
                .extracting(PublisherAgreementEntryJson::type)
                .containsExactly("signed", "reinstated", "revoked", "signed");
        assertThat(service.history(user))
                .extracting(PublisherAgreementEntryJson::actorId)
                .containsExactly(user.getId(), admin.getId(), admin.getId(), user.getId());
    }

    @Test
    void aUserWhoNeverSignedCanBeBlockedToo() {
        var user = persistUser("never-signed");
        var admin = persistUser("admin");
        var service = service("1");

        service.revoke(user, admin);
        entityManager.flush();

        assertThat(service.mayPublish(user)).isFalse();
        assertThat(service.history(user)).extracting(PublisherAgreementEntryJson::type).containsExactly("revoked");
    }
}
