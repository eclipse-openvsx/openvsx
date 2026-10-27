/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CustomAuthenticationSuccessHandlerTest {

    private static final String WEBUI_URL = "https://open-vsx.org";

    private final LoginReturnTo returnTo = new LoginReturnTo(new String[] { "/extension/**", "/user-settings/**" });

    @Test
    void returnsToThePageTheLoginStartedFrom() {
        var request = requestReturningTo("/extension/foo/bar/reviews");

        assertThat(targetUrl(WEBUI_URL, request, login("github")))
                .isEqualTo("https://open-vsx.org/extension/foo/bar/reviews");
    }

    @Test
    void joinsTheTargetToAWebUiUrlThatEndsInASlash() {
        var request = requestReturningTo("/extension/foo/bar");

        assertThat(targetUrl(WEBUI_URL + "/", request, login("github")))
                .isEqualTo("https://open-vsx.org/extension/foo/bar");
    }

    @Test
    void landsOnTheDefaultPageWhenNoTargetWasRemembered() {
        assertThat(targetUrl(WEBUI_URL, new MockHttpServletRequest(), login("github"))).isEqualTo(WEBUI_URL);
    }

    @Test
    void landsAnEclipseLoginWithoutATargetOnTheDefaultPage() {
        assertThat(targetUrl(WEBUI_URL, new MockHttpServletRequest(), login("eclipse"))).isEqualTo(WEBUI_URL);
    }

    // The page that links an extra login, such as the publisher agreement panel, names itself as the target
    @Test
    void returnsALoginOfAnyProviderToThePageItStartedFrom() {
        var request = requestReturningTo("/user-settings/profile");

        assertThat(targetUrl(WEBUI_URL, request, login("eclipse")))
                .isEqualTo("https://open-vsx.org/user-settings/profile");
        assertThat(LoginReturnTo.take(request)).isNull();
    }

    private MockHttpServletRequest requestReturningTo(String target) {
        var request = new MockHttpServletRequest();
        request.setParameter(LoginReturnTo.PARAMETER, target);
        returnTo.capture(request);
        request.removeParameter(LoginReturnTo.PARAMETER);
        return request;
    }

    private String targetUrl(String webuiUrl, MockHttpServletRequest request, Authentication authentication) {
        return new CustomAuthenticationSuccessHandler(webuiUrl)
                .determineTargetUrl(request, new MockHttpServletResponse(), authentication);
    }

    private Authentication login(String provider) {
        var token = Mockito.mock(OAuth2AuthenticationToken.class);
        when(token.getAuthorizedClientRegistrationId()).thenReturn(provider);
        return token;
    }
}
