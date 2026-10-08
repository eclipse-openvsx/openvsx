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

import jakarta.persistence.EntityManager;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.TimeUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A scoped token must be removed with the extension or namespace it is scoped to: were the scope
 * detached instead, the token would read as unrestricted. The foreign keys do this, so it takes a
 * real database to see it.
 */
@SpringBootTest
class ScopedTokenCascadeTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SearchUtilService search;

    @MockitoBean
    JobRequestScheduler scheduler;

    // The database is shared with every other container-backed test, and an active extension with no
    // version breaks the gallery queries of those that run after this one.
    @AfterEach
    void cleanUp() {
        inTransaction(() -> {
            em.createQuery("delete from PersonalAccessToken t where t.user.loginName like 'cascade-%'")
                    .executeUpdate();
            em.createQuery("delete from Extension e where e.name like 'cascade-%'").executeUpdate();
            em.createQuery("delete from Namespace n where n.name like 'cascade-%'").executeUpdate();
            em.createQuery("delete from UserData u where u.loginName like 'cascade-%'").executeUpdate();
            return null;
        });
    }

    @Test
    void removingAnExtensionRemovesItsScopedTokensOnly() {
        var ids = inTransaction(() -> {
            var user = persistUser("cascade-ext-user");
            var namespace = persistNamespace("cascade-ext-ns");
            var scoped = persistExtension("cascade-ext", namespace);
            var other = persistExtension("cascade-ext-other", namespace);
            return new long[] {
                persistToken(user, "ext", null, scoped),
                persistToken(user, "other-ext", null, other),
                persistToken(user, "ns", namespace, null),
                persistToken(user, "unrestricted", null, null),
                scoped.getId()
            };
        });

        inTransaction(() -> {
            em.remove(em.find(Extension.class, ids[4]));
            return null;
        });

        assertThat(tokenExists(ids[0])).isFalse();
        assertThat(tokenExists(ids[1])).isTrue();
        assertThat(tokenExists(ids[2])).isTrue();
        assertThat(tokenExists(ids[3])).isTrue();
    }

    @Test
    void removingANamespaceRemovesItsScopedTokensOnly() {
        var ids = inTransaction(() -> {
            var user = persistUser("cascade-ns-user");
            var scoped = persistNamespace("cascade-ns");
            var other = persistNamespace("cascade-ns-other");
            return new long[] {
                persistToken(user, "ns", scoped, null),
                persistToken(user, "other-ns", other, null),
                persistToken(user, "unrestricted", null, null),
                scoped.getId()
            };
        });

        inTransaction(() -> {
            em.remove(em.find(Namespace.class, ids[3]));
            return null;
        });

        assertThat(tokenExists(ids[0])).isFalse();
        assertThat(tokenExists(ids[1])).isTrue();
        assertThat(tokenExists(ids[2])).isTrue();
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(txManager).execute(status -> {
            var result = work.get();
            em.flush();
            return result;
        });
    }

    private boolean tokenExists(long id) {
        return inTransaction(() -> em.find(PersonalAccessToken.class, id) != null);
    }

    private UserData persistUser(String name) {
        var user = new UserData();
        user.setLoginName(name);
        user.setProvider("github");
        em.persist(user);
        return user;
    }

    private Namespace persistNamespace(String name) {
        var namespace = new Namespace();
        namespace.setName(name);
        em.persist(namespace);
        return namespace;
    }

    private Extension persistExtension(String name, Namespace namespace) {
        var extension = new Extension();
        extension.setName(name);
        extension.setNamespace(namespace);
        extension.setActive(true);
        em.persist(extension);
        return extension;
    }

    private long persistToken(UserData user, String description, Namespace namespace, Extension extension) {
        var token = new PersonalAccessToken();
        token.setUser(user);
        token.setValue(description + "-" + user.getLoginName() + "-value");
        token.setActive(true);
        token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
        token.setVersion(1);
        token.setType(PersonalAccessTokenType.LLT);
        token.setDescription(description);
        token.setScopeNamespace(namespace);
        token.setScopeExtension(extension);
        em.persist(token);
        em.flush();
        return token.getId();
    }
}
