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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NameSimilarityCheckConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(
                    context -> context.getBeanFactory()
                            .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(NameSimilarityCheckConfig.class);

    @Test
    void defaults() {
        runner.run(context -> {
            var config = context.getBean(NameSimilarityCheckConfig.class);
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getNameThreshold()).isEqualTo(0.7f);
            assertThat(config.getDisplayNameThreshold()).isEqualTo(0.7f);
            assertThat(config.getMinFuzzyLength()).isEqualTo(4);
            assertThat(config.getMaxMatches()).isEqualTo(10);
            assertThat(config.getStatementTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void bindsTheProperties() {
        runner.withPropertyValues(
                "ovsx.scanning.name-similarity.enabled=false",
                "ovsx.scanning.name-similarity.name-threshold=0.8",
                "ovsx.scanning.name-similarity.display-name-threshold=1",
                "ovsx.scanning.name-similarity.min-fuzzy-length=3",
                "ovsx.scanning.name-similarity.max-matches=5",
                "ovsx.scanning.name-similarity.statement-timeout=2s")
                .run(context -> {
                    var config = context.getBean(NameSimilarityCheckConfig.class);
                    assertThat(config.isEnabled()).isFalse();
                    assertThat(config.getNameThreshold()).isEqualTo(0.8f);
                    assertThat(config.getDisplayNameThreshold()).isEqualTo(1f);
                    assertThat(config.getMinFuzzyLength()).isEqualTo(3);
                    assertThat(config.getMaxMatches()).isEqualTo(5);
                    assertThat(config.getStatementTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "ovsx.scanning.name-similarity.name-threshold=0",
            "ovsx.scanning.name-similarity.name-threshold=1.1",
            "ovsx.scanning.name-similarity.display-name-threshold=-0.5",
            "ovsx.scanning.name-similarity.min-fuzzy-length=0",
            "ovsx.scanning.name-similarity.max-matches=0",
            "ovsx.scanning.name-similarity.statement-timeout=0s"
        }
    )
    void refusesToStartWithAnInvalidValue(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }
}
