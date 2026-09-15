/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Strings;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import org.eclipse.openvsx.util.UrlUtil;

public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public CustomAuthenticationSuccessHandler(String defaultTargetUrl) {
        setDefaultTargetUrl(defaultTargetUrl);
    }

    @Override
    protected String determineTargetUrl(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        // taken even where it loses to the eclipse redirect below, so it cannot outlive this login
        var returnTo = LoginReturnTo.take(request);
        if (authentication instanceof OAuth2AuthenticationToken) {
            var token = (OAuth2AuthenticationToken) authentication;
            // Redirect to user profile page after login to Eclipse
            if ("eclipse".equals(token.getAuthorizedClientRegistrationId())) {
                return UrlUtil.createApiUrl(getDefaultTargetUrl(), "user-settings", "profile");
            }
        }
        if (returnTo != null) {
            return Strings.CS.removeEnd(getDefaultTargetUrl(), "/") + returnTo;
        }
        return determineTargetUrl(request, response);
    }

}
