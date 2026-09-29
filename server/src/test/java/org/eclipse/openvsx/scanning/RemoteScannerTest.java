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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RemoteScanner}'s response parsing, focused on the malicious verdict extraction.
 */
class RemoteScannerTest {

    private RemoteScanner newScanner() {
        var config = new RemoteScannerProperties.ScannerConfig();
        config.setType("ARGUS");
        return new RemoteScanner(
                "argus",
                config,
                null,
                null,
                new HttpResponseExtractor(new JsonMapper()),
                null);
    }

    private RemoteScannerProperties.HttpOperation operationWithMaliciousVerdictPath(String maliciousVerdictPath) {
        var response = new RemoteScannerProperties.ResponseConfig();
        response.setMaliciousVerdictPath(maliciousVerdictPath);

        var operation = new RemoteScannerProperties.HttpOperation();
        operation.setResponse(response);
        return operation;
    }

    private Scanner.Result parseResult(
            RemoteScanner scanner,
            String response,
            RemoteScannerProperties.HttpOperation operation
    ) throws Exception {
        Method method = RemoteScanner.class.getDeclaredMethod(
                "parseResult",
                String.class,
                RemoteScannerProperties.HttpOperation.class);
        method.setAccessible(true);
        try {
            return (Scanner.Result) method.invoke(scanner, response, operation);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ScannerException scannerException) {
                throw scannerException;
            }
            throw e;
        }
    }

    @Test
    void parseResult_fallsBackToFindings_whenMaliciousVerdictPathConfigured_butMissingFromResponse()
            throws Exception {
        var scanner = newScanner();
        var operation = operationWithMaliciousVerdictPath("$.verdictData.isMalicious");
        String response = "{\"verdictData\": {\"summary\": \"ok\"}}"; // no isMalicious field

        Scanner.Result result = parseResult(scanner, response, operation);

        assertNull(result.isMalicious());
    }

    @Test
    void parseResult_fallsBackToFindings_whenMaliciousVerdictPathConfigured_butNotABoolean() throws Exception {
        var scanner = newScanner();
        var operation = operationWithMaliciousVerdictPath("$.verdictData.isMalicious");
        String response = "{\"verdictData\": {\"isMalicious\": \"unknown\"}}"; // not true/false

        Scanner.Result result = parseResult(scanner, response, operation);

        assertNull(result.isMalicious());
    }

    @Test
    void parseResult_succeeds_whenMaliciousVerdictPathConfigured_andResolvesToBoolean() throws Exception {
        var scanner = newScanner();
        var operation = operationWithMaliciousVerdictPath("$.verdictData.isMalicious");
        String response = "{\"verdictData\": {\"isMalicious\": true}}";

        Scanner.Result result = parseResult(scanner, response, operation);

        assertTrue(result.hasMaliciousVerdict());
    }

    @Test
    void parseResult_doesNotThrow_whenNoMaliciousVerdictPathConfigured() throws Exception {
        var scanner = newScanner();
        var operation = operationWithMaliciousVerdictPath(null);
        String response = "{}";

        Scanner.Result result = parseResult(scanner, response, operation);

        assertNull(result.isMalicious());
    }
}
