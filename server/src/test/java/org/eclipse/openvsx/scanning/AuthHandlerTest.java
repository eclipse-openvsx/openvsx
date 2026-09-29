/******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpAuthHandler} authentication handling.
 */
@ExtendWith(MockitoExtension.class)
class AuthHandlerTest {

    @Mock
    RestTemplate restTemplate;

    @Test
    void applyAuth_doesNothingWhenNoConfig() {
        var handler = new HttpAuthHandler("scanner", null, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);

        assertTrue(headers.isEmpty());
    }

    @Test
    void applyAuth_doesNothingWhenTypeNull() {
        var config = new RemoteScannerProperties.AuthConfig();
        config.setType(null);
        var handler = new HttpAuthHandler("scanner", config, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);

        assertTrue(headers.isEmpty());
    }

    @Test
    void applyAuth_appliesApiKeyToHeader() {
        var apiKeyConfig = new RemoteScannerProperties.ApiKeyAuth();
        apiKeyConfig.setKey("my-api-key");
        apiKeyConfig.setHeaderName("X-API-Key");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("api-key");
        authConfig.setApiKey(apiKeyConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);

        assertEquals("my-api-key", headers.getFirst("X-API-Key"));
    }

    @Test
    void applyAuth_appliesApiKeyWithPrefix() {
        var apiKeyConfig = new RemoteScannerProperties.ApiKeyAuth();
        apiKeyConfig.setKey("secret123");
        apiKeyConfig.setHeaderName("Authorization");
        apiKeyConfig.setPrefix("ApiKey ");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("api-key");
        authConfig.setApiKey(apiKeyConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);

        assertEquals("ApiKey secret123", headers.getFirst("Authorization"));
    }

    @Test
    void getAuthQueryParams_returnsApiKeyParam() {
        var apiKeyConfig = new RemoteScannerProperties.ApiKeyAuth();
        apiKeyConfig.setKey("query-key-value");
        apiKeyConfig.setQueryParam("api_key");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("api-key");
        authConfig.setApiKey(apiKeyConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);

        var params = handler.getAuthQueryParams();

        assertEquals(Map.of("api_key", "query-key-value"), params);
    }

    @Test
    void getAuthQueryParams_returnsEmptyForNonApiKey() {
        var bearerConfig = new RemoteScannerProperties.BearerAuth();
        bearerConfig.setToken("token");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("bearer");
        authConfig.setBearer(bearerConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);

        assertTrue(handler.getAuthQueryParams().isEmpty());
    }

    @Test
    void applyAuth_appliesBearerToken() {
        var bearerConfig = new RemoteScannerProperties.BearerAuth();
        bearerConfig.setToken("my-bearer-token");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("bearer");
        authConfig.setBearer(bearerConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);

        assertEquals("Bearer my-bearer-token", headers.getFirst("Authorization"));
    }

    @Test
    void applyAuth_appliesBasicAuth() {
        var basicConfig = new RemoteScannerProperties.BasicAuth();
        basicConfig.setUsername("user");
        basicConfig.setPassword("pass");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("basic");
        authConfig.setBasic(basicConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);

        String authHeader = headers.getFirst("Authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "));

        // Decode and verify credentials
        String encoded = authHeader.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded));
        assertEquals("user:pass", decoded);
    }

    @Test
    void applyAuth_handlesUnknownType() {
        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("unknown");

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        // Should not throw, just log warning
        handler.applyAuth(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void applyAuth_handlesMissingApiKeyConfig() {
        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("api-key");
        // apiKey not set

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        // Should not throw
        handler.applyAuth(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void applyAuth_handlesMissingBearerConfig() {
        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("bearer");
        // bearer not set

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void applyAuth_handlesMissingBasicConfig() {
        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("basic");
        // basic not set

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void applyAuth_handlesEmptyApiKey() {
        var apiKeyConfig = new RemoteScannerProperties.ApiKeyAuth();
        apiKeyConfig.setKey("");
        apiKeyConfig.setHeaderName("X-API-Key");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("api-key");
        authConfig.setApiKey(apiKeyConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);
        assertNull(headers.getFirst("X-API-Key"));
    }

    @Test
    void applyAuth_handlesEmptyBearerToken() {
        var bearerConfig = new RemoteScannerProperties.BearerAuth();
        bearerConfig.setToken("");

        var authConfig = new RemoteScannerProperties.AuthConfig();
        authConfig.setType("bearer");
        authConfig.setBearer(bearerConfig);

        var handler = new HttpAuthHandler("scanner", authConfig, restTemplate);
        var headers = new HttpHeaders();

        handler.applyAuth(headers);
        assertNull(headers.getFirst("Authorization"));
    }
}
