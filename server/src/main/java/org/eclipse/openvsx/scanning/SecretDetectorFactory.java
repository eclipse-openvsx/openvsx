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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.re2j.Pattern;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds and wires the secret-detection primitives once at startup.
 * <p>
 * The factory only initializes if rule paths are configured (via {@code rules-path})
 * or auto-generation is enabled (via {@code gitleaks.auto-fetch}).
 * If neither is configured, initialization is skipped, allowing the application
 * to start without requiring secret detection infrastructure.
 * <p>
 * If auto-generation is enabled, this depends on {@link GitleaksRulesService}
 * to ensure rules are generated before we try to load them.
 * Only loaded when secret detection is enabled via configuration.
 */
@Component
@ConditionalOnProperty(name = "ovsx.scanning.secret-detection.enabled", havingValue = "true")
public class SecretDetectorFactory {

    private static final Logger logger = LoggerFactory.getLogger(SecretDetectorFactory.class);

    private final SecretRuleLoader ruleLoader;
    private final SecretDetectorConfig config;
    private final ExtensionScanConfig scanConfig;
    private final GitleaksRulesService gitleaksService;

    private volatile List<SecretRule> rules = List.of();
    private volatile Map<String, List<SecretRule>> keywordToRules = Map.of();
    private volatile AhoCorasick keywordMatcher = AhoCorasick.builder().build();
    private volatile SecretDetector scanner;

    public SecretDetectorFactory(
            @NonNull SecretRuleLoader ruleLoader,
            @NonNull SecretDetectorConfig config,
            @NonNull ExtensionScanConfig scanConfig,
            @Nullable GitleaksRulesService gitleaksService
    ) {
        this.ruleLoader = ruleLoader;
        this.config = config;
        this.scanConfig = scanConfig;
        this.gitleaksService = gitleaksService;
    }

    @PostConstruct
    public void initialize() {
        // Build rules path list, prepending the generated file if auto-generation is enabled
        List<String> rulePaths = buildRulePaths();

        // Skip initialization if there are no rule paths to load
        if (rulePaths.isEmpty()) {
            logger.info("No secret detection rules configured; skipping scanner initialization");
            return;
        }

        // Load all rules from the rule paths
        SecretRuleLoader.LoadedRules loaded = ruleLoader.loadAll(rulePaths);
        List<SecretRule> loadedRules = loaded.rules();
        SecretRuleLoader.GlobalAllowlist globalAllowlist = loaded.globalAllowlist();

        // Build the keyword index for efficient keyword matching based on all loaded rules
        Set<String> allKeywords = new HashSet<>();
        Map<String, List<SecretRule>> keywordIndex = buildKeywordIndex(loadedRules, allKeywords);
        AhoCorasick builtKeywordMatcher = buildMatcher(allKeywords);

        // Build the global excluded path patterns for path matching based on the global allowlist
        List<Pattern> globalExcludedPathPatterns = getGlobalExcludedPathPatterns(globalAllowlist, config);
        List<Pattern> globalAllowlistPatterns = getGlobalAllowlistPatterns(globalAllowlist, config);
        List<String> globalStopwords = getGlobalStopwords(globalAllowlist, config);
        List<String> globalExcludedExtensions = getGlobalExcludedExtensions(globalAllowlist, config);
        List<Pattern> skipMimeTypePatterns = getSkipMimeTypePatterns(globalAllowlist);

        // Build the matchers for global stopwords, excluded extensions, and inline suppressions
        AhoCorasick globalStopwordMatcher = buildMatcher(globalStopwords);
        AhoCorasick globalExcludedExtensionMatcher = buildMatcher(globalExcludedExtensions);

        List<String> suppressionMarkersLower = getSuppressionMarkers(config);
        AhoCorasick suppressionMarkerMatcher = buildMatcher(suppressionMarkersLower);

        // Build the entropy calculator for entropy calculation
        EntropyCalculator entropyCalculator = new EntropyCalculator();

        this.rules = List.copyOf(loadedRules);
        this.keywordToRules = keywordIndex;
        this.keywordMatcher = builtKeywordMatcher != null ? builtKeywordMatcher : AhoCorasick.builder().build();
        this.scanner = new SecretDetector(
                this.keywordMatcher,
                this.keywordToRules,
                this.rules,
                globalAllowlistPatterns,
                globalExcludedPathPatterns,
                globalStopwordMatcher,
                globalExcludedExtensionMatcher,
                suppressionMarkerMatcher,
                skipMimeTypePatterns,
                entropyCalculator,
                scanConfig.getMaxSingleFileBytes(),
                config.getMinifiedLineThreshold(),
                config.getTimeoutCheckInterval(),
                config.getLongLineNoSpaceThreshold(),
                config.getRegexContextChars(),
                config.getDebugPreviewChars());

        logger.info(
                "Secret detection initialized: {} rules loaded, {} unique keywords indexed",
                this.rules.size(),
                this.keywordToRules.size());
    }

    /**
     * Reinitialize the scanner with fresh rules.
     *
     * This method is called by the scheduled refresh job after rules are updated.
     * It reloads all rules and rebuilds the scanner.
     *
     * Thread-safe: uses synchronized to prevent concurrent reinitialization.
     */
    public synchronized void reinitialize() {
        logger.info("Reinitializing secret scanner with fresh rules...");
        initialize();
    }

    @Nullable
    SecretDetector getScanner() {
        return scanner;
    }

    public @NonNull List<SecretRule> getRules() {
        return rules;
    }

    public @NonNull Map<String, List<SecretRule>> getKeywordToRules() {
        return keywordToRules;
    }

    public @NonNull AhoCorasick getKeywordMatcher() {
        return keywordMatcher;
    }

    private Map<String, List<SecretRule>> buildKeywordIndex(
            @NonNull List<SecretRule> sourceRules,
            @NonNull Set<String> allKeywords
    ) {
        Map<String, List<SecretRule>> index = new HashMap<>();

        for (SecretRule rule : sourceRules) {
            for (String keyword : rule.getKeywords()) {
                allKeywords.add(keyword);
                index.computeIfAbsent(keyword, k -> new ArrayList<>()).add(rule);
            }
        }

        logger.debug("Built keyword index with {} keywords from {} rules", allKeywords.size(), sourceRules.size());
        return index;
    }

    /**
     * Build the Aho-Corasick matcher for the given keywords
     */
    private AhoCorasick buildMatcher(java.util.Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        Set<String> normalized = new HashSet<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(trimmed.toLowerCase());
        }

        if (normalized.isEmpty()) {
            return null;
        }

        return AhoCorasick.builder().addKeywords(normalized).build();
    }

    /**
     * Get global excluded file extensions from YAML allowlist or fall back to config.
     * Combines extensions from the global allowlist in the YAML with extensions from config.
     */
    private List<String> getGlobalExcludedExtensions(
            SecretRuleLoader.@Nullable GlobalAllowlist globalAllowlist,
            @NonNull SecretDetectorConfig config
    ) {
        List<String> result = new ArrayList<>();

        if (globalAllowlist != null && globalAllowlist.fileExtensions != null) {
            result.addAll(
                    globalAllowlist.fileExtensions.stream()
                            .map(String::toLowerCase)
                            .toList());
        }

        return result;
    }

    /**
     * Get global excluded path patterns from YAML allowlist or fall back to config.
     * Paths are compiled as regex patterns for flexible matching.
     * Combines paths from the global allowlist in the YAML with paths from config.
     */
    private List<Pattern> getGlobalExcludedPathPatterns(
            SecretRuleLoader.@Nullable GlobalAllowlist globalAllowlist,
            @NonNull SecretDetectorConfig config
    ) {
        List<Pattern> result = new ArrayList<>();

        if (globalAllowlist != null && globalAllowlist.paths != null) {
            result.addAll(
                    globalAllowlist.paths.stream()
                            .map(path -> Pattern.compile(path, Pattern.CASE_INSENSITIVE))
                            .toList());
        }

        return result;
    }

    /**
     * Get global allowlist patterns from YAML or fall back to config.
     * Uses regex patterns from the global allowlist in the YAML, falling back to config.
     */
    private List<Pattern> getGlobalAllowlistPatterns(
            SecretRuleLoader.@Nullable GlobalAllowlist globalAllowlist,
            @NonNull SecretDetectorConfig config
    ) {
        List<Pattern> result = new ArrayList<>();

        if (globalAllowlist != null && globalAllowlist.regexes != null && !globalAllowlist.regexes.isEmpty()) {
            result.addAll(
                    globalAllowlist.regexes.stream()
                            .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
                            .toList());
        }

        return result;
    }

    /**
     * Get global stopwords from YAML or fall back to config.
     * Uses stopwords from the global allowlist in the YAML, falling back to config.
     */
    private List<String> getGlobalStopwords(
            SecretRuleLoader.@Nullable GlobalAllowlist globalAllowlist,
            @NonNull SecretDetectorConfig config
    ) {
        List<String> result = new ArrayList<>();

        if (globalAllowlist != null && globalAllowlist.stopwords != null) {
            result.addAll(
                    globalAllowlist.stopwords.stream()
                            .map(String::toLowerCase)
                            .toList());
        }

        return result;
    }

    /**
     * Get suppression markers from config.
     * Returns lowercase versions for case-insensitive matching.
     */
    private List<String> getSuppressionMarkers(@NonNull SecretDetectorConfig config) {
        return config.getSuppressionMarkers().stream()
                .map(String::toLowerCase)
                .toList();
    }

    /**
     * Get skip MIME type patterns from YAML allowlist.
     * Compiles regex patterns for MIME types that should be skipped during scanning.
     * Uses case-insensitive matching.
     */
    private List<Pattern> getSkipMimeTypePatterns(
            SecretRuleLoader.@Nullable GlobalAllowlist globalAllowlist
    ) {
        List<Pattern> result = new ArrayList<>();

        if (globalAllowlist != null && globalAllowlist.skipMimeTypes != null) {
            for (String pattern : globalAllowlist.skipMimeTypes) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                try {
                    result.add(Pattern.compile(pattern.trim(), Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    logger.warn("Invalid skip-mime-type regex pattern '{}': {}", pattern, e.getMessage());
                }
            }
        }

        logger.debug("Compiled {} skip-mime-type patterns", result.size());
        return result;
    }

    /**
     * Build the list of rule paths to load.
     * If auto-generation is enabled and succeeded, prepend the generated file path.
     * Append the configured paths from the application.yml.
     */
    private List<String> buildRulePaths() {
        List<String> paths = new ArrayList<>();

        // If auto-generation is enabled and the service bean exists, use the generated file
        if (gitleaksService != null) {
            String generatedPath = gitleaksService.getGeneratedRulesPath();
            if (generatedPath != null) {
                logger.debug("Using auto-generated rules file: {}", generatedPath);
                paths.add(generatedPath);
            }
        }

        // Add configured paths (these may include custom rules or override gitleaks rules)
        paths.addAll(config.getRulePaths());

        return paths;
    }
}
