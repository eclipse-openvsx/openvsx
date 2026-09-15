/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Notes the page a login started from, see {@link LoginReturnTo}. The Web UI links straight to the
 * authorization endpoint, so the request resolved here is the only one that can carry it.
 */
public class ReturnToAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final LoginReturnTo returnTo;

    public ReturnToAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrations,
            LoginReturnTo returnTo
    ) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrations,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.returnTo = returnTo;
    }

    @Override
    @Nullable
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return capture(request, delegate.resolve(request));
    }

    @Override
    @Nullable
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return capture(request, delegate.resolve(request, clientRegistrationId));
    }

    @Nullable
    private OAuth2AuthorizationRequest capture(
            HttpServletRequest request,
            @Nullable OAuth2AuthorizationRequest authorizationRequest
    ) {
        if (authorizationRequest != null) {
            returnTo.capture(request);
        }
        return authorizationRequest;
    }
}
