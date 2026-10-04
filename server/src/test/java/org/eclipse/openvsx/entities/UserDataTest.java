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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDataTest {

    @Test
    void hasPermissionIsFalseByDefault() {
        var user = new UserData();
        assertThat(user.hasPermission(Permission.MANAGE_EXTENSIONS)).isFalse();
    }

    @Test
    void hasPermissionIsTrueOnceGranted() {
        var user = new UserData();
        user.getPermissions().add(Permission.MANAGE_EXTENSIONS);

        assertThat(user.hasPermission(Permission.MANAGE_EXTENSIONS)).isTrue();
        assertThat(user.hasPermission(Permission.MANAGE_NAMESPACES)).isFalse();
    }

    // ADMIN implies every permission without it being reflected in the granted set, including
    // permissions added after the role was granted.
    @Test
    void adminRoleImpliesEveryPermissionWithoutBeingGrantedIndividually() {
        var user = new UserData();
        user.setRole(UserData.Role.ADMIN);

        for (var permission : Permission.values()) {
            assertThat(user.hasPermission(permission)).isTrue();
        }
        assertThat(user.getPermissions()).isEmpty();
    }

    @Test
    void privilegedRoleAloneDoesNotImplyAnyPermission() {
        var user = new UserData();
        user.setRole(UserData.Role.PRIVILEGED);

        assertThat(user.hasPermission(Permission.MANAGE_EXTENSIONS)).isFalse();
    }
}
