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

import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import org.eclipse.openvsx.entities.UserData;

/**
 * The coarse gate in front of {@code /admin/**}: the caller must hold the admin role or at least one
 * granted permission. Which capability a given endpoint needs is still decided per-handler by
 * {@code AdminService#checkPermission}, so this only keeps users with no admin access at all from
 * reaching admin handlers - including any future one that forgets to check for itself.
 * <p>
 * Deliberately reads the user per request rather than going by the authorities derived at login
 * ({@link OAuth2UserServices#getAuthorities}): a permission granted to someone already logged in
 * takes effect immediately, matching the per-request check the handlers themselves do.
 */
public class AdminAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    // An ObjectProvider rather than the EntityManager itself: several @WebMvcTest slices stand this
    // configuration up without a persistence context at all (they disable the filters anyway), and an
    // authorization component failing closed there is better than their contexts failing to start.
    private final ObjectProvider<EntityManager> entityManagers;

    public AdminAccessAuthorizationManager(ObjectProvider<EntityManager> entityManagers) {
        this.entityManagers = entityManagers;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context
    ) {
        return new AuthorizationDecision(hasAnyAdminAccess(authentication.get()));
    }

    private boolean hasAnyAdminAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        // The role is already in the authorities, so an admin needs no lookup at all. A revoked role
        // lingers here until the session ends, which only matters for this coarse gate: the handler's
        // own check reads the user fresh and still refuses.
        if (authentication.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()))) {
            return true;
        }
        // Granted permissions are not in the authorities - they are looked up per request, so granting
        // one takes effect without the user logging in again. An anonymous token reports itself as
        // authenticated, so the principal type is what distinguishes a logged-in user here.
        if (!(authentication.getPrincipal() instanceof IdPrincipal principal)) {
            return false;
        }

        var entityManager = entityManagers.getIfAvailable();
        if (entityManager == null) {
            return false;
        }

        var user = entityManager.find(UserData.class, principal.getId());
        return user != null && (UserData.Role.ADMIN.equals(user.getRole()) || !user.getPermissions().isEmpty());
    }
}
