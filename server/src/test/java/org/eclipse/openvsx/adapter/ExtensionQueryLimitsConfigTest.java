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
package org.eclipse.openvsx.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionQueryLimitsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ExtensionQueryLimitsConfig.class);

    @Test
    void defaults() {
        contextRunner.run(context -> {
            var config = context.getBean(ExtensionQueryLimitsConfig.class);
            assertThat(config.getMaxCriteria()).isEqualTo(1000);
            assertThat(config.getMaxSearchPageSize()).isEqualTo(100);
        });
    }

    @Test
    void bindsTheProperties() {
        contextRunner
                .withPropertyValues(
                        "ovsx.extension-query.max-criteria=2000",
                        "ovsx.extension-query.max-search-page-size=50")
                .run(context -> {
                    var config = context.getBean(ExtensionQueryLimitsConfig.class);
                    assertThat(config.getMaxCriteria()).isEqualTo(2000);
                    assertThat(config.getMaxSearchPageSize()).isEqualTo(50);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = { "max-criteria", "max-search-page-size" })
    void refusesANonPositiveLimit(String property) {
        contextRunner.withPropertyValues("ovsx.extension-query." + property + "=0")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("ovsx.extension-query." + property));
    }
}
