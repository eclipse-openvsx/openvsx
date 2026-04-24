/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.scanning;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RemoteScannerProperties} configuration classes.
 */
class RemoteScannerPropertiesTest {

    // === ScannerConfig defaults ===

    @Test
    void scannerConfig_hasCorrectDefaults() {
        var config = new RemoteScannerProperties.ScannerConfig();

        assertFalse(config.isEnabled());
        assertTrue(config.isRequired());
        assertTrue(config.isEnforced());
        assertEquals(60, config.getTimeoutMinutes());
        assertTrue(config.isAsync());
        assertNotNull(config.getHttp());
        assertNotNull(config.getPolling());
    }

    @Test
    void scannerConfig_settersWork() {
        var config = new RemoteScannerProperties.ScannerConfig();
        config.setEnabled(true);
        config.setType("MY_SCANNER");
        config.setRequired(false);
        config.setEnforced(false);
        config.setTimeoutMinutes(120);
        config.setAsync(false);

        assertTrue(config.isEnabled());
        assertEquals("MY_SCANNER", config.getType());
        assertFalse(config.isRequired());
        assertFalse(config.isEnforced());
        assertEquals(120, config.getTimeoutMinutes());
        assertFalse(config.isAsync());
    }

    @Test
    void scannerConfig_externalUrlTemplateDefaultsToNull() {
        // Scanners without an external dashboard should not contribute a deep
        // link. The absence of a template is the signal for that.
        var config = new RemoteScannerProperties.ScannerConfig();

        assertNull(config.getExternalUrlTemplate());
    }

    @Test
    void scannerConfig_externalUrlTemplateSetterWorks() {
        var config = new RemoteScannerProperties.ScannerConfig();
        config.setExternalUrlTemplate("https://app.example.com/scans/{jobId}");

        assertEquals("https://app.example.com/scans/{jobId}", config.getExternalUrlTemplate());
    }

    // === HttpOperation defaults ===

    @Test
    void httpOperation_hasCorrectDefaults() {
        var operation = new RemoteScannerProperties.HttpOperation();

        assertEquals("GET", operation.getMethod());
        assertNotNull(operation.getHeaders());
        assertTrue(operation.getHeaders().isEmpty());
        assertNotNull(operation.getQueryParams());
        assertTrue(operation.getQueryParams().isEmpty());
    }

    @Test
    void httpOperation_settersWork() {
        var operation = new RemoteScannerProperties.HttpOperation();
        operation.setMethod("POST");
        operation.setUrl("https://api.example.com/scan");
        operation.setHeaders(Map.of("X-API-Key", "secret"));

        assertEquals("POST", operation.getMethod());
        assertEquals("https://api.example.com/scan", operation.getUrl());
        assertEquals("secret", operation.getHeaders().get("X-API-Key"));
    }

    // === BodyConfig defaults ===

    @Test
    void bodyConfig_hasCorrectDefaults() {
        var body = new RemoteScannerProperties.BodyConfig();

        assertEquals("json", body.getType());
        assertEquals("file", body.getFileField());
        assertNotNull(body.getFields());
        assertTrue(body.getFields().isEmpty());
    }

    @Test
    void bodyConfig_settersWork() {
        var body = new RemoteScannerProperties.BodyConfig();
        body.setType("multipart");
        body.setFileField("extension");
        body.setTemplate("{\"data\": \"{fileName}\"}");

        assertEquals("multipart", body.getType());
        assertEquals("extension", body.getFileField());
        assertEquals("{\"data\": \"{fileName}\"}", body.getTemplate());
    }

    // === ResponseConfig defaults ===

    @Test
    void responseConfig_hasCorrectDefaults() {
        var response = new RemoteScannerProperties.ResponseConfig();

        assertEquals("json", response.getFormat());
        assertNotNull(response.getStatusMapping());
        assertTrue(response.getStatusMapping().isEmpty());
    }

    @Test
    void responseConfig_settersWork() {
        var response = new RemoteScannerProperties.ResponseConfig();
        response.setFormat("xml");
        response.setJobIdPath("$.scan.id");
        response.setStatusPath("$.scan.status");
        response.setStatusMapping(Map.of("done", "COMPLETED"));
        response.setThreatsPath("$.threats[*]");
        response.setErrorPath("$.error");
        response.setErrorCondition("$.status == 'error'");
        response.setSummaryPath("$.verdictData.summary");

        assertEquals("xml", response.getFormat());
        assertEquals("$.scan.id", response.getJobIdPath());
        assertEquals("$.scan.status", response.getStatusPath());
        assertEquals("COMPLETED", response.getStatusMapping().get("done"));
        assertEquals("$.threats[*]", response.getThreatsPath());
        assertEquals("$.error", response.getErrorPath());
        assertEquals("$.status == 'error'", response.getErrorCondition());
        assertEquals("$.verdictData.summary", response.getSummaryPath());
    }

    // === ThreatMapping tests ===

    @Test
    void threatMapping_settersWork() {
        var mapping = new RemoteScannerProperties.ThreatMapping();
        mapping.setNamePath("$.rule");
        mapping.setDescriptionPath("$.message");
        mapping.setSeverityPath("$.severity");
        mapping.setSeverityExpression("detected ? 'HIGH' : 'LOW'");
        mapping.setFilePathPath("$.file");
        mapping.setFileHashPath("$.hash");
        mapping.setCondition("$.detected == true");

        assertEquals("$.rule", mapping.getNamePath());
        assertEquals("$.message", mapping.getDescriptionPath());
        assertEquals("$.severity", mapping.getSeverityPath());
        assertEquals("detected ? 'HIGH' : 'LOW'", mapping.getSeverityExpression());
        assertEquals("$.file", mapping.getFilePathPath());
        assertEquals("$.hash", mapping.getFileHashPath());
        assertEquals("$.detected == true", mapping.getCondition());
    }

    // === RetryConfig defaults ===

    @Test
    void retryConfig_hasCorrectDefaults() {
        var retry = new RemoteScannerProperties.RetryConfig();

        assertEquals(3, retry.getMaxAttempts());
        assertEquals(1000, retry.getInitialDelayMs());
        assertEquals(2.0, retry.getMultiplier());
        assertEquals(30000, retry.getMaxDelayMs());
    }

    // === HttpConfig defaults ===

    @Test
    void httpConfig_hasCorrectDefaults() {
        var http = new RemoteScannerProperties.HttpConfig();

        assertEquals(10, http.getMaxTotal());
        assertEquals(5, http.getDefaultMaxPerRoute());
        assertEquals(30000, http.getConnectionRequestTimeoutMs());
        assertEquals(30000, http.getConnectTimeoutMs());
        assertEquals(300000, http.getSocketTimeoutMs());
    }

    // === PollConfig defaults ===

    @Test
    void pollConfig_hasCorrectDefaults() {
        var poll = new RemoteScannerProperties.PollConfig();

        assertEquals(5, poll.getInitialDelaySeconds());
        assertEquals(30, poll.getIntervalSeconds());
        assertEquals(60, poll.getMaxAttempts());
        assertFalse(poll.isExponentialBackoff());
        assertEquals(300, poll.getMaxIntervalSeconds());
        assertEquals(2.0, poll.getBackoffMultiplier());
    }

    @Test
    void pollConfig_settersWork() {
        var poll = new RemoteScannerProperties.PollConfig();
        poll.setInitialDelaySeconds(10);
        poll.setIntervalSeconds(60);
        poll.setMaxAttempts(120);
        poll.setExponentialBackoff(true);
        poll.setMaxIntervalSeconds(600);
        poll.setBackoffMultiplier(1.5);

        assertEquals(10, poll.getInitialDelaySeconds());
        assertEquals(60, poll.getIntervalSeconds());
        assertEquals(120, poll.getMaxAttempts());
        assertTrue(poll.isExponentialBackoff());
        assertEquals(600, poll.getMaxIntervalSeconds());
        assertEquals(1.5, poll.getBackoffMultiplier());
    }

    // === Auth configs ===

    @Test
    void apiKeyAuth_settersWork() {
        var apiKey = new RemoteScannerProperties.ApiKeyAuth();
        apiKey.setKey("my-api-key");
        apiKey.setHeaderName("X-API-Key");
        apiKey.setQueryParam("api_key");
        apiKey.setPrefix("ApiKey ");

        assertEquals("my-api-key", apiKey.getKey());
        assertEquals("X-API-Key", apiKey.getHeaderName());
        assertEquals("api_key", apiKey.getQueryParam());
        assertEquals("ApiKey ", apiKey.getPrefix());
    }

    @Test
    void apiKeyAuth_defaultPrefix() {
        var apiKey = new RemoteScannerProperties.ApiKeyAuth();
        assertEquals("", apiKey.getPrefix());
    }

    @Test
    void bearerAuth_settersWork() {
        var bearer = new RemoteScannerProperties.BearerAuth();
        bearer.setToken("my-bearer-token");

        assertEquals("my-bearer-token", bearer.getToken());
    }

    @Test
    void basicAuth_settersWork() {
        var basic = new RemoteScannerProperties.BasicAuth();
        basic.setUsername("user");
        basic.setPassword("pass");

        assertEquals("user", basic.getUsername());
        assertEquals("pass", basic.getPassword());
    }

    @Test
    void oauth2Auth_hasCorrectDefaults() {
        var oauth2 = new RemoteScannerProperties.OAuth2Auth();

        assertEquals(60, oauth2.getRefreshBeforeExpiry());
    }

    @Test
    void oauth2Auth_settersWork() {
        var oauth2 = new RemoteScannerProperties.OAuth2Auth();
        oauth2.setTokenUrl("https://auth.example.com/token");
        oauth2.setClientId("client-id");
        oauth2.setClientSecret("client-secret");
        oauth2.setScope("scan:read scan:write");
        oauth2.setRefreshBeforeExpiry(120);

        assertEquals("https://auth.example.com/token", oauth2.getTokenUrl());
        assertEquals("client-id", oauth2.getClientId());
        assertEquals("client-secret", oauth2.getClientSecret());
        assertEquals("scan:read scan:write", oauth2.getScope());
        assertEquals(120, oauth2.getRefreshBeforeExpiry());
    }

    // === RemoteScannerProperties ===

    @Test
    void remoteScannerProperties_configuredMapWorks() {
        var props = new RemoteScannerProperties();
        var scannerConfig = new RemoteScannerProperties.ScannerConfig();
        scannerConfig.setType("TEST_SCANNER");

        var configured = new HashMap<String, RemoteScannerProperties.ScannerConfig>();
        configured.put("test", scannerConfig);
        props.setConfigured(configured);

        assertEquals(1, props.getConfigured().size());
        assertEquals("TEST_SCANNER", props.getConfigured().get("test").getType());
        // getScanners() should return same map
        assertEquals(props.getConfigured(), props.getScanners());
    }
}
