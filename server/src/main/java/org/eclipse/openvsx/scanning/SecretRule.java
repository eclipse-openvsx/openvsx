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

import com.google.re2j.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Single secret rule definition plus builder.
 * <p>
 * A secret rule defines:
 * <p>
 *   A regex pattern to match potential secrets
 *   Optional keywords to filter lines before applying the regex (performance optimization)
 *   Optional entropy threshold to reduce false positives
 *   Optional allowlist patterns to exclude known safe matches
 *   Optional capture group specification to extract the actual secret value
 * <p>
 *
 * Rules are loaded from YAML files and compiled into efficient Pattern objects.
 */
public class SecretRule {
    /** Unique identifier for this rule (e.g., "github-pat", "aws-access-key") */
    private final @NonNull String id;

    /** Human-readable description of what this rule detects */
    private final @NonNull String description;

    /** Compiled regex pattern to match secrets (case-insensitive) */
    private final @NonNull Pattern pattern;

    /** Minimum Shannon entropy threshold (0.0-8.0). If set, matches below this are filtered out. */
    private final @Nullable Double entropy;

    /** Keywords that must appear in a line before applying the regex (lowercase, for performance) */
    private final @NonNull List<String> keywords;

    /** Compiled allowlist patterns to exclude known safe matches from this rule */
    private final @NonNull List<Pattern> allowlistPatterns;

    /** Optional capture group index to extract the secret (default: 1 if available, else 0) */
    private final @Nullable Integer secretGroup;

    private SecretRule(@NonNull Builder builder) {
        this.id = builder.id;
        this.description = builder.description;
        this.pattern = Pattern.compile(builder.regex, Pattern.CASE_INSENSITIVE);
        this.entropy = builder.entropy;
        this.secretGroup = builder.secretGroup;

        if (builder.keywords != null) {
            var lowerKeywords = new java.util.ArrayList<String>();
            for (String k : builder.keywords) {
                lowerKeywords.add(k.toLowerCase());
            }
            this.keywords = List.copyOf(lowerKeywords);
        } else {
            this.keywords = List.of();
        }
        if (builder.allowlistRegexes != null && !builder.allowlistRegexes.isEmpty()) {
            var list = new java.util.ArrayList<Pattern>();
            for (String regex : builder.allowlistRegexes) {
                list.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            }
            this.allowlistPatterns = List.copyOf(list);
        } else {
            this.allowlistPatterns = List.of();
        }
    }

    public @NonNull String getId() {
        return id;
    }

    public @NonNull String getDescription() {
        return description;
    }

    public @NonNull Pattern getPattern() {
        return pattern;
    }

    public @Nullable Double getEntropy() {
        return entropy;
    }

    public @NonNull List<String> getKeywords() {
        return keywords;
    }

    public @NonNull List<Pattern> getAllowlistPatterns() {
        return allowlistPatterns;
    }

    /**
     * Optional capture group index to extract the secret from.
     * When absent, the scanner falls back to group 1 when available, otherwise group 0.
     */
    public @Nullable Integer getSecretGroup() {
        return secretGroup;
    }

    /**
     * Builder for constructing SecretRule instances.
     */
    public static class Builder {
        private String id;
        private String description;
        private String regex;
        private Double entropy;
        private List<String> keywords;
        private List<String> allowlistRegexes;
        private Integer secretGroup;

        /**
         * Set the unique rule identifier.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Set the human-readable description.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the regex pattern to match secrets.
         * Pattern will be compiled as case-insensitive.
         */
        public Builder regex(String regex) {
            this.regex = regex;
            return this;
        }

        /**
         * Set the minimum Shannon entropy threshold.
         * Matches with entropy below this value are filtered out as likely false positives.
         */
        public Builder entropy(double entropy) {
            this.entropy = entropy;
            return this;
        }

        /**
         * Set keywords that must appear in a line before applying the regex.
         * This is a performance optimization - only lines containing at least one keyword
         * will have the regex applied. Keywords are case-insensitive.
         */
        public Builder keywords(String... keywords) {
            this.keywords = List.of(keywords);
            return this;
        }

        /**
         * Set allowlist regex patterns for this rule.
         * Matches that also match any allowlist pattern are excluded as known safe values.
         */
        public Builder allowlistRegexes(List<String> allowlistRegexes) {
            this.allowlistRegexes = allowlistRegexes;
            return this;
        }

        /**
         * Set the capture group index to extract the secret value.
         * If not set, defaults to group 1 if available, otherwise group 0 (full match).
         */
        public Builder secretGroup(Integer secretGroup) {
            this.secretGroup = secretGroup;
            return this;
        }

        /**
         * Build the SecretRule instance.
         */
        public SecretRule build() {
            if (id == null || regex == null) {
                throw new IllegalStateException("Rule must have id and regex");
            }
            return new SecretRule(this);
        }
    }
}
