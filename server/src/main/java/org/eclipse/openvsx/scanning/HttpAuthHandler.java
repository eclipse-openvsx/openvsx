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
package org.eclipse.openvsx.scanning;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Handles authentication for scanner HTTP requests.
 * <p>
 * Supports api-key, bearer, basic, and oauth2 (client credentials with auto refresh).
 * Thread-safe with per-scanner OAuth2 token caching.
 */
public class HttpAuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(HttpAuthHandler.class);

    private static final Map<String, OAuth2Token> tokenCache = new ConcurrentHashMap<>();

    private final String scannerName;
    private final RemoteScannerProperties.AuthConfig authConfig;
    private final RestTemplate restTemplate;

    /**
     * Create an auth handler for a scanner.
     */
    public HttpAuthHandler(
            String scannerName,
            RemoteScannerProperties.@Nullable AuthConfig authConfig,
            RestTemplate restTemplate
    ) {
        this.scannerName = scannerName;
        this.authConfig = authConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * Apply authentication to HTTP headers.
     */
    public void applyAuth(HttpHeaders headers) {
        if (authConfig == null || authConfig.getType() == null) {
            return; // No auth configured
        }

        String authType = authConfig.getType().toLowerCase();
        switch (authType) {
            case "api-key":
                applyApiKeyAuth(headers);
                break;
            case "bearer":
                applyBearerAuth(headers);
                break;
            case "basic":
                applyBasicAuth(headers);
                break;
            case "oauth2":
                applyOAuth2Auth(headers);
                break;
            default :
                logger.warn("Unknown auth type '{}' for scanner {}", authType, scannerName);
        }
    }

    /**
     * Get query parameters for authentication (for api-key in query string).
     */
    public Map<String, String> getAuthQueryParams() {
        if (authConfig == null || authConfig.getType() == null) {
            return Map.of();
        }

        if ("api-key".equalsIgnoreCase(authConfig.getType())) {
            var apiKeyConfig = authConfig.getApiKey();
            if (apiKeyConfig != null && apiKeyConfig.getQueryParam() != null) {
                return Map.of(apiKeyConfig.getQueryParam(), apiKeyConfig.getKey());
            }
        }

        return Map.of();
    }

    /**
     * Apply API key authentication.
     * Can be in header or query parameter (query handled by getAuthQueryParams).
     */
    private void applyApiKeyAuth(HttpHeaders headers) {
        var apiKeyConfig = authConfig.getApiKey();
        if (apiKeyConfig == null) {
            logger.warn("API key auth configured but api-key section missing for scanner {}", scannerName);
            return;
        }

        String key = apiKeyConfig.getKey();
        if (key == null || key.isEmpty()) {
            logger.warn("API key is empty for scanner {}", scannerName);
            return;
        }

        // Apply prefix if configured (e.g., "ApiKey " or "Api-Key ")
        String prefix = apiKeyConfig.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            key = prefix + key;
        }

        // Add to header if header name is configured
        String headerName = apiKeyConfig.getHeaderName();
        if (headerName != null && !headerName.isEmpty()) {
            headers.set(headerName, key);
            logger.debug("Applied API key auth to header '{}' for scanner {}", headerName, scannerName);
        }
    }

    /**
     * Apply Bearer token authentication.
     */
    private void applyBearerAuth(HttpHeaders headers) {
        var bearerConfig = authConfig.getBearer();
        if (bearerConfig == null) {
            logger.warn("Bearer auth configured but bearer section missing for scanner {}", scannerName);
            return;
        }

        String token = bearerConfig.getToken();
        if (token == null || token.isEmpty()) {
            logger.warn("Bearer token is empty for scanner {}", scannerName);
            return;
        }

        headers.setBearerAuth(token);
        logger.debug("Applied Bearer auth for scanner {}", scannerName);
    }

    /**
     * Apply HTTP Basic authentication.
     */
    private void applyBasicAuth(HttpHeaders headers) {
        var basicConfig = authConfig.getBasic();
        if (basicConfig == null) {
            logger.warn("Basic auth configured but basic section missing for scanner {}", scannerName);
            return;
        }

        String username = basicConfig.getUsername();
        String password = basicConfig.getPassword();

        if (username == null || password == null) {
            logger.warn("Basic auth username or password is null for scanner {}", scannerName);
            return;
        }

        headers.setBasicAuth(username, password, StandardCharsets.UTF_8);
        logger.debug("Applied Basic auth for scanner {}", scannerName);
    }

    /**
     * Apply OAuth2 client credentials authentication.
     * Automatically refreshes tokens when they expire.
     */
    private void applyOAuth2Auth(HttpHeaders headers) {
        var oauth2Config = authConfig.getOauth2();
        if (oauth2Config == null) {
            logger.warn("OAuth2 auth configured but oauth2 section missing for scanner {}", scannerName);
            return;
        }

        String token = getOrRefreshOAuth2Token(oauth2Config);
        if (token == null) {
            logger.error("Failed to obtain OAuth2 token for scanner {}", scannerName);
            return;
        }

        headers.setBearerAuth(token);
        logger.debug("Applied OAuth2 auth for scanner {}", scannerName);
    }

    /**
     * Get a valid OAuth2 token, refreshing if necessary.
     */
    private synchronized String getOrRefreshOAuth2Token(RemoteScannerProperties.OAuth2Auth config) {
        String cacheKey = scannerName;
        OAuth2Token cachedToken = tokenCache.get(cacheKey);

        // Check if we have a valid cached token
        int refreshBuffer = config.getRefreshBeforeExpiry();
        if (cachedToken != null && !cachedToken.isExpired(refreshBuffer)) {
            return cachedToken.accessToken;
        }

        // Need to fetch a new token
        logger.info("Fetching new OAuth2 token for scanner {}", scannerName);

        try {
            String tokenUrl = config.getTokenUrl();
            String clientId = config.getClientId();
            String clientSecret = config.getClientSecret();
            String scope = config.getScope();

            // Build token request
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            requestHeaders.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            if (scope != null && !scope.isEmpty()) {
                body.add("scope", scope);
            }

            var request = new org.springframework.http.HttpEntity<>(body, requestHeaders);
            var response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response == null || !response.containsKey("access_token")) {
                logger.error("OAuth2 token response missing access_token for scanner {}", scannerName);
                return null;
            }

            String accessToken = (String) response.get("access_token");
            int expiresIn = 3600; // Default 1 hour
            if (response.containsKey("expires_in")) {
                Object expiresInObj = response.get("expires_in");
                if (expiresInObj instanceof Number) {
                    expiresIn = ((Number) expiresInObj).intValue();
                }
            }

            // Cache the token
            OAuth2Token newToken = new OAuth2Token(accessToken, Instant.now().plusSeconds(expiresIn));
            tokenCache.put(cacheKey, newToken);

            logger.info("Obtained OAuth2 token for scanner {}, expires in {} seconds", scannerName, expiresIn);
            return accessToken;

        } catch (Exception e) {
            logger.error("Failed to fetch OAuth2 token for scanner {}: {}", scannerName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Cached OAuth2 token with expiry tracking.
     */
    private static class OAuth2Token {
        final String accessToken;
        final Instant expiresAt;

        OAuth2Token(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        /**
         * Check if token is expired or will expire soon.
         *
         * @param bufferSeconds Seconds before expiry to consider expired
         */
        boolean isExpired(int bufferSeconds) {
            return Instant.now().plusSeconds(bufferSeconds).isAfter(expiresAt);
        }
    }
}
