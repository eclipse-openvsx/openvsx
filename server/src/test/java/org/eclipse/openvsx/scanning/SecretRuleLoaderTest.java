/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.scanning;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for {@link SecretRuleLoader}.
 * These tests keep the loader behavior well-specified without touching Spring wiring.
 */
class SecretRuleLoaderTest {

    private final SecretRuleLoader loader = new SecretRuleLoader();

    @Test
    void loadAll_overridesRulesByIdAndPreservesOrder() {
        // Use two YAML files where the second overrides rule-a and adds rule-b.
        List<SecretRule> rules = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml",
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-b.yaml"))
                .rules();

        assertEquals(2, rules.size());

        SecretRule first = rules.get(0);
        SecretRule second = rules.get(1);

        assertEquals("rule-a", first.getId());
        assertEquals("override[0-9]+", first.getPattern().pattern());
        assertEquals(List.of("overridekey"), first.getKeywords());
        // Allowlist should be replaced when overriding the rule.
        assertTrue(first.getAllowlistPatterns().isEmpty());

        assertEquals("rule-b", second.getId());
        assertEquals(List.of("key"), second.getKeywords());
    }

    @Test
    void loadAll_returnsEmptyWhenPathsEmpty() {
        // Empty path lists should return empty result with a warning (not throw)
        SecretRuleLoader.LoadedRules result = loader.loadAll(List.of());
        assertTrue(result.rules().isEmpty(), "Should return empty rules list");
        assertNull(result.globalAllowlist(), "Should return null global allowlist");
    }

    @Test
    void loadSingle_throwsOnBlankPath() {
        // A blank path is invalid and should surface as an exception, not silent fallback.
        assertThrows(IllegalStateException.class, () -> loader.load("   "));
    }

    @Test
    void loadSingle_parsesAllowlistsKeywordsAndSecretGroup() {
        // The sample YAML contains allowlists, entropy, keywords, and a secret group.
        List<SecretRule> rules = loader.load("classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml");

        assertEquals(1, rules.size());
        SecretRule rule = rules.getFirst();

        assertEquals(2, rule.getAllowlistPatterns().size());
        assertEquals("test", rule.getAllowlistPatterns().getFirst().pattern());
        assertEquals(1, rule.getSecretGroup());
        assertEquals(3.5, rule.getEntropy());
        assertEquals(List.of("token", "shared"), rule.getKeywords());
    }

    @Test
    void loadAll_parsesGlobalAllowlistPaths() {
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml"));

        SecretRuleLoader.GlobalAllowlist allowlist = Objects.requireNonNull(result.globalAllowlist());
        assertNotNull(allowlist.paths);
        assertEquals(3, allowlist.paths.size());
        assertTrue(allowlist.paths.contains("node_modules/"));
        assertTrue(allowlist.paths.contains(".git/"));
        assertTrue(allowlist.paths.contains("test/"));
    }

    @Test
    void loadAll_parsesGlobalAllowlistRegexes() {
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml"));

        SecretRuleLoader.GlobalAllowlist allowlist = Objects.requireNonNull(result.globalAllowlist());
        assertNotNull(allowlist.regexes);
        assertEquals(3, allowlist.regexes.size());
        assertTrue(allowlist.regexes.contains("^test$"));
        assertTrue(allowlist.regexes.contains("^example$"));
        assertTrue(allowlist.regexes.contains(".+\\.min\\.js$"));
    }

    @Test
    void loadAll_parsesGlobalAllowlistStopwords() {
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml"));

        SecretRuleLoader.GlobalAllowlist allowlist = Objects.requireNonNull(result.globalAllowlist());
        assertNotNull(allowlist.stopwords);
        assertEquals(3, allowlist.stopwords.size());
        assertTrue(allowlist.stopwords.contains("example"));
        assertTrue(allowlist.stopwords.contains("test"));
        assertTrue(allowlist.stopwords.contains("dummy"));
    }

    @Test
    void loadAll_parsesGlobalAllowlistFileExtensions() {
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml"));

        SecretRuleLoader.GlobalAllowlist allowlist = Objects.requireNonNull(result.globalAllowlist());
        assertNotNull(allowlist.fileExtensions);
        assertEquals(3, allowlist.fileExtensions.size());
        assertTrue(allowlist.fileExtensions.contains(".png"));
        assertTrue(allowlist.fileExtensions.contains(".jpg"));
        assertTrue(allowlist.fileExtensions.contains(".zip"));
    }

    @Test
    void loadAll_handlesYamlWithoutGlobalAllowlist() {
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml"));

        // Should return null when no global allowlist is present
        assertNull(result.globalAllowlist());
        // But rules should still be loaded
        assertEquals(1, result.rules().size());
    }

    @Test
    void loadAll_mergesGlobalAllowlistsFromAllFiles() {
        // When loading multiple files, all global allowlists should be merged
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml", // has allowlist
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-allowlist-2.yaml" // has different allowlist
                ));

        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);

        // Paths should be merged (3 from first file + 2 from second = 5)
        assertNotNull(allowlist.paths);
        assertEquals(5, allowlist.paths.size());
        assertTrue(allowlist.paths.contains("node_modules/"));
        assertTrue(allowlist.paths.contains(".git/"));
        assertTrue(allowlist.paths.contains("test/"));
        assertTrue(allowlist.paths.contains("vendor/"));
        assertTrue(allowlist.paths.contains("build/"));

        // Regexes should be merged (3 + 2 = 5)
        assertNotNull(allowlist.regexes);
        assertEquals(5, allowlist.regexes.size());
        assertTrue(allowlist.regexes.contains("^test$"));
        assertTrue(allowlist.regexes.contains("^example$"));
        assertTrue(allowlist.regexes.contains("^placeholder$"));
        assertTrue(allowlist.regexes.contains("^dummy$"));

        // Stopwords should be merged (3 + 2 = 5)
        assertNotNull(allowlist.stopwords);
        assertEquals(5, allowlist.stopwords.size());
        assertTrue(allowlist.stopwords.contains("example"));
        assertTrue(allowlist.stopwords.contains("test"));
        assertTrue(allowlist.stopwords.contains("dummy"));
        assertTrue(allowlist.stopwords.contains("fake"));
        assertTrue(allowlist.stopwords.contains("mock"));

        // File extensions should be merged (3 + 2 = 5)
        assertNotNull(allowlist.fileExtensions);
        assertEquals(5, allowlist.fileExtensions.size());
        assertTrue(allowlist.fileExtensions.contains(".png"));
        assertTrue(allowlist.fileExtensions.contains(".jpg"));
        assertTrue(allowlist.fileExtensions.contains(".zip"));
        assertTrue(allowlist.fileExtensions.contains(".svg"));
        assertTrue(allowlist.fileExtensions.contains(".gif"));

        // Both rules should be present (merged)
        assertEquals(2, result.rules().size());
    }

    @Test
    void loadAll_returnsRulesAndGlobalAllowlistTogether() {
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml"));

        // Verify both components are present
        assertNotNull(result.rules());
        assertEquals(1, result.rules().size());
        assertEquals("test-rule-1", result.rules().getFirst().getId());

        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);
        assertNotNull(allowlist.paths);
        assertNotNull(allowlist.regexes);
        assertNotNull(allowlist.stopwords);
        assertNotNull(allowlist.fileExtensions);
    }

    @Test
    void loadAll_mergesPartialAllowlists() {
        // Test merging when some files have only partial allowlist fields
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml", // full allowlist
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-allowlist-3.yaml" // partial (only paths and stopwords)
                ));

        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);

        // Paths: 3 from first + 1 from second = 4
        assertEquals(4, allowlist.paths.size());
        assertTrue(allowlist.paths.contains("dist/"));

        // Regexes: 3 from first + 0 from second = 3
        assertEquals(3, allowlist.regexes.size());

        // Stopwords: 3 from first + 1 from second = 4
        assertEquals(4, allowlist.stopwords.size());
        assertTrue(allowlist.stopwords.contains("sample"));

        // File extensions: 3 from first + 0 from second = 3
        assertEquals(3, allowlist.fileExtensions.size());

        // Rules should be merged (1 + 1 = 2)
        assertEquals(2, result.rules().size());
    }

    @Test
    void loadAll_mergesAllowlistWhenFirstFileHasNone() {
        // Test that allowlist from second file is used when first has none
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml", // no allowlist
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml" // has allowlist
                ));

        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);
        assertEquals(3, allowlist.paths.size());
        assertEquals(3, allowlist.regexes.size());
        assertEquals(3, allowlist.stopwords.size());
        assertEquals(3, allowlist.fileExtensions.size());

        // Both rules should be present
        assertEquals(2, result.rules().size());
    }

    @Test
    void loadAll_mergesThreeFilesWithMixedAllowlists() {
        // Test merging three files with different allowlist configurations
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml", // full
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml", // no allowlist
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-allowlist-2.yaml" // full
                ));

        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);

        // Should have items from file 1 and file 3 (file 2 has no allowlist)
        assertEquals(5, allowlist.paths.size()); // 3 + 2
        assertEquals(5, allowlist.regexes.size()); // 3 + 2
        assertEquals(5, allowlist.stopwords.size()); // 3 + 2
        assertEquals(5, allowlist.fileExtensions.size()); // 3 + 2

        // All three rules should be present, but rule-a is overridden by file 2
        assertEquals(3, result.rules().size());

        // Verify rule-a was overridden (should have the description from file A, not the original)
        SecretRule ruleA = result.rules().stream()
                .filter(r -> r.getId().equals("rule-a"))
                .findFirst()
                .orElseThrow();

        // File 2 (secret-rules-a.yaml) is loaded second, so its version should win
        assertEquals("a[0-9]{3}", ruleA.getPattern().pattern());
        assertEquals(List.of("token", "shared"), ruleA.getKeywords());
    }

    @Test
    void loadAll_overridesRulesButMergesAllowlists() {
        // Verify that rules are overridden (last wins) but allowlists are merged
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml", // test-rule-1
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-allowlist-2.yaml", // test-rule-2
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-allowlist-3.yaml" // test-rule-3
                ));

        // All three unique rules should be present
        assertEquals(3, result.rules().size());

        List<String> ruleIds = result.rules().stream()
                .map(SecretRule::getId)
                .sorted()
                .toList();
        assertEquals(List.of("test-rule-1", "test-rule-2", "test-rule-3"), ruleIds);

        // All allowlists should be merged
        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);

        // Paths: 3 + 2 + 1 = 6
        assertEquals(6, allowlist.paths.size());

        // Regexes: 3 + 2 + 0 = 5
        assertEquals(5, allowlist.regexes.size());

        // Stopwords: 3 + 2 + 1 = 6
        assertEquals(6, allowlist.stopwords.size());

        // File extensions: 3 + 2 + 0 = 5
        assertEquals(5, allowlist.fileExtensions.size());
    }

    @Test
    void loadAll_allowsDuplicateAllowlistItems() {
        // If multiple files specify the same allowlist item, it should appear multiple times
        // (caller can deduplicate if needed, but loader preserves all)
        SecretRuleLoader.LoadedRules result = loader.loadAll(
                List.of(
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml",
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml" // same file twice
                ));

        SecretRuleLoader.GlobalAllowlist allowlist = result.globalAllowlist();
        assertNotNull(allowlist);

        // Items should be duplicated (3 + 3 = 6)
        assertEquals(6, allowlist.paths.size());
        assertEquals(6, allowlist.regexes.size());
        assertEquals(6, allowlist.stopwords.size());
        assertEquals(6, allowlist.fileExtensions.size());

        // But rules should not be duplicated (same ID)
        assertEquals(1, result.rules().size());
    }
}
