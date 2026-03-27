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
package org.eclipse.openvsx.scanning;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes HTTP requests based on configuration.
 * <p>
 * Handles different HTTP methods, body types (JSON, multipart, form-urlencoded),
 * headers, query parameters, file uploads, and authentication.
 * <p>
 * Use static factory methods to create instances with scanner-specific configs.
 */
public class HttpClientExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpClientExecutor.class);
    
    private final RestTemplate restTemplate;
    private final HttpAuthHandler authHandler;
    
    /**
     * Private constructor - use static factory methods.
     */
    private HttpClientExecutor(RestTemplate restTemplate, @Nullable HttpAuthHandler authHandler) {
        this.restTemplate = restTemplate;
        this.authHandler = authHandler;
    }
    
    /**
     * Create an HttpClientExecutor with HTTP config and authentication.
     */
    public static HttpClientExecutor create(
            RemoteScannerProperties.HttpConfig httpConfig,
            @Nullable RemoteScannerProperties.AuthConfig authConfig,
            String scannerName
    ) {
        logger.debug("Creating HTTP client for scanner '{}': socketTimeout={}ms, connectTimeout={}ms, auth={}",
            scannerName,
            httpConfig.getSocketTimeoutMs(),
            httpConfig.getConnectTimeoutMs(),
            authConfig != null ? authConfig.getType() : "none"
        );
        
        RestTemplate restTemplate = createRestTemplate(httpConfig);
        
        HttpAuthHandler authHandler = null;
        if (authConfig != null && authConfig.getType() != null) {
            authHandler = new HttpAuthHandler(scannerName, authConfig, restTemplate);
        }
        
        return new HttpClientExecutor(restTemplate, authHandler);
    }
    
    /**
     * Create an HttpClientExecutor with HTTP config only (no auth).
     */
    public static HttpClientExecutor create(
            RemoteScannerProperties.HttpConfig httpConfig,
            String scannerName
    ) {
        return create(httpConfig, null, scannerName);
    }
    
    /**
     * Create an HttpClientExecutor with default HTTP configuration.
     */
    public static HttpClientExecutor createWithDefaults(String scannerName) {
        return create(new RemoteScannerProperties.HttpConfig(), null, scannerName);
    }
    
    /**
     * Create a RestTemplate with the specified HTTP configuration.
     */
    private static RestTemplate createRestTemplate(RemoteScannerProperties.HttpConfig httpConfig) {
        var connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.of(httpConfig.getConnectTimeoutMs(), TimeUnit.MILLISECONDS))
            .setSocketTimeout(Timeout.of(httpConfig.getSocketTimeoutMs(), TimeUnit.MILLISECONDS))
            .build();
        
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(httpConfig.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(httpConfig.getDefaultMaxPerRoute());
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        
        var requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(httpConfig.getConnectionRequestTimeoutMs(), TimeUnit.MILLISECONDS))
            .build();
        
        var httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
        
        return new RestTemplateBuilder()
            .requestFactory(() -> {
                HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                factory.setHttpClient(httpClient);
                return factory;
            })
            .build();
    }
    
    /**
     * Execute an HTTP request based on configuration.
     */
    public String execute(
        RemoteScannerProperties.HttpOperation operation,
        File file
    ) throws ScannerException {
        try {
            // Build request entity (includes auth headers)
            HttpEntity<?> requestEntity = buildRequestEntity(operation, file);
            
            // Build URL with auth query params if needed
            String url = buildUrlWithAuth(operation.getUrl());
            
            // Execute request
            HttpMethod method = HttpMethod.valueOf(operation.getMethod().toUpperCase());
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                method,
                requestEntity,
                String.class
            );
            
            // Return response body for successful requests
            return response.getBody();
            
        } catch (HttpStatusCodeException e) {
            // 5xx = server error — always treat as failure even if body is present
            if (e.getStatusCode().is5xxServerError()) {
                String bodySnippet = e.getResponseBodyAsString();
                if (bodySnippet != null && bodySnippet.length() > 200) {
                    bodySnippet = bodySnippet.substring(0, 200) + "...";
                }
                throw new ScannerException(
                    "Scanner service error: " + e.getStatusCode() + " " +
                    e.getStatusText() + " on " + operation.getMethod() + " " +
                    operation.getUrl() +
                    (bodySnippet != null && !bodySnippet.isEmpty() ? " — " + bodySnippet : ""), e
                );
            }
            // 4xx — some scanners use non-2xx for valid results (e.g., 406 = malware found).
            // Return the body so the scanner can parse it.
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null && !responseBody.isEmpty()) {
                return responseBody;
            }
            // No body on a 4xx — genuine error
            throw new ScannerException(
                "Failed to execute HTTP request: " + e.getStatusCode() + " " + 
                e.getStatusText() + " on " + operation.getMethod() + " request for \"" + 
                operation.getUrl() + "\"", e
            );
        } catch (Exception e) {
            throw new ScannerException("Failed to execute HTTP request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build URL with authentication query parameters if needed.
     */
    private String buildUrlWithAuth(String baseUrl) {
        if (authHandler == null) {
            return baseUrl;
        }
        
        Map<String, String> authParams = authHandler.getAuthQueryParams();
        if (authParams.isEmpty()) {
            return baseUrl;
        }
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        authParams.forEach(builder::queryParam);
        return builder.build().toUriString();
    }
    
    /**
     * Build the HTTP request entity based on configuration.
     */
    private HttpEntity<?> buildRequestEntity(
        RemoteScannerProperties.HttpOperation operation,
        File file
    ) {
        // Build headers
        HttpHeaders headers = new HttpHeaders();
        
        // Apply authentication headers first (can be overridden by operation headers)
        if (authHandler != null) {
            authHandler.applyAuth(headers);
        }
        
        // Apply operation-specific headers (may override auth headers)
        if (operation.getHeaders() != null) {
            operation.getHeaders().forEach(headers::set);
        }
        
        // Build body based on type
        RemoteScannerProperties.BodyConfig bodyConfig = operation.getBody();
        if (bodyConfig == null) {
            // No body - just headers
            return new HttpEntity<>(headers);
        }
        
        String bodyType = bodyConfig.getType();
        return switch (bodyType.toLowerCase()) {
            case "multipart" -> buildMultipartEntity(bodyConfig, file, headers);
            case "json" -> buildJsonEntity(bodyConfig, headers);
            case "form-urlencoded" -> buildFormEntity(bodyConfig, headers);
            case "raw" -> buildRawEntity(bodyConfig, headers);
            default -> throw new IllegalArgumentException("Unsupported body type: " + bodyType);
        };
    }
    
    /**
     * Build multipart/form-data request entity.
     * Used for file uploads.
     */
    private HttpEntity<MultiValueMap<String, Object>> buildMultipartEntity(
        RemoteScannerProperties.BodyConfig bodyConfig,
        File file,
        HttpHeaders headers
    ) {
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        // Add file if present
        if (file != null) {
            String fileField = bodyConfig.getFileField();
            body.add(fileField, new FileSystemResource(file));
        }
        
        // Add additional fields
        if (bodyConfig.getFields() != null) {
            bodyConfig.getFields().forEach(body::add);
        }
        
        return new HttpEntity<>(body, headers);
    }
    
    /**
     * Build JSON request entity.
     */
    private HttpEntity<String> buildJsonEntity(
        RemoteScannerProperties.BodyConfig bodyConfig,
        HttpHeaders headers
    ) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String jsonBody = bodyConfig.getTemplate();
        if (jsonBody == null) {
            jsonBody = "{}";
        }
        
        return new HttpEntity<>(jsonBody, headers);
    }
    
    /**
     * Build form-urlencoded request entity.
     */
    private HttpEntity<MultiValueMap<String, String>> buildFormEntity(
        RemoteScannerProperties.BodyConfig bodyConfig,
        HttpHeaders headers
    ) {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        if (bodyConfig.getFields() != null) {
            bodyConfig.getFields().forEach(body::add);
        }
        
        return new HttpEntity<>(body, headers);
    }
    
    /**
     * Build raw text request entity.
     */
    private HttpEntity<String> buildRawEntity(
        RemoteScannerProperties.BodyConfig bodyConfig,
        HttpHeaders headers
    ) {
        String rawBody = bodyConfig.getTemplate();
        if (rawBody == null) {
            rawBody = "";
        }
        
        return new HttpEntity<>(rawBody, headers);
    }
}

