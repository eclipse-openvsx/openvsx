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
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.entities.UserData;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JooqOnlyTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class UserDataJooqRepositoryTest extends AbstractPostgresContainerTest {

    @Autowired
    UserDataJooqRepository repo;

    @Autowired
    EntityManager em;

    @Test
    void anyMatchesGrantedPermissionsButNotAdminRoleOrNoPermissions() {
        var prefix = UUID.randomUUID().toString();
        persistUser(prefix + "-plain", null);
        persistUser(prefix + "-admin", UserData.Role.ADMIN);
        persistUser(prefix + "-admin-granted", UserData.Role.ADMIN, Permission.MANAGE_SCANS);
        persistUser(prefix + "-privileged", UserData.Role.PRIVILEGED, Permission.MANAGE_SCANS);
        persistUser(prefix + "-scans", null, Permission.MANAGE_SCANS);
        persistUser(prefix + "-both", null, Permission.MANAGE_SCANS, Permission.MANAGE_CACHES);
        em.flush();

        var found = repo.findUsers(prefix, null, "any", Pageable.ofSize(10));

        assertThat(found.getContent()).extracting(UserData::getLoginName)
                .containsExactly(prefix + "-both", prefix + "-privileged", prefix + "-scans");
        assertThat(found.getTotalElements()).isEqualTo(3);
    }

    @Test
    void specificPermissionMatchesOnlyUsersHoldingIt() {
        var prefix = UUID.randomUUID().toString();
        persistUser(prefix + "-scans", null, Permission.MANAGE_SCANS);
        persistUser(prefix + "-both", null, Permission.MANAGE_SCANS, Permission.MANAGE_CACHES);
        em.flush();

        var found = repo.findUsers(prefix, null, "manage_caches", Pageable.ofSize(10));

        assertThat(found.getContent()).extracting(UserData::getLoginName).containsExactly(prefix + "-both");
    }

    private void persistUser(String loginName, UserData.Role role, Permission... permissions) {
        var user = new UserData();
        user.setLoginName(loginName);
        user.setProvider("github");
        user.setRole(role);
        user.getPermissions().addAll(java.util.List.of(permissions));
        em.persist(user);
    }
}
