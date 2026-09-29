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

import java.lang.reflect.Field;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the lightweight wiring performed by {@link SecretDetectorFactory}.
 * These tests avoid the Spring context and keep assertions close to the wiring logic.
 */
class SecretDetectorFactoryTest {

    @Test
    void initialize_buildsEvenWhenDisabled() throws Exception {
        // Factory initializes if rule paths are configured, even when publishing-time scanning is disabled
        TrackingRuleLoader loader = new TrackingRuleLoader();
        SecretDetectorConfig config = buildConfig(false); // disabled
        setField(config, "rulesPath", "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml"); // Use test resource
        ExtensionScanConfig scanConfig = buildScanConfig();
        MockGitleaksRulesService gitleaksService = new MockGitleaksRulesService(null);
        SecretDetectorFactory factory = new SecretDetectorFactory(loader, config, scanConfig, gitleaksService);

        factory.initialize();

        assertTrue(loader.wasCalled, "Loader should run when rule paths are configured");
        assertNotNull(factory.getScanner(), "Scanner should be created for retroactive scan use cases");
        assertEquals(1, factory.getRules().size(), "Rules should be loaded");
        assertFalse(factory.getKeywordToRules().isEmpty(), "Keyword index should be built");
    }

    @Test
    void initialize_skipsWhenNoRulesConfigured() throws Exception {
        // Factory skips initialization when enabled but no rule paths are configured
        // Note: With @ConditionalOnProperty, factory is only created when enabled=true
        TrackingRuleLoader loader = new TrackingRuleLoader();
        SecretDetectorConfig config = buildConfig(true); // enabled but no rules paths
        ExtensionScanConfig scanConfig = buildScanConfig();
        MockGitleaksRulesService gitleaksService = new MockGitleaksRulesService(null); // no generated rules either
        SecretDetectorFactory factory = new SecretDetectorFactory(loader, config, scanConfig, gitleaksService);

        factory.initialize();

        assertFalse(loader.wasCalled, "Loader should not run when no rules are configured");
        assertNull(factory.getScanner(), "Scanner should not be created when no rules configured");
        assertTrue(factory.getRules().isEmpty(), "Rules should remain empty");
        assertTrue(factory.getKeywordToRules().isEmpty(), "Keyword index should remain empty");
    }

    @Test
    void initialize_buildsMatchersAndIndexes() throws Exception {
        TrackingRuleLoader loader = new TrackingRuleLoader();
        SecretDetectorConfig config = buildConfig(true);
        setField(
                config,
                "rulesPath",
                "classpath:org/eclipse/openvsx/scanning/secret-rules-a.yaml," +
                        "classpath:org/eclipse/openvsx/scanning/secret-rules-b.yaml");
        ExtensionScanConfig scanConfig = buildScanConfig();
        MockGitleaksRulesService gitleaksService = new MockGitleaksRulesService(null);

        SecretDetectorFactory factory = new SecretDetectorFactory(loader, config, scanConfig, gitleaksService);

        factory.initialize();

        assertTrue(loader.wasCalled, "Loader should run when scanning is enabled");
        assertNotNull(factory.getScanner(), "Scanner should be created from the loaded rules");
        assertEquals(2, factory.getRules().size(), "Expected merged rules from both YAML files");

        // Keyword index should include the lower-cased keyword from the override rule.
        assertTrue(factory.getKeywordToRules().containsKey("overridekey"));
        assertEquals("rule-a", factory.getKeywordToRules().get("overridekey").getFirst().getId());

        // The matcher should actually find the keyword in a sample string.
        List<AhoCorasick.Match> matches = factory.getKeywordMatcher().search("prefix overridekey suffix");
        assertFalse(matches.isEmpty(), "Keyword matcher should find at least one match");
        assertEquals("overridekey", matches.getFirst().getKeyword());
    }

    @Test
    void initialize_loadsGlobalAllowlistFromYaml() throws Exception {
        SecretRuleLoader loader = new SecretRuleLoader();
        SecretDetectorConfig config = buildConfig(true);
        setField(config, "rulesPath", "classpath:org/eclipse/openvsx/scanning/secret-rules-with-allowlist.yaml");
        ExtensionScanConfig scanConfig = buildScanConfig();
        MockGitleaksRulesService gitleaksService = new MockGitleaksRulesService(null);

        SecretDetectorFactory factory = new SecretDetectorFactory(loader, config, scanConfig, gitleaksService);
        factory.initialize();

        assertNotNull(factory.getScanner(), "Scanner should be created");
        assertEquals(1, factory.getRules().size());

        // Verify scanner was initialized (global allowlist is used internally)
        // We can't directly inspect the scanner's internal allowlist, but we can verify it was created
        assertNotNull(factory.getScanner());
    }

    private SecretDetectorConfig buildConfig(boolean enabled) throws Exception {
        // We set all the primitive fields explicitly so the factory can use getters safely.
        SecretDetectorConfig config = new SecretDetectorConfig();
        setField(config, "enabled", enabled);
        setField(config, "minifiedLineThreshold", 10_000);
        setField(config, "suppressionMarkers", "");
        setField(config, "timeoutSeconds", 5);
        setField(config, "maxFindings", 10);
        setField(config, "timeoutCheckInterval", 10);
        setField(config, "longLineNoSpaceThreshold", 80);
        setField(config, "regexContextChars", 10);
        setField(config, "debugPreviewChars", 4);
        return config;
    }

    private ExtensionScanConfig buildScanConfig() throws Exception {
        ExtensionScanConfig config = new ExtensionScanConfig();
        setField(config, "enabled", true);
        setField(config, "maxArchiveSizeBytes", 1024 * 1024L);
        setField(config, "maxSingleFileBytes", 1024 * 1024L);
        setField(config, "maxEntryCount", 100);
        return config;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TrackingRuleLoader extends SecretRuleLoader {
        boolean wasCalled = false;

        @Override
        public LoadedRules loadAll(@NonNull List<String> paths) {
            wasCalled = true;
            // Delegate to the real loader so the behavior stays realistic.
            return super.loadAll(paths);
        }
    }

    /**
     * Mock service for testing factory integration.
     */
    static class MockGitleaksRulesService extends GitleaksRulesService {
        private final String mockPath;

        MockGitleaksRulesService(String mockPath) {
            super(null, null, null); // Don't need real dependencies for mock
            this.mockPath = mockPath;
        }

        @Override
        public String getGeneratedRulesPath() {
            return mockPath;
        }
    }
}
