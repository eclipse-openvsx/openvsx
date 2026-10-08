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
package org.eclipse.openvsx.security;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.eclipse.openvsx.entities.Permission;
import org.eclipse.openvsx.entities.UserData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The coarse gate in front of {@code /admin/**}. It is deliberately not the authorization decision -
 * each handler still checks the capability it needs - so these only cover "has any admin access".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAccessAuthorizationManagerTest {

    @Mock
    EntityManager entityManager;

    @Mock
    ObjectProvider<EntityManager> entityManagers;

    private AdminAccessAuthorizationManager manager() {
        when(entityManagers.getIfAvailable()).thenReturn(entityManager);
        return new AdminAccessAuthorizationManager(entityManagers);
    }

    private boolean authorize(Authentication authentication) {
        return manager().authorize(() -> authentication, null).isGranted();
    }

    @Test
    void grantsTheAdminRoleWithoutLookingTheUserUp() {
        var authentication = new TestingAuthenticationToken(
                new IdPrincipal(1L, "root", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(authorize(authentication)).isTrue();
        verify(entityManager, never()).find(any(), any());
    }

    @Test
    void grantsAUserHoldingAPermission() {
        var user = new UserData();
        user.getPermissions().add(Permission.MANAGE_EXTENSIONS);
        when(entityManager.find(UserData.class, 7L)).thenReturn(user);

        assertThat(authorize(authenticated(7L))).isTrue();
    }

    @Test
    void refusesAUserWithNoRoleAndNoPermissions() {
        when(entityManager.find(UserData.class, 7L)).thenReturn(new UserData());

        assertThat(authorize(authenticated(7L))).isFalse();
    }

    // PRIVILEGED only bypasses namespace verification; it is not admin access.
    @Test
    void refusesThePrivilegedRoleOnItsOwn() {
        var user = new UserData();
        user.setRole(UserData.Role.PRIVILEGED);
        when(entityManager.find(UserData.class, 7L)).thenReturn(user);

        assertThat(authorize(authenticated(7L))).isFalse();
    }

    @Test
    void refusesAnonymousAndUnauthenticatedCallers() {
        var anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(authorize(anonymous)).isFalse();
        assertThat(authorize(null)).isFalse();
    }

    private Authentication authenticated(long userId) {
        var principal = new IdPrincipal(userId, "someone", List.of());
        var authentication = new TestingAuthenticationToken(principal, null, List.of());
        authentication.setAuthenticated(true);
        return authentication;
    }
}
