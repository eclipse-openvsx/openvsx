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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extracts data from HTTP responses using JSONPath-style paths.
 */
@Component
public class HttpResponseExtractor {

    private final JsonMapper jsonMapper;

    public HttpResponseExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Extract a single string value from a response.
     */
    public String extractString(String response, String format, String path) throws ScannerException {
        if (response == null || path == null) {
            return null;
        }

        return switch (format.toLowerCase()) {
            case "json" -> extractJsonString(response, path);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    /**
     * Extract a boolean value from a response. Accepts JSON booleans and string forms ("true"/"false"). Returns null
     * when the path is missing or the value is not a boolean.
     */
    public Boolean extractBoolean(String response, String format, String path) throws ScannerException {
        if (response == null || path == null) {
            return null;
        }

        return switch (format.toLowerCase()) {
            case "json" -> extractJsonBoolean(response, path);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    /**
     * Extract a list of objects from a response.
     */
    public List<Map<String, Object>> extractList(String response, String format, String path) throws ScannerException {
        if (response == null || path == null) {
            return new ArrayList<>();
        }

        return switch (format.toLowerCase()) {
            case "json" -> extractJsonList(response, path);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    private JsonNode parseJson(String json) throws ScannerException {
        try {
            return jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ScannerException("Failed to parse JSON response", e);
        }
    }

    private String extractJsonString(String json, String jsonPath) throws ScannerException {
        JsonNode node = pickFirst(parseJson(json), jsonPath);
        return node != null && !node.isMissingNode() ? node.asString(null) : null;
    }

    private Boolean extractJsonBoolean(String json, String jsonPath) throws ScannerException {
        JsonNode node = pickFirst(parseJson(json), jsonPath);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isString()) {
            String value = node.asString(null);
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractJsonList(String json, String jsonPath) throws ScannerException {
        JsonNode root = parseJson(json);
        List<JsonNode> nodes = evaluateJsonPath(root, jsonPath);

        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (node.isObject()) {
                Map<String, Object> map = jsonMapper.convertValue(
                        node,
                        new TypeReference<Map<String, Object>>() {
                        });
                result.add(map);
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    if (child.isObject()) {
                        Map<String, Object> map = jsonMapper.convertValue(
                                child,
                                new TypeReference<Map<String, Object>>() {
                                });
                        result.add(map);
                    } else {
                        result.add(Map.of("value", child.asString(null)));
                    }
                }
            } else {
                result.add(Map.of("value", node.asString(null)));
            }
        }

        return result;
    }

    private JsonNode pickFirst(JsonNode root, String jsonPath) {
        List<JsonNode> nodes = evaluateJsonPath(root, jsonPath);
        return nodes.isEmpty() ? null : nodes.getFirst();
    }

    private List<JsonNode> evaluateJsonPath(JsonNode node, String jsonPath) {
        List<JsonNode> current = List.of(node);
        String path = jsonPath.trim();

        if (path.equals("$") || path.equals("/") || path.isEmpty()) {
            return current;
        }

        if (path.startsWith("$.")) {
            path = path.substring(2);
        }

        String[] segments = path.split("\\.");
        for (String segment : segments) {
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode candidate : current) {
                next.addAll(descend(candidate, segment));
            }
            current = next;
        }

        return current;
    }

    private List<JsonNode> descend(JsonNode node, String segment) {
        List<JsonNode> result = new ArrayList<>();

        // Handle standalone wildcard
        if (segment.equals("*")) {
            if (node.isArray()) {
                node.forEach(result::add);
            } else if (node.isObject()) {
                node.properties().iterator().forEachRemaining(entry -> result.add(entry.getValue()));
            }
            return result;
        }

        // Handle array notation: "field[*]" or "field[0]"
        if (segment.contains("[")) {
            String key = segment.substring(0, segment.indexOf('['));
            String indexPart = segment.substring(segment.indexOf('[') + 1, segment.indexOf(']'));
            JsonNode next = node.get(key);

            if (next != null && next.isArray()) {
                // Handle wildcard [*] - return all array elements
                if (indexPart.equals("*")) {
                    next.forEach(result::add);
                } else {
                    // Handle numeric index [0], [1], etc.
                    try {
                        int index = Integer.parseInt(indexPart);
                        JsonNode indexed = next.get(index);
                        if (indexed != null) {
                            result.add(indexed);
                        }
                    } catch (NumberFormatException ignored) {
                        // Invalid index format, skip
                    }
                }
            }
            return result;
        }

        JsonNode next = node.get(segment);
        if (next != null) {
            result.add(next);
        }

        return result;
    }

    /**
     * Evaluate a simple condition expression.
     *
     * Supports:
     * - Literal "true" or "false"
     * - Basic comparisons: "$.detected == true", "$.status != 'clean'", "$.score > 5"
     */
    public boolean evaluateCondition(Map<String, Object> object, String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return true; // No condition = always true
        }

        String trimmed = condition.trim().toLowerCase();

        // Handle literal true/false
        if (trimmed.equals("true")) {
            return true;
        }
        if (trimmed.equals("false")) {
            return false;
        }

        // Simple condition parsing
        // Format: "$.path operator value"
        String[] parts = condition.split("\\s+");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid condition format: " + condition);
        }

        String path = parts[0];
        String operator = parts[1];
        String expectedValue = parts[2].replace("'", "").replace("\"", "");

        // Extract actual value from object
        Object actualValue = extractValueFromMap(object, path);

        // Compare based on operator
        return switch (operator) {
            case "==", "=" -> String.valueOf(actualValue).equals(expectedValue);
            case "!=" -> !String.valueOf(actualValue).equals(expectedValue);
            case ">" -> compareNumeric(actualValue, expectedValue) > 0;
            case "<" -> compareNumeric(actualValue, expectedValue) < 0;
            case ">=" -> compareNumeric(actualValue, expectedValue) >= 0;
            case "<=" -> compareNumeric(actualValue, expectedValue) <= 0;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    /**
     * Extract a value from a map using a simple path (e.g., "$.detected.detail").
     */
    private Object extractValueFromMap(Map<String, Object> map, String path) {
        if (map == null || path == null) {
            return null;
        }

        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        if (normalized.isEmpty()) {
            return null;
        }

        String[] components = normalized.split("\\.");
        Object current = map;
        for (String component : components) {
            if (!(current instanceof Map<?, ?> nested)) {
                return null;
            }
            current = nested.get(component);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    /**
     * Compare two values numerically.
     */
    private int compareNumeric(Object actual, String expected) {
        try {
            double actualNum = Double.parseDouble(String.valueOf(actual));
            double expectedNum = Double.parseDouble(expected);
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot compare non-numeric values: " + actual + " vs " + expected);
        }
    }

    /**
     * Evaluate a simple expression to compute a value.
     *
     * Supports:
     * - Ternary: "detected ? 'HIGH' : 'CLEAN'"
     * - Direct value: "'MEDIUM'"
     * - Path reference: "$.severity"
     */
    public String evaluateExpression(Map<String, Object> object, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        // Check for ternary expression: "condition ? trueValue : falseValue"
        if (expression.contains("?") && expression.contains(":")) {
            String[] parts = expression.split("\\?");
            String condition = parts[0].trim();
            String[] values = parts[1].split(":");
            String trueValue = values[0].trim().replace("'", "").replace("\"", "");
            String falseValue = values[1].trim().replace("'", "").replace("\"", "");

            // Evaluate condition (simple field check)
            Object fieldValue = extractValueFromMap(object, "$." + condition);
            boolean conditionMet = fieldValue != null &&
                    (fieldValue.equals(true) || fieldValue.equals("true"));

            return conditionMet ? trueValue : falseValue;
        }

        // Check for direct value (quoted string)
        if (expression.startsWith("'") || expression.startsWith("\"")) {
            return expression.replace("'", "").replace("\"", "");
        }

        // Check for path reference
        if (expression.startsWith("$.")) {
            Object value = extractValueFromMap(object, expression);
            return value != null ? value.toString() : null;
        }

        // Default: return as-is
        return expression;
    }
}
