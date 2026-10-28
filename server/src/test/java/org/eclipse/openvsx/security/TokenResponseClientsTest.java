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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TokenResponseClientsTest {

    private static final String TOKEN_JSON = "{\"access_token\":\"tok\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}";

    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/token", exchange -> {
            var body = TOKEN_JSON.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            // Well over httpcore5's 8192-byte default line limit.
            exchange.getResponseHeaders().add("Content-Security-Policy", "default-src 'none'; " + "x".repeat(20_000));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldAcceptTokenResponseWithOversizedHeader() {
        var registration = ClientRegistration.withRegistrationId("github")
                .clientId("id")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/github")
                .authorizationUri("http://localhost/authorize")
                .tokenUri("http://localhost:" + server.getAddress().getPort() + "/token")
                .build();
        var authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("http://localhost/authorize")
                .clientId("id")
                .redirectUri("http://localhost/login/oauth2/code/github")
                .state("state")
                .build();
        var authorizationResponse = OAuth2AuthorizationResponse.success("code")
                .redirectUri("http://localhost/login/oauth2/code/github")
                .state("state")
                .build();
        var grantRequest = new OAuth2AuthorizationCodeGrantRequest(
                registration,
                new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse));

        var response = TokenResponseClients.authorizationCode().getTokenResponse(grantRequest);

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("tok");
    }
}
