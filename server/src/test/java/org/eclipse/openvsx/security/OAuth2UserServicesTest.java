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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;

import org.eclipse.openvsx.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class OAuth2UserServicesTest {

    private ExternalLoginProvider provider;
    private OAuth2UserServices services;

    @BeforeEach
    void setup() {
        provider = Mockito.mock(ExternalLoginProvider.class);
        Mockito.when(provider.registrationId()).thenReturn("linked");
        services = new OAuth2UserServices(
                Mockito.mock(UserService.class),
                List.of(provider),
                Mockito.mock(OAuth2AttributesConfig.class));
    }

    @Test
    void matchingProviderLoadsTheUser() {
        var request = request("linked");
        var principal = new IdPrincipal(1, "user", List.of());
        Mockito.when(provider.loadUser(request)).thenReturn(principal);

        assertThat(services.loadUser(request)).isSameAs(principal);
    }

    @Test
    void unmatchedRegistrationStaysOnTheGenericLogin() {
        var request = request("other");

        // The mocked attributes config knows no mapping for it, which is the generic path's answer.
        assertThatThrownBy(() -> services.loadUser(request))
                .isInstanceOfSatisfying(
                        CodedAuthException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CodedAuthException.UNSUPPORTED_REGISTRATION));
        Mockito.verify(provider, Mockito.never()).loadUser(any());
    }

    @Test
    void matchingProviderReceivesTheSuccessCallback() {
        var principal = new IdPrincipal(1, "user", List.of());
        var token = loginToken("linked", principal);

        services.authenticationSucceeded(new AuthenticationSuccessEvent(token));

        Mockito.verify(provider).authenticationSucceeded(eq(principal), eq(token));
    }

    @Test
    void unmatchedRegistrationGetsNoSuccessCallback() {
        var token = loginToken("other", new IdPrincipal(1, "user", List.of()));

        services.authenticationSucceeded(new AuthenticationSuccessEvent(token));

        Mockito.verify(provider, Mockito.never()).authenticationSucceeded(any(), any());
    }

    private static ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id)
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://auth.example/authorize")
                .tokenUri("http://auth.example/token")
                .build();
    }

    private static OAuth2UserRequest request(String registrationId) {
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null);
        return new OAuth2UserRequest(registration(registrationId), accessToken);
    }

    private static OAuth2LoginAuthenticationToken loginToken(String registrationId, IdPrincipal principal) {
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null);
        return new OAuth2LoginAuthenticationToken(
                registration(registrationId),
                Mockito.mock(OAuth2AuthorizationExchange.class),
                principal,
                List.of(),
                accessToken);
    }
}
