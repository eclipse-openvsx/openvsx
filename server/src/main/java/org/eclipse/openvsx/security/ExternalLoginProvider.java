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

import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;

/**
 * Extension point for an OAuth2 registration that links an account to the already logged-in user
 * rather than creating one. Registrations without a provider are handled as generic logins.
 */
public interface ExternalLoginProvider {

    String registrationId();

    IdPrincipal loadUser(OAuth2UserRequest userRequest);

    /**
     * Called once the login completed; the principal is the one returned by {@link #loadUser}.
     */
    void authenticationSucceeded(IdPrincipal principal, OAuth2LoginAuthenticationToken authentication);
}
