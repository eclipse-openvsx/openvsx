/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of the NAME_SIMILARITY publish check, which flags a version whose extension name or
 * display name matches or resembles another namespace's active extension, and of the name squatting
 * admin dashboard that looks those flags up again.
 * <p>
 * The check never blocks publication, whether it finds a match or fails: it only flags the version for
 * an admin to review. Like every publish check it runs only while {@code ovsx.scanning.enabled} is set.
 * <p>
 * Configuration example:
 * <pre>
 * ovsx:
 *   scanning:
 *     name-similarity:
 *       enabled: true               # default: true
 *       name-threshold: 0.7         # trigram similarity of extension names, default: 0.7
 *       display-name-threshold: 0.7 # trigram similarity of display names, default: 0.7
 *       min-fuzzy-length: 4         # shorter names only match exactly, default: 4
 *       max-matches: 10             # matches looked up per field and name, default: 10
 *       statement-timeout: 10s      # cancels a lookup that runs longer, default: 10s
 * </pre>
 */
@Configuration
public class NameSimilarityCheckConfig {

    /**
     * Property: {@code ovsx.scanning.name-similarity.enabled}
     * Default: {@code true}
     */
    @Value("${ovsx.scanning.name-similarity.enabled:true}")
    private boolean enabled;

    /**
     * Lowest trigram similarity, above 0 and at most 1, at which two extension names count as a match.
     * <p>
     * Property: {@code ovsx.scanning.name-similarity.name-threshold}
     * Default: {@code 0.7}
     */
    @Value("${ovsx.scanning.name-similarity.name-threshold:0.7}")
    private float nameThreshold;

    /**
     * Lowest trigram similarity, above 0 and at most 1, at which two display names count as a match.
     * <p>
     * Property: {@code ovsx.scanning.name-similarity.display-name-threshold}
     * Default: {@code 0.7}
     */
    @Value("${ovsx.scanning.name-similarity.display-name-threshold:0.7}")
    private float displayNameThreshold;

    /**
     * Names shorter than this, once normalized, only match exactly: trigram scores of very short strings
     * say little.
     * <p>
     * Property: {@code ovsx.scanning.name-similarity.min-fuzzy-length}
     * Default: {@code 4}
     */
    @Value("${ovsx.scanning.name-similarity.min-fuzzy-length:4}")
    private int minFuzzyLength;

    /**
     * Most matches looked up per field and name, which bounds the work a very generic name causes.
     * <p>
     * Property: {@code ovsx.scanning.name-similarity.max-matches}
     * Default: {@code 10}
     */
    @Value("${ovsx.scanning.name-similarity.max-matches:10}")
    private int maxMatches;

    /**
     * How long one lookup may run before the database cancels it. A cancelled lookup fails the check,
     * which does not block publication, or the dashboard page that asked for it.
     * <p>
     * Property: {@code ovsx.scanning.name-similarity.statement-timeout}
     * Default: {@code 10s}
     */
    @Value("${ovsx.scanning.name-similarity.statement-timeout:10s}")
    private Duration statementTimeout;

    @PostConstruct
    void validate() {
        requireThreshold("name-threshold", nameThreshold);
        requireThreshold("display-name-threshold", displayNameThreshold);
        if (minFuzzyLength < 1) {
            throw new IllegalStateException(
                    "ovsx.scanning.name-similarity.min-fuzzy-length must be at least 1: " + minFuzzyLength);
        }
        if (maxMatches < 1) {
            throw new IllegalStateException(
                    "ovsx.scanning.name-similarity.max-matches must be at least 1: " + maxMatches);
        }
        if (statementTimeout == null || statementTimeout.isNegative() || statementTimeout.isZero()) {
            throw new IllegalStateException(
                    "ovsx.scanning.name-similarity.statement-timeout must be positive: " + statementTimeout);
        }
    }

    private static void requireThreshold(String property, float value) {
        if (!(value > 0 && value <= 1)) {
            throw new IllegalStateException(
                    "ovsx.scanning.name-similarity." + property + " must be above 0 and at most 1: " + value);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getNameThreshold() {
        return nameThreshold;
    }

    public float getDisplayNameThreshold() {
        return displayNameThreshold;
    }

    public int getMinFuzzyLength() {
        return minFuzzyLength;
    }

    public int getMaxMatches() {
        return maxMatches;
    }

    public Duration getStatementTimeout() {
        return statementTimeout;
    }
}
