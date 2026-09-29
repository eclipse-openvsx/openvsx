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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpTemplateEngine} placeholder substitution.
 */
class HttpTemplateEngineTest {

    private final HttpTemplateEngine engine = new HttpTemplateEngine();

    @Test
    void process_substitutesSinglePlaceholder() {
        String template = "https://api.example.com/jobs/{jobId}";
        var placeholders = Map.of("jobId", "12345");

        String result = engine.process(template, placeholders);

        assertEquals("https://api.example.com/jobs/12345", result);
    }

    @Test
    void process_substitutesMultiplePlaceholders() {
        String template = "{protocol}://{host}/api/{version}";
        var placeholders = Map.of(
                "protocol",
                "https",
                "host",
                "api.example.com",
                "version",
                "v2");

        String result = engine.process(template, placeholders);

        assertEquals("https://api.example.com/api/v2", result);
    }

    @Test
    void process_returnsNullForNullTemplate() {
        assertNull(engine.process(null, Map.of("key", "value")));
    }

    @Test
    void process_returnsTemplateForNullPlaceholders() {
        String template = "no placeholders";
        assertEquals(template, engine.process(template, null));
    }

    @Test
    void process_returnsTemplateForEmptyPlaceholders() {
        String template = "no placeholders";
        assertEquals(template, engine.process(template, Map.of()));
    }

    @Test
    void process_throwsForMissingPlaceholder() {
        String template = "value is {missing}";
        var placeholders = Map.of("other", "value");

        assertThrows(IllegalArgumentException.class, () -> engine.process(template, placeholders));
    }

    @Test
    void process_handlesSpecialCharactersInValue() {
        String template = "query={query}";
        var placeholders = Map.of("query", "a+b=c&d");

        String result = engine.process(template, placeholders);

        assertEquals("query=a+b=c&d", result);
    }

    @Test
    void process_noArgsReturnsAsIs() {
        String template = "static text";
        assertEquals(template, engine.process(template));
    }

    @Test
    void processMap_substitutesAllValues() {
        var map = Map.of(
                "Authorization",
                "Bearer {token}",
                "X-Job-Id",
                "{jobId}");
        var placeholders = Map.of("token", "abc123", "jobId", "999");

        var result = engine.processMap(map, placeholders);

        assertEquals("Bearer abc123", result.get("Authorization"));
        assertEquals("999", result.get("X-Job-Id"));
    }

    @Test
    void processMap_returnsEmptyForNullMap() {
        var result = engine.processMap(null, Map.of("key", "value"));
        assertTrue(result.isEmpty());
    }

    @Test
    void process_handlesAdjacentPlaceholders() {
        String template = "{first}{second}";
        var placeholders = Map.of("first", "hello", "second", "world");

        String result = engine.process(template, placeholders);

        assertEquals("helloworld", result);
    }

    @Test
    void process_preservesTextWithoutPlaceholders() {
        String template = "plain text without any placeholders";
        var placeholders = Map.of("unused", "value");

        String result = engine.process(template, placeholders);

        assertEquals(template, result);
    }
}
