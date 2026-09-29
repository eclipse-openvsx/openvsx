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
package org.eclipse.openvsx.accesstoken;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Token rows against a real database: what a write actually sends, and what expiry actually leaves behind.
 * Neither is visible without one - the first is decided by Hibernate's generated UPDATE, the second by a
 * native query.
 */
@SpringBootTest
class AccessTokenConcurrentWriteTest extends AbstractPostgresContainerTest {

    @Autowired
    AccessTokenService accessTokens;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    @Test
    void upgradingATokenDoesNotResurrectOneRevokedMeanwhile() {
        var tokenId = persistLegacyToken();

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            // what the upgrade job holds: a snapshot taken before the revoke below, so its copy still
            // says active = true
            var token = em.find(PersonalAccessToken.class, tokenId);
            assertThat(token.isActive()).isTrue();

            revokeInAnotherTransaction(tokenId);

            // and now it writes the two columns it actually cares about
            token.setValue("hashed-" + token.getValue());
            token.setVersion(1);
        });

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var token = em.find(PersonalAccessToken.class, tokenId);
            assertThat(token.getVersion()).isEqualTo(1);
            assertThat(token.getValue()).startsWith("hashed-");
            // the revoke has to survive: without @DynamicUpdate the upgrade's full-row UPDATE writes
            // active = true back over it, handing a revoked token back to its holder
            assertThat(token.isActive()).isFalse();
        });

        cleanUp(tokenId);
    }

    // A trusted publishing token is reusable within its lifetime but never past it, and nothing shows it
    // to its owner, so there is no expired row for anyone to read - unlike a long-lived token, whose
    // expired rows a user is shown and the notification mails read. One is minted per exchange, so keeping
    // them would retain exactly the rows nobody has a question about.
    @Test
    void expiryDeletesAnEphemeralTokenAndOnlyDeactivatesALongLivedOne() {
        var expired = TimeUtil.getCurrentUTC().minusMinutes(1);
        var ephemeral = persistToken("expiry-tpt", PersonalAccessTokenType.TPT, 1, expired);
        var longLived = persistToken("expiry-llt", PersonalAccessTokenType.LLT, 1, expired);

        new TransactionTemplate(txManager).executeWithoutResult(status -> accessTokens.expireAccessTokens());

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            assertThat(em.find(PersonalAccessToken.class, ephemeral)).isNull();
            var kept = em.find(PersonalAccessToken.class, longLived);
            // a user is shown their own expired tokens, and the notification mails read them
            assertThat(kept).isNotNull();
            assertThat(kept.isActive()).isFalse();
        });

        cleanUp(longLived);
        // the ephemeral token's own row is gone, but its user is not
        cleanUpUser("expiry-tpt-user");
    }

    // Best-effort provenance: it answers "what did this credential publish" after a leak, and must give
    // way rather than stand in the way when the token itself is deleted.
    @Test
    void aVersionRemembersTheTokenItWasPublishedWithUntilThatTokenIsDeleted() {
        var tokenId = persistToken("provenance", PersonalAccessTokenType.LLT, 1, null);
        var versionId = persistVersionPublishedWith(tokenId);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            assertThat(em.find(ExtensionVersion.class, versionId).getPublishedWithId()).isEqualTo(tokenId);
        });

        new TransactionTemplate(txManager)
                .executeWithoutResult(status -> em.remove(em.find(PersonalAccessToken.class, tokenId)));

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var version = em.find(ExtensionVersion.class, versionId);
            // the reference decays, the authorship does not
            assertThat(version.getPublishedWithId()).isNull();
            assertThat(version.getPublishedBy()).isNotNull();
            assertThat(version.getPublishedWithTt()).isEqualTo(PersonalAccessTokenType.LLT);
        });

        cleanUpVersion(versionId);
        cleanUpUser("provenance-user");
    }

    // The link from a published artifact back to the workflow run is most of what trusted publishing buys
    // over a token pasted into CI, and the token that carried it is deleted as soon as it expires - which
    // is why the claims are copied onto the version rather than reached through the token.
    @Test
    void aVersionKeepsItsTrustedPublishingProvenanceAfterTheTokenIsGone() {
        var claims = Map.of(
                "repository_id",
                "74",
                "repository_owner_id",
                "65",
                "workflow_ref",
                "octo-org/octo-repo/.github/workflows/publish.yml@refs/tags/v1.2.0");
        var tokenId = persistToken("provenance-tpt", PersonalAccessTokenType.TPT, 1, null);
        new TransactionTemplate(txManager)
                .executeWithoutResult(status -> em.find(PersonalAccessToken.class, tokenId).setClaims(claims));
        var versionId = persistVersionPublishedWith(tokenId);

        // using a one-time token deletes it, so the copy is the only thing left
        new TransactionTemplate(txManager)
                .executeWithoutResult(status -> em.remove(em.find(PersonalAccessToken.class, tokenId)));

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var version = em.find(ExtensionVersion.class, versionId);
            assertThat(version.getPublishedProvenance()).isEqualTo(claims);
            assertThat(version.getPublishedWithId()).isNull();
        });

        cleanUpVersion(versionId);
        cleanUpUser("provenance-tpt-user");
    }

    private long persistVersionPublishedWith(long tokenId) {
        return new TransactionTemplate(txManager).execute(status -> {
            var token = em.find(PersonalAccessToken.class, tokenId);

            var namespace = new Namespace();
            namespace.setName("provenance-ns-" + tokenId);
            em.persist(namespace);

            var extension = new Extension();
            extension.setName("provenance-ext-" + tokenId);
            extension.setNamespace(namespace);
            extension.setActive(true);
            em.persist(extension);

            var version = new ExtensionVersion();
            version.setExtension(extension);
            version.setVersion("1.0.0");
            version.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            version.setTimestamp(TimeUtil.getCurrentUTC());
            version.setPublishedBy(token.getUser());
            version.setPublishedWithTt(token.getType());
            version.setPublishedWithId(token.getId());
            version.setPublishedProvenance(token.getClaims());
            em.persist(version);
            em.flush();
            return version.getId();
        });
    }

    private void cleanUpVersion(long versionId) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var version = em.find(ExtensionVersion.class, versionId);
            var extension = version.getExtension();
            var namespaceId = extension.getNamespace().getId();
            em.remove(version);
            em.flush();
            em.createQuery("delete from Extension e where e.id = :id").setParameter("id", extension.getId())
                    .executeUpdate();
            em.createQuery("delete from Namespace n where n.id = :id").setParameter("id", namespaceId)
                    .executeUpdate();
        });
    }

    private long persistToken(String name, PersonalAccessTokenType type, int version, LocalDateTime expires) {
        return new TransactionTemplate(txManager).execute(status -> {
            var user = new UserData();
            user.setLoginName(name + "-user");
            user.setProvider("github");
            em.persist(user);

            var token = new PersonalAccessToken();
            token.setUser(user);
            token.setValue(name + "-token-value");
            token.setActive(true);
            token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
            token.setExpiresTimestamp(expires);
            token.setVersion(version);
            token.setType(type);
            token.setDescription(name);
            em.persist(token);
            em.flush();
            return token.getId();
        });
    }

    private long persistLegacyToken() {
        return new TransactionTemplate(txManager).execute(status -> {
            var user = new UserData();
            user.setLoginName("concurrent-write-user");
            user.setProvider("github");
            em.persist(user);

            var token = new PersonalAccessToken();
            token.setUser(user);
            token.setValue("concurrent-write-token-value");
            token.setActive(true);
            token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
            token.setVersion(0);
            token.setType(PersonalAccessTokenType.LLT);
            token.setDescription("legacy token");
            em.persist(token);
            em.flush();
            return token.getId();
        });
    }

    private void revokeInAnotherTransaction(long tokenId) {
        var requiresNew = new TransactionTemplate(txManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(
                status -> em.createNativeQuery("UPDATE personal_access_token SET active = false WHERE id = :id")
                        .setParameter("id", tokenId)
                        .executeUpdate());
    }

    private void cleanUpUser(String loginName) {
        new TransactionTemplate(txManager).executeWithoutResult(
                status -> em.createQuery("delete from UserData u where u.loginName = :name")
                        .setParameter("name", loginName)
                        .executeUpdate());
    }

    /** The container is shared with every other test, so leave nothing of this one behind. */
    private void cleanUp(long tokenId) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var token = em.find(PersonalAccessToken.class, tokenId);
            var userId = token.getUser().getId();
            em.remove(token);
            em.flush();
            em.createQuery("delete from UserData u where u.id = :id").setParameter("id", userId).executeUpdate();
        });
    }
}
