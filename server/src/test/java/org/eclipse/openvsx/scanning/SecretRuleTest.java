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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SecretRule} builder and rule matching.
 */
class SecretRuleTest {

    @Test
    void build_createsRuleWithRequiredFields() {
        var rule = new SecretRule.Builder()
                .id("test-rule")
                .regex("secret[0-9]+")
                .build();

        assertEquals("test-rule", rule.getId());
        assertNotNull(rule.getPattern());
        assertTrue(rule.getKeywords().isEmpty());
        assertTrue(rule.getAllowlistPatterns().isEmpty());
        assertNull(rule.getEntropy());
        assertNull(rule.getSecretGroup());
    }

    @Test
    void build_throwsWithoutId() {
        var builder = new SecretRule.Builder()
                .regex("pattern");

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void build_throwsWithoutRegex() {
        var builder = new SecretRule.Builder()
                .id("rule-id");

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void build_setsDescription() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .description("Test description")
                .build();

        assertEquals("Test description", rule.getDescription());
    }

    @Test
    void build_setsEntropy() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .entropy(3.5)
                .build();

        assertEquals(3.5, rule.getEntropy());
    }

    @Test
    void build_setsSecretGroup() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("prefix([a-z]+)suffix")
                .secretGroup(1)
                .build();

        assertEquals(1, rule.getSecretGroup());
    }

    @Test
    void build_convertsKeywordsToLowercase() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .keywords("API_KEY", "Secret", "TOKEN")
                .build();

        assertEquals(3, rule.getKeywords().size());
        assertTrue(rule.getKeywords().contains("api_key"));
        assertTrue(rule.getKeywords().contains("secret"));
        assertTrue(rule.getKeywords().contains("token"));
    }

    @Test
    void build_compilesAllowlistPatterns() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .allowlistRegexes(List.of("example\\.com", "test_value"))
                .build();

        assertEquals(2, rule.getAllowlistPatterns().size());
    }

    @Test
    void pattern_matchesCaseInsensitive() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("secret_key")
                .build();

        assertTrue(rule.getPattern().matcher("SECRET_KEY").find());
        assertTrue(rule.getPattern().matcher("Secret_Key").find());
        assertTrue(rule.getPattern().matcher("secret_key").find());
    }

    @Test
    void pattern_matchesExpectedContent() {
        var rule = new SecretRule.Builder()
                .id("github-pat")
                .regex("ghp_[a-zA-Z0-9]{36}")
                .build();

        assertTrue(rule.getPattern().matcher("ghp_1234567890abcdefghijklmnopqrstuvwxyz").find());
        assertFalse(rule.getPattern().matcher("ghp_short").find());
    }

    @Test
    void allowlistPatterns_matchCaseInsensitive() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .allowlistRegexes(List.of("EXAMPLE"))
                .build();

        var allowlistPattern = rule.getAllowlistPatterns().getFirst();
        assertTrue(allowlistPattern.matcher("example").find());
        assertTrue(allowlistPattern.matcher("EXAMPLE").find());
    }

    @Test
    void keywords_areImmutable() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .keywords("key1", "key2")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> rule.getKeywords().add("key3"));
    }

    @Test
    void allowlistPatterns_areImmutable() {
        var rule = new SecretRule.Builder()
                .id("test")
                .regex("pattern")
                .allowlistRegexes(List.of("allow"))
                .build();

        assertThrows(UnsupportedOperationException.class, () -> rule.getAllowlistPatterns().clear());
    }
}
