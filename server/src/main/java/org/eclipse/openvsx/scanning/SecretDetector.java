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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.re2j.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.openvsx.util.SizeLimitInputStream;

/**
 * Scans individual files for secrets using keyword routing and regex validation.
 */
class SecretDetector {

    @FunctionalInterface
    interface FindingRecorder {
        boolean record(@NonNull List<Finding> findings, @NonNull AtomicInteger count, @NonNull Finding finding);
    }

    private static final Logger logger = LoggerFactory.getLogger(SecretDetector.class);

    // Tika for content-based file type detection (thread-safe, reusable)
    private static final Tika tika = new Tika();

    private final AhoCorasick keywordMatcher;
    private final Map<String, List<SecretRule>> keywordToRules;
    private final List<SecretRule> rules;
    private final List<Pattern> globalAllowlistPatterns;
    private final List<Pattern> globalExcludedPathPatterns;
    private final AhoCorasick globalStopwordMatcher;
    private final AhoCorasick globalExcludedExtensionMatcher;
    private final AhoCorasick inlineSuppressionMatcher;
    private final List<Pattern> skipMimeTypePatterns;
    private final EntropyCalculator entropyCalculator;
    private final long maxFileSizeBytes;
    private final int maxLineLength;
    private final int timeoutCheckEveryNLines;
    private final int longLineNoSpaceThreshold;
    private final int keywordContextChars;
    private final int logAllowlistedPreviewLength;

    SecretDetector(
            @NonNull AhoCorasick keywordMatcher,
            @NonNull Map<String, List<SecretRule>> keywordToRules,
            @NonNull List<SecretRule> rules,
            @Nullable List<Pattern> allowlistPatterns,
            @Nullable List<Pattern> excludedPathPatterns,
            @Nullable AhoCorasick stopwordMatcher,
            @Nullable AhoCorasick excludedExtensionMatcher,
            @Nullable AhoCorasick inlineSuppressionMatcher,
            @Nullable List<Pattern> skipMimeTypePatterns,
            @NonNull EntropyCalculator entropyCalculator,
            long maxFileSizeBytes,
            int maxLineLength,
            int timeoutCheckEveryNLines,
            int longLineNoSpaceThreshold,
            int keywordContextChars,
            int logAllowlistedPreviewLength
    ) {
        this.keywordMatcher = keywordMatcher;
        this.keywordToRules = keywordToRules;
        this.rules = rules;
        this.globalAllowlistPatterns = allowlistPatterns != null ? allowlistPatterns : List.of();
        this.globalExcludedPathPatterns = excludedPathPatterns != null ? excludedPathPatterns : List.of();
        this.globalStopwordMatcher = stopwordMatcher;
        this.globalExcludedExtensionMatcher = excludedExtensionMatcher;
        this.inlineSuppressionMatcher = inlineSuppressionMatcher;
        this.skipMimeTypePatterns = skipMimeTypePatterns != null ? skipMimeTypePatterns : List.of();
        this.entropyCalculator = entropyCalculator;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxLineLength = maxLineLength;
        this.timeoutCheckEveryNLines = timeoutCheckEveryNLines;
        this.longLineNoSpaceThreshold = longLineNoSpaceThreshold;
        this.keywordContextChars = keywordContextChars;
        this.logAllowlistedPreviewLength = logAllowlistedPreviewLength;
    }

    boolean scanFile(
            @NonNull ZipFile zipFile,
            @NonNull ZipEntry entry,
            @NonNull List<SecretDetector.Finding> findings,
            long startTime,
            long timeoutMillis,
            @NonNull AtomicInteger findingsCount,
            @NonNull FindingRecorder recorder
    ) throws IOException {
        String filePath = entry.getName();

        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        if (entry.getSize() < 0 && entry.getCompressedSize() < 0) {
            return false;
        }

        if (entry.getSize() > maxFileSizeBytes) {
            return false;
        }

        // Check if the file type is excluded
        if (isExcludedFileType(filePath)) {
            return false;
        }

        // Check if the file is excluded by any of the global excluded patterns
        if (shouldExcludeFile(filePath)) {
            return false;
        }

        // Check if file should be skipped based on MIME type
        if (shouldExcludeByMimeType(zipFile, entry)) {
            return false;
        }

        // Read the file line by line with a hard byte limit
        try (
                InputStream zipStream = zipFile.getInputStream(entry);
                // Use a limited stream since the entry header may not correctly reflect the file size
                InputStream limitedStream = new SizeLimitInputStream(zipStream, maxFileSizeBytes);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(limitedStream, StandardCharsets.UTF_8))
        ) {

            int lineNumber = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                if (lineNumber % timeoutCheckEveryNLines == 0
                        && System.currentTimeMillis() - startTime > timeoutMillis) {
                    throw new SecretScanningTimeoutException("Secret detection timed out during file: " + filePath);
                }

                lineNumber++;

                // Check if the line is too long
                if (line.length() > maxLineLength) {
                    continue;
                }

                // Check if the line is likely to be a minified/bundled file
                if (line.length() > longLineNoSpaceThreshold && !StringUtils.containsWhitespace(line)) {
                    continue;
                }

                // Check if the line is suppressed by the publisher
                if (hasInlineSuppression(line)) {
                    continue;
                }

                scanLineWithKeywordMatching(
                        line,
                        line.toLowerCase(),
                        filePath,
                        lineNumber,
                        findings,
                        findingsCount,
                        recorder);
            }
        } catch (IOException e) {
            logger.error("Error reading file {}: {}", filePath, e.getMessage());
            throw e;
        }

        return true;
    }

    /**
     * Scan the line with keyword matching.
     * This is a performance optimization to skip the regex matching for lines that don't contain any keywords.
     */
    private void scanLineWithKeywordMatching(
            @NonNull String line,
            @NonNull String lowerLine,
            @NonNull String filePath,
            int lineNumber,
            @NonNull List<SecretDetector.Finding> findings,
            @NonNull AtomicInteger findingsCount,
            @NonNull FindingRecorder recorder
    ) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        List<AhoCorasick.Match> keywordMatches = keywordMatcher.search(lowerLine);

        Set<SecretRule> processedRules = new HashSet<>();

        for (AhoCorasick.Match match : keywordMatches) {
            String keyword = match.getKeyword();
            List<SecretRule> relevantRules = keywordToRules.get(keyword);

            if (relevantRules == null) {
                continue;
            }

            for (SecretRule rule : relevantRules) {
                if (processedRules.contains(rule)) {
                    continue;
                }
                processedRules.add(rule);

                int chunkStart = Math.max(0, match.getStartPos() - keywordContextChars);
                int chunkEnd = Math.min(line.length(), match.getEndPos() + keywordContextChars);
                String chunk = line.substring(chunkStart, chunkEnd);

                scanLineWithRule(rule, chunk, filePath, lineNumber, findings, findingsCount, recorder);
            }
        }

        for (SecretRule rule : rules) {
            if (rule.getKeywords().isEmpty() && !processedRules.contains(rule)) {
                scanLineWithRule(rule, line, filePath, lineNumber, findings, findingsCount, recorder);
            }
        }
    }

    /**
     * Scan the line with the given rule's regex pattern
     */
    private void scanLineWithRule(
            @NonNull SecretRule rule,
            @NonNull String chunk,
            @NonNull String filePath,
            int lineNumber,
            @NonNull List<SecretDetector.Finding> findings,
            @NonNull AtomicInteger findingsCount,
            @NonNull FindingRecorder recorder
    ) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        var matcher = rule.getPattern().matcher(chunk);

        while (matcher.find()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            // Extract the secret value from the matched group
            int groupIndex = matcher.groupCount() > 0 ? 1 : 0;
            Integer configuredGroup = rule.getSecretGroup();

            if (configuredGroup != null) {
                if (configuredGroup >= 0 && configuredGroup <= matcher.groupCount()) {
                    groupIndex = configuredGroup;
                }
            }

            String secretValue = matcher.group(groupIndex);

            if (secretValue == null) {
                continue;
            }

            // Check if the secret value is allowlisted by any of the rule's allowlist patterns
            if (!rule.getAllowlistPatterns().isEmpty()) {
                boolean allowlisted = false;
                for (Pattern allow : rule.getAllowlistPatterns()) {
                    if (allow.matcher(secretValue).find()) {
                        allowlisted = true;
                        break;
                    }
                }
                if (allowlisted) {
                    logger.debug(
                            "Skipping rule-allowlisted content in {}:{}: {}",
                            filePath,
                            lineNumber,
                            secretValue.substring(0, Math.min(logAllowlistedPreviewLength, secretValue.length())));
                    continue;
                }
            }

            // Check if the secret value is allowlisted by any of the global allowlist patterns
            if (isAllowlistedContent(secretValue)) {
                logger.debug(
                        "Skipping globally allowlisted content in {}:{}: {}",
                        filePath,
                        lineNumber,
                        secretValue.substring(0, Math.min(logAllowlistedPreviewLength, secretValue.length())));
                continue;
            }

            Double requiredEntropy = rule.getEntropy();
            double entropy = 0.0;

            // Check if the secret value is below the rule's required entropy threshold
            if (requiredEntropy != null) {
                entropy = entropyCalculator.calculate(secretValue);
                if (entropy < requiredEntropy) {
                    continue;
                }
            } else {
                entropy = entropyCalculator.calculate(secretValue);
            }

            logger.debug(
                    "Secret detected in {}:{} (rule: {}, entropy: {})",
                    filePath,
                    lineNumber,
                    rule.getId(),
                    entropy);

            var finding = new SecretDetector.Finding(filePath, lineNumber, entropy, secretValue, rule.getId());
            if (!recorder.record(findings, findingsCount, finding)) {
                return;
            }
        }
    }

    /**
     * Check if the line has any inline suppressions
     */
    private boolean hasInlineSuppression(@NonNull String line) {
        if (inlineSuppressionMatcher == null) {
            return false;
        }

        return !inlineSuppressionMatcher.search(line.toLowerCase()).isEmpty();
    }

    /**
     * Check if the file path is excluded by any of the global excluded path patterns
     */
    private boolean shouldExcludeFile(@NonNull String filePath) {
        for (Pattern pattern : globalExcludedPathPatterns) {
            if (pattern.matcher(filePath).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the file type is excluded by any of the global excluded file extensions
     */
    private boolean isExcludedFileType(@NonNull String filePath) {
        String lowerPath = filePath.toLowerCase();

        if (globalExcludedExtensionMatcher != null) {
            for (AhoCorasick.Match match : globalExcludedExtensionMatcher.search(lowerPath)) {
                if (match.getEndPos() == lowerPath.length()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if the secret value is allowlisted by any of the global allowlist patterns
     */
    private boolean isAllowlistedContent(@NonNull String secretValue) {
        // Stopwords are exact strings that should never be flagged as secrets
        if (globalStopwordMatcher != null && !globalStopwordMatcher.search(secretValue.toLowerCase()).isEmpty()) {
            return true;
        }

        // Allowlist patterns are regexes that may be case-sensitive, use original secretValue
        for (Pattern allowPattern : globalAllowlistPatterns) {
            if (allowPattern.matcher(secretValue).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if file should be skipped based on MIME type detection using Apache Tika.
     *
     * Uses Tika to detect MIME type from file content (magic bytes, structure, heuristics).
     * This is more reliable than extension-based detection for files without extensions
     * or with misleading extensions.
     *
     * Skip patterns are configured via {@code allowlist.skip-mime-types} in the YAML config.
     * Each pattern is a regex matched against the detected MIME type.
     *
     * @return true if file should be skipped, false if it should be scanned
     */
    private boolean shouldExcludeByMimeType(@NonNull ZipFile zipFile, @NonNull ZipEntry entry) {
        // If no skip patterns configured, don't skip any files based on MIME type
        if (skipMimeTypePatterns.isEmpty()) {
            return false;
        }

        try (
                InputStream is = zipFile.getInputStream(entry);
                BufferedInputStream bis = new BufferedInputStream(is)
        ) {

            // Tika.detect() reads only the bytes needed for detection
            String mimeType = tika.detect(bis, entry.getName());

            if (mimeType == null) {
                return false; // Unknown type, scan it to be safe
            }

            // Check against configured skip patterns (regex)
            for (Pattern pattern : skipMimeTypePatterns) {
                if (pattern.matcher(mimeType).find()) {
                    logger.debug(
                            "Skipping file (MIME {} matches pattern {}): {}",
                            mimeType,
                            pattern.pattern(),
                            entry.getName());
                    return true;
                }
            }

            return false;

        } catch (IOException e) {
            // If we can't detect type, don't skip - let the main scan handle errors
            logger.debug("Could not detect file type for {}: {}", entry.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Result of a secret scan. Immutable.
     */
    static final class Result {
        private final boolean secretsFound;
        private final boolean timedOut;
        private final @NonNull List<Finding> findings;

        private Result(boolean secretsFound, boolean timedOut, @NonNull List<Finding> findings) {
            this.secretsFound = secretsFound;
            this.timedOut = timedOut;
            this.findings = List.copyOf(findings);
        }

        public static Result secretsFound(@NonNull List<Finding> findings) {
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("Cannot create secretsFound result with empty findings");
            }
            return new Result(true, false, findings);
        }

        /**
         * Create a result for a scan that timed out with partial findings.
         * Only use this when there are actual findings to report.
         * If no findings were found before timeout, throw an exception instead.
         */
        public static Result timedOut(@NonNull List<Finding> partialFindings) {
            if (partialFindings.isEmpty()) {
                throw new IllegalArgumentException(
                        "Cannot create timedOut result with empty findings - throw exception instead");
            }
            return new Result(true, true, partialFindings);
        }

        public static Result noSecretsFound() {
            return new Result(false, false, List.of());
        }

        public static Result skipped() {
            return new Result(false, false, List.of());
        }

        public boolean isSecretsFound() {
            return secretsFound;
        }
        public boolean isTimedOut() {
            return timedOut;
        }
        public @NonNull List<Finding> getFindings() {
            return findings;
        }

        public @NonNull String getSummaryMessage() {
            String prefix = timedOut ? "(PARTIAL - timed out) " : "";
            if (!secretsFound) {
                return prefix + "No secrets detected";
            }
            return prefix + String.format(
                    "Found %d potential secret%s in extension package",
                    findings.size(),
                    findings.size() == 1 ? "" : "s");
        }
    }

    /**
     * A single secret finding. Secrets are redacted immediately.
     */
    static final class Finding {
        private final @NonNull String filePath;
        private final int lineNumber;
        private final double entropy;
        private final @NonNull String redactedSecret;
        private final @NonNull String ruleId;

        Finding(
                @NonNull String filePath,
                int lineNumber,
                double entropy,
                @Nullable String secretValue,
                @NonNull String ruleId
        ) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.entropy = entropy;
            this.redactedSecret = redactSecret(secretValue);
            this.ruleId = ruleId;
        }

        public @NonNull String getFilePath() {
            return filePath;
        }
        public int getLineNumber() {
            return lineNumber;
        }
        public double getEntropy() {
            return entropy;
        }
        public @NonNull String getSecretValue() {
            return redactedSecret;
        }
        public @NonNull String getRuleId() {
            return ruleId;
        }

        @Override
        public String toString() {
            return String.format(
                    "Potential secret found in %s:%d (rule: %s, entropy: %f): %s",
                    filePath,
                    lineNumber,
                    ruleId,
                    entropy,
                    redactedSecret);
        }

        private static @NonNull String redactSecret(@Nullable String secret) {
            if (secret == null || secret.length() <= 6) {
                return "***";
            }
            int prefixLen = Math.min(3, secret.length() / 3);
            int suffixLen = Math.min(3, secret.length() / 3);
            return secret.substring(0, prefixLen) + "***" + secret.substring(secret.length() - suffixLen);
        }
    }
}
