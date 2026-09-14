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
package org.eclipse.openvsx.util;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpHeadersUtilTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testResolveAccessTokenPrefersAuthorizationBearer() {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bearer-token");
        request.addHeader(HttpHeadersUtil.TOKEN_HEADER, "fallback-header-token");

        assertThat(HttpHeadersUtil.resolveAccessToken(request, "query-token")).isEqualTo("bearer-token");
    }

    @Test
    void testResolveAccessTokenBearerSchemeIsCaseInsensitiveAndTrimmed() {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "bearer   bearer-token  ");

        assertThat(HttpHeadersUtil.resolveAccessToken(request, "query-token")).isEqualTo("bearer-token");
    }

    @Test
    void testResolveAccessTokenFallsBackToOpenVsxHeader() {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeadersUtil.TOKEN_HEADER, "fallback-header-token");

        assertThat(HttpHeadersUtil.resolveAccessToken(request, "query-token")).isEqualTo("fallback-header-token");
    }

    @Test
    void testResolveAccessTokenIgnoresNonBearerAuthorization() {
        // Authorization: Basic is a different scheme entirely (e.g. a reverse proxy in front of a
        // self-hosted registry) - it must not be mistaken for a PAT nor block the other fallbacks.
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        request.addHeader(HttpHeadersUtil.TOKEN_HEADER, "fallback-header-token");

        assertThat(HttpHeadersUtil.resolveAccessToken(request, "query-token")).isEqualTo("fallback-header-token");
    }

    @Test
    void testResolveAccessTokenFallsBackToQueryParam() {
        var request = new MockHttpServletRequest();

        assertThat(HttpHeadersUtil.resolveAccessToken(request, "query-token")).isEqualTo("query-token");
    }

    @Test
    void testResolveAccessTokenTreatsBlankHeadersAsAbsent() {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer   ");
        request.addHeader(HttpHeadersUtil.TOKEN_HEADER, "   ");

        assertThat(HttpHeadersUtil.resolveAccessToken(request, "query-token")).isEqualTo("query-token");
    }

    @Test
    void testResolveAccessTokenReturnsNullWhenNothingIsSupplied() {
        var request = new MockHttpServletRequest();

        assertThat(HttpHeadersUtil.resolveAccessToken(request, null)).isNull();
    }

    @Test
    void testGetForwardedHeadersExcludesCredentials() {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        request.addHeader(HttpHeadersUtil.TOKEN_HEADER, "another-secret-token");
        request.addHeader(HttpHeaders.HOST, "openvsx.example");
        request.addHeader("Accept", "application/json");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var headers = HttpHeadersUtil.getForwardedHeaders();

        assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(headers.get(HttpHeadersUtil.TOKEN_HEADER)).isNull();
        assertThat(headers.get(HttpHeaders.HOST)).isNull();
        assertThat(headers.getFirst("Accept")).isEqualTo("application/json");
    }

    @Test
    void testCreateJsonFileResponseHeaders() {
        var headers = HttpHeadersUtil.createJsonFileResponseHeaders();

        var contentType = headers.getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.toString()).isEqualTo("application/json");

        var contentTypeOptions = headers.get("X-Content-Type-Options");
        assertThat(contentTypeOptions).isEqualTo(List.of("nosniff"));

        var contentSecurityPolicy = headers.get("Content-Security-Policy");
        assertThat(contentSecurityPolicy).isEqualTo(
                List.of("default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox"));
    }

    @Test
    void testCreateFileResponseHeadersForHtml() {
        var headers = HttpHeadersUtil.createFileResponseHeaders((InputStream) null, "file.html");

        var contentType = headers.getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.toString()).isEqualTo("text/plain;charset=UTF-8");

        var contentTypeOptions = headers.get("X-Content-Type-Options");
        assertThat(contentTypeOptions).isEqualTo(List.of("nosniff"));

        var contentSecurityPolicy = headers.get("Content-Security-Policy");
        assertThat(contentSecurityPolicy).isEqualTo(
                List.of("default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox"));
    }

    @Test
    void testCreateFileResponseHeadersForVsix() {
        var headers = HttpHeadersUtil.createFileResponseHeaders((InputStream) null, "file.vsix");

        var contentType = headers.getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.toString()).isEqualTo("application/octet-stream");

        var contentTypeOptions = headers.get("X-Content-Type-Options");
        assertThat(contentTypeOptions).isEqualTo(List.of("nosniff"));

        var contentSecurityPolicy = headers.get("Content-Security-Policy");
        assertThat(contentSecurityPolicy).isEqualTo(
                List.of("default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox"));

        var contentDisposition = headers.getContentDisposition();
        assertThat(contentDisposition.isAttachment()).isTrue();
        assertThat(contentDisposition.getFilename()).isEqualTo("file.vsix");
    }
}
