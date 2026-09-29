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
package org.eclipse.openvsx.search;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for extension similarity checking (name squatting protection).
 * <p>
 * Configuration example:
 * ovsx:
 *   similarity:
 *     enabled: true   # Enables/disables similarity checks during publishing.
 *     enforced: true  # Block publishing on similarity matches.
 *     only-check-new-extensions: false  # If true, only check the very first upload (no prior versions).
 *     similarity-threshold: 0.20  # Valid range: 0.0 - 0.3. Smaller values are stricter.
 *     skip-if-publisher-verified: false  # If true, skip checks for verified publishers.
 *     only-protect-verified-names: false  # If true, compare only against verified publishers' extensions.
 *     allow-similarity-to-own-names: true  # If true, exclude namespaces where the publisher is an owner.
 */
@Configuration
public class SimilarityConfig {
    /**
     * If enabled, run similarity checks during extension publishing.
     * <p>
     * Property: {@code ovsx.similarity.enabled}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.similarity.enabled:true}")
    private boolean enabled;

    /**
     * If enabled, only run similarity checks for the very first upload of an extension.
     * This means updates to an already existing extension (new versions) are not blocked by similarity.
     * <p>
     * Property: {@code ovsx.similarity.only-check-new-extensions}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.similarity.only-check-new-extensions:false}")
    private boolean onlyCheckNewExtensions;

    /**
     * Whether similarity failures are enforced (i.e. block publishing) when a similarity match is found.
     * <p>
     * Why this exists:
     * - We sometimes want to run the check and store the audit trail (scan + failures)
     *   without rejecting publication (monitor-only mode).
     * <p>
     * Default is true to preserve the historic behavior: when the similarity check is enabled,
     * it blocks publishing on matches unless explicitly configured otherwise.
     */
    @Value("${ovsx.scanning.similarity.enforced:true}")
    private boolean enforced;

    /**
     * Whether errors (exceptions) from this check should block publishing.
     * <p>
     * If true (default): an error during similarity checking will cause publishing to fail.
     * If false: errors are logged and recorded, but publishing continues.
     * <p>
     * Property: {@code ovsx.scanning.similarity.required}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.similarity.required:true}")
    private boolean required;

    /**
     * Threshold used to decide whether two extension identifiers are "too similar".
     * The check compares the edit distance against a fraction of the identifier length.
     * Larger values are stricter. For example {@code 0.2} requires at least ~20% difference.
     * <p>
     * Property: {@code ovsx.similarity.similarity-threshold}
     * Default: {@code 0.2}<br>
     * Valid range: {@code 0.0} - {@code 0.3} (validated at startup)
     */
    @Value("${ovsx.scanning.similarity.similarity-threshold:0.2}")
    private double similarityThreshold;

    /**
     * If enabled, do not run similarity checks for verified publishers.
     * This reduces friction for trusted publishers while keeping checks for unverified ones.
     * <p>
     * Property: {@code ovsx.similarity.skip-if-publisher-verified}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.similarity.skip-if-publisher-verified:false}")
    private boolean skipIfPublisherVerified;

    /**
     * If enabled, compare new extensions only against extensions from verified publishers.
     * This reduces noise by focusing on protecting well-known publishers.
     * <p>
     * Property: {@code ovsx.similarity.only-protect-verified-names}
     * Default: {@code false}
     */
    @Value("${ovsx.scanning.similarity.only-protect-verified-names:false}")
    private boolean onlyProtectVerifiedNames;

    /**
     * If enabled, exclude namespaces where the publishing user is a member (owner or contributor)
     * from similarity checks. This prevents false positives when a user uploads to namespaces
     * they legitimately have access to.
     * <p>
     * Property: {@code ovsx.similarity.allow-similarity-to-own-names}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.similarity.allow-similarity-to-own-names:true}")
    private boolean allowSimilarityToOwnNames;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOnlyCheckNewExtensions() {
        return onlyCheckNewExtensions;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public boolean isRequired() {
        return required;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public boolean isSkipIfPublisherVerified() {
        return skipIfPublisherVerified;
    }

    public boolean isOnlyProtectVerifiedNames() {
        return onlyProtectVerifiedNames;
    }

    public boolean isAllowSimilarityToOwnNames() {
        return allowSimilarityToOwnNames;
    }

    @PostConstruct
    public void validate() {
        if (similarityThreshold < 0.0 || similarityThreshold > 0.3) {
            throw new IllegalArgumentException(
                    "ovsx.similarity.similarity-threshold must be between 0.0 and 0.3, got: " + similarityThreshold);
        }
    }
}
