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

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.config.Http1Config;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

final class TokenResponseClients {

    // httpcore5 rejects header lines over 8192 bytes by default, and GitHub's token response
    // carries a longer Content-Security-Policy header.
    static final int MAX_LINE_LENGTH = 64 * 1024;

    private TokenResponseClients() {
    }

    static OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCode() {
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .useSystemProperties()
                .setConnectionFactory(
                        ManagedHttpClientConnectionFactory.builder()
                                .http1Config(Http1Config.custom().setMaxLineLength(MAX_LINE_LENGTH).build())
                                .build())
                .build();
        var httpClient = HttpClients.custom()
                .useSystemProperties()
                .setConnectionManager(connectionManager)
                .build();
        // Same converters and error handler as the client Spring Security builds by default.
        var restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .configureMessageConverters(
                        converters -> converters
                                .addCustomConverter(new FormHttpMessageConverter())
                                .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        return client;
    }
}
