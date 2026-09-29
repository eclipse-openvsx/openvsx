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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpResponseExtractor} JSON extraction and condition evaluation.
 */
class ResponseExtractorTest {

    private HttpResponseExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new HttpResponseExtractor(new JsonMapper());
    }

    // === String extraction tests ===

    @Test
    void extractString_simpleField() throws ScannerException {
        String json = """
                {"status": "completed"}
                """;

        String result = extractor.extractString(json, "json", "$.status");

        assertEquals("completed", result);
    }

    @Test
    void extractString_nestedField() throws ScannerException {
        String json = """
                {"data": {"result": {"value": "found"}}}
                """;

        String result = extractor.extractString(json, "json", "$.data.result.value");

        assertEquals("found", result);
    }

    @Test
    void extractString_returnsNullForMissingField() throws ScannerException {
        String json = """
                {"status": "ok"}
                """;

        String result = extractor.extractString(json, "json", "$.missing");

        assertNull(result);
    }

    @Test
    void extractString_returnsNullForNullInput() throws ScannerException {
        assertNull(extractor.extractString(null, "json", "$.field"));
    }

    @Test
    void extractBoolean_readsJsonBoolean() throws ScannerException {
        String json = """
                {"verdictData": {"isMalicious": true}}
                """;

        assertEquals(Boolean.TRUE, extractor.extractBoolean(json, "json", "$.verdictData.isMalicious"));
    }

    @Test
    void extractBoolean_readsFalseAndMissing() throws ScannerException {
        String json = """
                {"verdictData": {"isMalicious": false}}
                """;

        assertEquals(Boolean.FALSE, extractor.extractBoolean(json, "json", "$.verdictData.isMalicious"));
        assertNull(extractor.extractBoolean(json, "json", "$.verdictData.missing"));
    }

    @Test
    void extractString_handlesArrayIndex() throws ScannerException {
        String json = """
                {"items": ["first", "second", "third"]}
                """;

        String result = extractor.extractString(json, "json", "$.items[0]");

        assertEquals("first", result);
    }

    @Test
    void extractString_throwsForInvalidJson() {
        assertThrows(ScannerException.class, () -> extractor.extractString("not json", "json", "$.field"));
    }

    @Test
    void extractString_throwsForUnsupportedFormat() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extractString("<xml/>", "xml", "/root"));
    }

    // === List extraction tests ===

    @Test
    void extractList_returnsArrayItems() throws ScannerException {
        String json = """
                {"threats": [{"name": "malware"}, {"name": "virus"}]}
                """;

        var result = extractor.extractList(json, "json", "$.threats[*]");

        assertEquals(2, result.size());
        assertEquals("malware", result.get(0).get("name"));
        assertEquals("virus", result.get(1).get("name"));
    }

    @Test
    void extractList_returnsEmptyForNullPath() throws ScannerException {
        var result = extractor.extractList("{}", "json", null);
        assertTrue(result.isEmpty());
    }

    // === Condition evaluation tests ===

    @Test
    void evaluateCondition_trueForLiteralTrue() {
        assertTrue(extractor.evaluateCondition(Map.of(), "true"));
    }

    @Test
    void evaluateCondition_falseForLiteralFalse() {
        assertFalse(extractor.evaluateCondition(Map.of(), "false"));
    }

    @Test
    void evaluateCondition_trueForEmptyCondition() {
        assertTrue(extractor.evaluateCondition(Map.of(), ""));
        assertTrue(extractor.evaluateCondition(Map.of(), null));
    }

    @Test
    void evaluateCondition_equalsComparison() {
        Map<String, Object> map = Map.of("status", "infected");

        assertTrue(extractor.evaluateCondition(map, "$.status == infected"));
        assertFalse(extractor.evaluateCondition(map, "$.status == clean"));
    }

    @Test
    void evaluateCondition_notEqualsComparison() {
        Map<String, Object> map = Map.of("status", "clean");

        assertTrue(extractor.evaluateCondition(map, "$.status != infected"));
        assertFalse(extractor.evaluateCondition(map, "$.status != clean"));
    }

    @Test
    void evaluateCondition_numericGreaterThan() {
        Map<String, Object> map = Map.of("score", 75);

        assertTrue(extractor.evaluateCondition(map, "$.score > 50"));
        assertFalse(extractor.evaluateCondition(map, "$.score > 100"));
    }

    @Test
    void evaluateCondition_numericLessThan() {
        Map<String, Object> map = Map.of("score", 25);

        assertTrue(extractor.evaluateCondition(map, "$.score < 50"));
        assertFalse(extractor.evaluateCondition(map, "$.score < 10"));
    }

    @Test
    void evaluateCondition_nestedPath() {
        Map<String, Object> map = Map.of("data", Map.of("detected", "true"));

        assertTrue(extractor.evaluateCondition(map, "$.data.detected == true"));
    }

    @Test
    void evaluateCondition_throwsForInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> extractor.evaluateCondition(Map.of(), "invalid"));
    }

    @Test
    void evaluateCondition_throwsForUnsupportedOperator() {
        assertThrows(IllegalArgumentException.class, () -> extractor.evaluateCondition(Map.of("x", 1), "$.x ~= 1"));
    }

    // === Expression evaluation tests ===

    @Test
    void evaluateExpression_returnsLiteralString() {
        var result = extractor.evaluateExpression(Map.of(), "'HIGH'");
        assertEquals("HIGH", result);
    }

    @Test
    void evaluateExpression_returnsPathValue() {
        Map<String, Object> map = Map.of("severity", "critical");

        var result = extractor.evaluateExpression(map, "$.severity");

        assertEquals("critical", result);
    }

    @Test
    void evaluateExpression_evaluatesTernary() {
        Map<String, Object> mapTrue = Map.of("detected", true);
        Map<String, Object> mapFalse = Map.of("detected", false);

        assertEquals("HIGH", extractor.evaluateExpression(mapTrue, "detected ? 'HIGH' : 'CLEAN'"));
        assertEquals("CLEAN", extractor.evaluateExpression(mapFalse, "detected ? 'HIGH' : 'CLEAN'"));
    }

    @Test
    void evaluateExpression_returnsNullForEmpty() {
        assertNull(extractor.evaluateExpression(Map.of(), ""));
        assertNull(extractor.evaluateExpression(Map.of(), null));
    }
}
