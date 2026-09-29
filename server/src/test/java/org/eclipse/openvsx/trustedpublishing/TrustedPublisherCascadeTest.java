/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.trustedpublishing;

import java.util.Map;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.TrustedPublisher;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A trusted publisher points at an extension, and the publishing token issued under it points at both, so
 * deleting either end has to take what depends on it along. Only the database can be asserted on: none of
 * these references has an inverse mapping on {@link Extension} or {@link TrustedPublisher}, so JPA cascades
 * nothing and the foreign key constraints alone decide what happens.
 */
@SpringBootTest
class TrustedPublisherCascadeTest extends AbstractPostgresContainerTest {

    @Autowired
    ExtensionService extensions;

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    @Test
    void purgingAnExtensionTakesItsTrustedPublisherAndTokensWithIt() {
        var ids = persistRegistrationWithToken("purge");

        // without the constraints deleting the registration and the token, this fails outright on a foreign
        // key and the extension can never be purged at all
        assertThatCode(() -> transaction(status -> {
            var user = em.find(UserData.class, ids.userId());
            var extension = em.find(Extension.class, ids.extensionId());
            extensions.purgeExtension(user, extension, false);
        })).doesNotThrowAnyException();

        transaction(status -> {
            assertThat(em.find(Extension.class, ids.extensionId())).isNull();
            assertThat(em.find(TrustedPublisher.class, ids.trustedPublisherId())).isNull();
            // retiring the token rather than detaching it: one left behind with neither scope set would
            // read as unrestricted
            assertThat(em.find(PersonalAccessToken.class, ids.tokenId())).isNull();
        });

        cleanUp(ids, "purge");
    }

    @Test
    void deletingATrustedPublisherTakesTheTokensIssuedUnderItWithIt() {
        var ids = persistRegistrationWithToken("revoke");

        transaction(
                status -> repositories
                        .deleteTrustedPublisher(em.find(TrustedPublisher.class, ids.trustedPublisherId())));

        transaction(status -> {
            assertThat(em.find(TrustedPublisher.class, ids.trustedPublisherId())).isNull();
            // a token issued under a registration may only publish the extension it was made for, so it
            // has nothing left to authorize once the registration is gone
            assertThat(em.find(PersonalAccessToken.class, ids.tokenId())).isNull();
            // the extension itself is untouched by this
            assertThat(em.find(Extension.class, ids.extensionId())).isNotNull();
        });

        cleanUp(ids, "revoke");
    }

    private record Ids(long userId, long namespaceId, long extensionId, long trustedPublisherId, long tokenId) {}

    /**
     * A registration that has actually been used: the exchange leaves a token scoped to the same extension
     * behind, and expiry only deactivates that row, so it outlives the exchange it was issued for.
     */
    private Ids persistRegistrationWithToken(String prefix) {
        return new TransactionTemplate(txManager).execute(status -> {
            var user = new UserData();
            user.setLoginName(prefix + "-tp-owner");
            user.setProvider("github");
            em.persist(user);

            var namespace = new Namespace();
            namespace.setName(prefix + "-tp-testns");
            em.persist(namespace);

            var extension = new Extension();
            extension.setName(prefix + "-tp-testext");
            extension.setNamespace(namespace);
            extension.setActive(true);
            em.persist(extension);

            var trustedPublisher = new TrustedPublisher();
            trustedPublisher.setExtension(extension);
            trustedPublisher.setProvider("github");
            trustedPublisher.setRegistration(Map.of("owner", "octo-org", "repo", "octo-repo"));
            trustedPublisher.setClaims(Map.of("repository_id", "74"));
            trustedPublisher.setCreatedBy(user);
            trustedPublisher.setCreatedTimestamp(TimeUtil.getCurrentUTC());
            em.persist(trustedPublisher);

            var token = new PersonalAccessToken();
            token.setUser(user);
            token.setValue(prefix + "-tp-token-value");
            token.setActive(false);
            token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
            token.setVersion(1);
            token.setType(PersonalAccessTokenType.TPT);
            token.setDescription("Trusted publishing (github)");
            token.setTrustedPublisher(trustedPublisher);
            token.setScopeExtension(extension);
            em.persist(token);
            em.flush();

            return new Ids(
                    user.getId(),
                    namespace.getId(),
                    extension.getId(),
                    trustedPublisher.getId(),
                    token.getId());
        });
    }

    private void transaction(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> work) {
        new TransactionTemplate(txManager).executeWithoutResult(work::accept);
    }

    /** The container is shared with every other test, so leave nothing of this one behind. */
    private void cleanUp(Ids ids, String prefix) {
        transaction(status -> {
            em.createQuery("delete from PersonalAccessToken t where t.user.id = :id")
                    .setParameter("id", ids.userId())
                    .executeUpdate();
            em.createQuery("delete from TrustedPublisher p where p.createdBy.id = :id")
                    .setParameter("id", ids.userId())
                    .executeUpdate();
            em.createQuery("delete from Extension e where e.namespace.id = :id")
                    .setParameter("id", ids.namespaceId())
                    .executeUpdate();
            em.createQuery("delete from PersistedLog l where l.user.id = :id")
                    .setParameter("id", ids.userId())
                    .executeUpdate();
            em.createQuery("delete from Namespace n where n.id = :id")
                    .setParameter("id", ids.namespaceId())
                    .executeUpdate();
            em.createQuery("delete from UserData u where u.id = :id")
                    .setParameter("id", ids.userId())
                    .executeUpdate();
        });
    }
}
