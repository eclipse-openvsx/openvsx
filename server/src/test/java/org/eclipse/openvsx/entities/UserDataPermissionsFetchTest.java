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
package org.eclipse.openvsx.entities;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.AbstractPostgresContainerTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for permissions being unreadable right after login: every real deployment config
 * sets {@code spring.jpa.open-in-view: false} (not set in the test profile, so it is forced here to
 * match), and {@code UserService#findLoggedInUser} loads the user with a bare
 * {@code entityManager.find(...)} outside any {@code @Transactional} method - exactly like the setup
 * below. A LAZY collection has no session left to initialize from by the time a caller (e.g.
 * {@code UserAPI#getUserData}) reads it, unlike a plain column such as {@code role}, which is already
 * materialized by the {@code find} call itself.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class UserDataPermissionsFetchTest extends AbstractPostgresContainerTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void permissionsAreReadableAfterANonTransactionalFindLikeFindLoggedInUserDoes() {
        var user = new UserData();
        user.setLoginName("permissions-fetch-" + UUID.randomUUID());
        user.getPermissions().add(Permission.MANAGE_EXTENSIONS);

        // Persisted and committed in its own transaction, so by the time the find() below runs,
        // no session from this setup is still open to lean on. The database is shared JVM-wide, so
        // the row has to be cleaned up rather than rolled back with the test.
        var transactions = new TransactionTemplate(txManager);
        transactions.executeWithoutResult(status -> entityManager.persist(user));
        try {
            // Mirrors UserService#findLoggedInUser exactly: no surrounding @Transactional.
            var reloaded = entityManager.find(UserData.class, user.getId());

            assertThat(reloaded.getPermissionsAsStrings()).containsExactly("manage_extensions");
        } finally {
            transactions.executeWithoutResult(
                    status -> entityManager.remove(entityManager.find(UserData.class, user.getId())));
        }
    }
}
