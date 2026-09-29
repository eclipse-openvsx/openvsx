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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for secret detection in extension packages.
 * <p>
 * Note: Archive limits (max-archive-size-bytes, max-single-file-bytes, max-entry-count)
 * are configured centrally in ExtensionScanConfig and shared by all scanning checks.
 */
@Configuration
public class SecretDetectorConfig {

    /**
     * Enables or disables secret detection for extension publishing.
     *
     * Property: {@code ovsx.secret-detection.enabled}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.secret-detection.enabled:false}")
    private boolean enabled;

    /**
     * Automatically fetch and generate secret detection rules from gitleaks.toml at startup.
     * <p>
     * When enabled, the application will download and convert the official gitleaks
     * configuration to YAML format at startup if the rules file does not exist.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.gitleaks.auto-fetch}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.secret-detection.gitleaks.auto-fetch:false}")
    private boolean gitleaksAutoFetch;

    /**
     * Force refresh of gitleaks rules even if they already exist.
     * <p>
     * Only has an effect when {@code gitleaks.auto-fetch} is enabled.
     * Use this to ensure rules are refreshed on every startup.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.gitleaks.force-refresh}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.secret-detection.gitleaks.force-refresh:false}")
    private boolean gitleaksForceRefresh;

    /**
     * Full path where the auto-generated gitleaks rules will be written.
     * <p>
     * This should be an absolute path to a writable location. The directory
     * will be created automatically if it doesn't exist.
     * <p>
     * Only used when {@code gitleaks.auto-fetch} is enabled.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.gitleaks.output-path}
     * Default: empty
     * Required when {@code gitleaks.auto-fetch} is true
     * Example: {@code /app/data/secret-detection-rules-gitleaks.yaml}
     */
    @Value("${ovsx.scanning.secret-detection.gitleaks.output-path:}")
    private String gitleaksOutputPath;

    /**
     * Enable scheduled refresh of gitleaks rules.
     * <p>
     * When enabled, the application will periodically download fresh rules
     * and reload the scanner. This ensures all pods stay up to date.
     * <p>
     * Only has an effect when {@code gitleaks.auto-fetch} is enabled.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.gitleaks.scheduled-refresh}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.secret-detection.gitleaks.scheduled-refresh:false}")
    private boolean gitleaksScheduledRefresh;

    /**
     * Cron expression for scheduled gitleaks rules refresh.
     * <p>
     * Only used when {@code gitleaks.scheduled-refresh} is enabled.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.gitleaks.refresh-cron}
     * Default: {@code 0 0 3 * * *} (daily at 3 AM)
     */
    @Value("${ovsx.scanning.secret-detection.gitleaks.refresh-cron:0 0 3 * * *}")
    private String gitleaksRefreshCron;

    /**
     * Comma-separated list of gitleaks rule IDs to skip when generating rules.
     * <p>
     * Some rules produce too many false positives. Use this to exclude them.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.gitleaks.skip-rule-ids}
     * Default: {@code generic-api-key}
     * Example: {@code generic-api-key,another-noisy-rule}
     */
    @Value("${ovsx.scanning.secret-detection.gitleaks.skip-rule-ids:generic-api-key}")
    private String gitleaksSkipRuleIds;

    /**
     * Whether secret scan findings are enforced (i.e. block publishing) when detected.
     * <p>
     * Why this exists:
     * - We sometimes want to run secret detection and record audit data,
     *   but not reject publication (monitor-only mode).
     * <p>
     * Default is true to preserve historic behavior: when secret detection is enabled,
     * findings will block publishing unless explicitly configured otherwise.
     */
    @Value("${ovsx.scanning.secret-detection.enforced:true}")
    private boolean enforced;

    /**
     * Whether errors (exceptions) from this check should block publishing.
     * <p>
     * If true (default): an error during secret scanning will cause publishing to fail.
     * If false: errors are logged and recorded, but publishing continues.
     * <p>
     * Use false for non-critical deployments where secret detection availability
     * issues (e.g., timeout, out of memory) shouldn't block all publishing.
     * <p>
     * Property: {@code ovsx.scanning.secret-detection.required}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.secret-detection.required:true}")
    private boolean required;

    /**
     * Maximum line length to process in characters. Files with longer lines are skipped.
     * Very long lines (>10K) typically indicate minified/bundled code and may cause performance issues.
     * <p>
     * Property: {@code ovsx.secret-detection.minified-line-threshold}
     * Default: {@code 10000}
     */
    @Value("${ovsx.scanning.secret-detection.minified-line-threshold:10000}")
    private int minifiedLineThreshold;

    /**
     * Comma-separated list of inline suppression markers that skip an entire line from scanning.
     * <p>
     * Property: {@code ovsx.secret-detection.suppression-markers}
     * Default: empty
     * Example: {@code "secret-scanner:ignore,gitleaks:allow"}
     */
    @Value("${ovsx.scanning.secret-detection.suppression-markers:}")
    private String suppressionMarkers;

    /**
     * Timeout for scanning in seconds. If scanning takes longer, it will be aborted.
     * <p>
     * Property: {@code ovsx.secret-detection.timeout-seconds}
     * Default: {@code 5}
     */
    @Value("${ovsx.scanning.secret-detection.timeout-seconds:5}")
    private int timeoutSeconds;

    /**
     * Maximum findings to collect before aborting to protect memory and UX.
     * <p>
     * Property: {@code ovsx.secret-detection.max-findings}
     * Default: {@code 200}
     */
    @Value("${ovsx.scanning.secret-detection.max-findings:200}")
    private int maxFindings;

    /**
     * Comma-separated YAML rule file paths. Later entries override earlier by rule id.
     * Supports {@code classpath:} prefix for classpath resources.
     * <p>
     * Property: {@code ovsx.secret-detection.rules-path}
     * Default: empty
     */
    @Value("${ovsx.scanning.secret-detection.rules-path:}")
    private String rulesPath;

    /**
     * How often (in lines) to check for scan timeout while reading files.
     * <p>
     * Property: {@code ovsx.secret-detection.timeout-check-interval}
     * Default: {@code 100}
     */
    @Value("${ovsx.scanning.secret-detection.timeout-check-interval:100}")
    private int timeoutCheckInterval;

    /**
     * Lines longer than this threshold without spaces are skipped to avoid minified blobs.
     * <p>
     * Property: {@code ovsx.secret-detection.long-line-no-space-threshold}
     * Default: {@code 1000}
     */
    @Value("${ovsx.scanning.secret-detection.long-line-no-space-threshold:1000}")
    private int longLineNoSpaceThreshold;

    /**
     * Characters of context around a keyword when applying regex matching.
     * <p>
     * Property: {@code ovsx.secret-detection.regex-context-chars}
     * Default: {@code 100}
     */
    @Value("${ovsx.scanning.secret-detection.regex-context-chars:100}")
    private int regexContextChars;

    /**
     * Characters to include when logging secret preview values (for debugging allowlisted secrets).
     * <p>
     * Property: {@code ovsx.secret-detection.debug-preview-chars}
     * Default: {@code 10}
     */
    @Value("${ovsx.scanning.secret-detection.debug-preview-chars:10}")
    private int debugPreviewChars;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public boolean isRequired() {
        return required;
    }

    public int getMinifiedLineThreshold() {
        return minifiedLineThreshold;
    }

    /**
     * Suppression markers that skip an entire line from scanning.
     */
    public @NonNull List<String> getSuppressionMarkers() {
        if (suppressionMarkers == null || suppressionMarkers.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(suppressionMarkers.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxFindings() {
        return maxFindings;
    }

    /**
     * Get YAML rule paths, supporting comma-separated inputs.
     * Empty input yields an empty list so callers can fail fast.
     */
    public @NonNull List<String> getRulePaths() {
        if (rulesPath == null || rulesPath.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(rulesPath.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public int getTimeoutCheckInterval() {
        return timeoutCheckInterval;
    }

    public int getLongLineNoSpaceThreshold() {
        return longLineNoSpaceThreshold;
    }

    public int getRegexContextChars() {
        return regexContextChars;
    }

    public int getDebugPreviewChars() {
        return debugPreviewChars;
    }

    public boolean isGitleaksAutoFetch() {
        return gitleaksAutoFetch;
    }

    public boolean isGitleaksForceRefresh() {
        return gitleaksForceRefresh;
    }

    public String getGitleaksOutputPath() {
        return gitleaksOutputPath;
    }

    public boolean isGitleaksScheduledRefresh() {
        return gitleaksScheduledRefresh;
    }

    public String getGitleaksRefreshCron() {
        return gitleaksRefreshCron;
    }

    /**
     * Get gitleaks rule IDs to skip when generating rules.
     * Returns a set for efficient lookup.
     */
    public @NonNull Set<String> getGitleaksSkipRuleIds() {
        if (gitleaksSkipRuleIds == null || gitleaksSkipRuleIds.trim().isEmpty()) {
            return java.util.Set.of();
        }
        return Arrays.stream(gitleaksSkipRuleIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    @PostConstruct
    public void validate() {
        if (minifiedLineThreshold <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.minified-line-threshold must be positive, got: " + minifiedLineThreshold);
        }

        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.timeout-seconds must be positive, got: " + timeoutSeconds);
        }

        if (maxFindings <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.max-findings must be positive, got: " + maxFindings);
        }

        if (timeoutCheckInterval <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.timeout-check-interval must be positive, got: " + timeoutCheckInterval);
        }

        if (longLineNoSpaceThreshold <= 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.long-line-no-space-threshold must be positive, got: "
                            + longLineNoSpaceThreshold);
        }

        if (regexContextChars < 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.regex-context-chars must be >= 0, got: " + regexContextChars);
        }

        if (debugPreviewChars < 0) {
            throw new IllegalArgumentException(
                    "ovsx.secret-detection.debug-preview-chars must be >= 0, got: " + debugPreviewChars);
        }
    }
}
