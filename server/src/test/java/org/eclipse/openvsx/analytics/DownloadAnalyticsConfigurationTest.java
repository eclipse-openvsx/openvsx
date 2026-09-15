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
package org.eclipse.openvsx.analytics;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The settling margin is bound with @Value rather than read from the Environment so that the
 * configuration reference check can see it. These cover the validation that binding brought with it.
 */
class DownloadAnalyticsConfigurationTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(config(Duration.ofHours(2), Duration.ofHours(1))::validateConfiguration)
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsZeroForBoth() {
        // Zero is a deliberate setting in each case, not a missing one: no backing off from the
        // current bucket boundary, and no caching of settled ranges.
        assertThatCode(config(Duration.ZERO, Duration.ZERO)::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void rejectsANegativeMargin() {
        // It would put the settled boundary in the future, so the whole series - including buckets
        // that have not happened - would come from the settled cache.
        assertThatThrownBy(config(Duration.ofHours(-1), Duration.ofHours(1))::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.settling-margin");
    }

    @Test
    void rejectsANegativeCacheTtl() {
        assertThatThrownBy(config(Duration.ofHours(2), Duration.ofHours(-1))::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ovsx.analytics.settled-cache.ttl");
    }

    private DownloadAnalyticsConfiguration config(Duration settlingMargin, Duration settledCacheTtl) {
        var config = new DownloadAnalyticsConfiguration();
        config.settlingMargin = settlingMargin;
        config.settledCacheTtl = settledCacheTtl;
        return config;
    }
}
