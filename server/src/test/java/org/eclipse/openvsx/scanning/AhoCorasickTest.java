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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AhoCorasick} multi-pattern string matching.
 */
class AhoCorasickTest {

    @Test
    void search_findsSimpleKeyword() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        var matches = ac.search("this is a secret value");

        assertEquals(1, matches.size());
        assertEquals("secret", matches.getFirst().getKeyword());
        assertEquals(10, matches.getFirst().getStartPos());
        assertEquals(16, matches.getFirst().getEndPos());
    }

    @Test
    void search_findsMultipleKeywords() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("api", "key", "token"))
                .build();

        var matches = ac.search("api_key and token");

        assertEquals(3, matches.size());
        var keywords = matches.stream().map(AhoCorasick.Match::getKeyword).toList();
        assertTrue(keywords.contains("api"));
        assertTrue(keywords.contains("key"));
        assertTrue(keywords.contains("token"));
    }

    @Test
    void search_findsOverlappingPatterns() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("abc", "bc", "c"))
                .build();

        var matches = ac.search("abc");

        // All three patterns should match
        assertEquals(3, matches.size());
    }

    @Test
    void search_returnsEmptyForNoMatch() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("secret", "password"))
                .build();

        var matches = ac.search("nothing to find here");

        assertTrue(matches.isEmpty());
    }

    @Test
    void search_handlesCaseSensitivity() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        // Lowercase search should find lowercase pattern
        var matches = ac.search("secret");
        assertEquals(1, matches.size());

        // Uppercase text won't match lowercase pattern
        var noMatches = ac.search("SECRET");
        assertTrue(noMatches.isEmpty());
    }

    @Test
    void search_findsMultipleOccurrences() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("test"))
                .build();

        var matches = ac.search("test test test");

        assertEquals(3, matches.size());
        assertEquals(0, matches.get(0).getStartPos());
        assertEquals(5, matches.get(1).getStartPos());
        assertEquals(10, matches.get(2).getStartPos());
    }

    @Test
    void search_handlesEmptyText() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("secret"))
                .build();

        var matches = ac.search("");

        assertTrue(matches.isEmpty());
    }

    @Test
    void build_ignoresEmptyKeywords() {
        // Builder ignores empty/null keywords
        var keywords = new java.util.HashSet<String>();
        keywords.add("valid");
        keywords.add("");
        keywords.add(null);

        var ac = AhoCorasick.builder()
                .addKeywords(keywords)
                .build();

        var matches = ac.search("valid keyword");

        assertEquals(1, matches.size());
        assertEquals("valid", matches.getFirst().getKeyword());
    }

    @Test
    void search_findsKeywordAtStart() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("start"))
                .build();

        var matches = ac.search("start of text");

        assertEquals(1, matches.size());
        assertEquals(0, matches.getFirst().getStartPos());
    }

    @Test
    void search_findsKeywordAtEnd() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("end"))
                .build();

        var matches = ac.search("at the end");

        assertEquals(1, matches.size());
        assertEquals(7, matches.getFirst().getStartPos());
    }

    @Test
    void search_handlesSpecialCharacters() {
        var ac = AhoCorasick.builder()
                .addKeywords(Set.of("api_key", "auth-token"))
                .build();

        var matches = ac.search("use api_key or auth-token");

        assertEquals(2, matches.size());
    }

    @Test
    void match_toStringFormat() {
        var match = new AhoCorasick.Match("test", 5, 9);

        String str = match.toString();

        assertTrue(str.contains("test"));
        assertTrue(str.contains("5"));
        assertTrue(str.contains("9"));
    }

    @Test
    void builder_cannotBeReused() {
        var builder = AhoCorasick.builder().addKeyword("test");
        builder.build();

        assertThrows(IllegalStateException.class, () -> builder.addKeyword("another"));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void builder_addKeywordChains() {
        var ac = AhoCorasick.builder()
                .addKeyword("one")
                .addKeyword("two")
                .addKeyword("three")
                .build();

        var matches = ac.search("one two three");
        assertEquals(3, matches.size());
    }
}
