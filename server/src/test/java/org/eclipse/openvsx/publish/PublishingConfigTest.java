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

package org.eclipse.openvsx.publish;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PublishingConfigTest {

    // PublishingConfig binds via @ConfigurationProperties: registering the bean alone would not run
    // the binder, and every assertion below would pass against the defaults for the wrong reason.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(
                    context -> context.getBeanFactory()
                            .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultsMaxOverrideSizeToOneGibibyte() {
        contextRunner.run(
                context -> assertThat(context.getBean(PublishingConfig.class).getMaxOverrideSize())
                        .isEqualTo(1024L * 1024 * 1024));
    }

    @Test
    void bindsMaxOverrideSizeFromTheProperty() {
        contextRunner.withPropertyValues("ovsx.publishing.max-override-size=2147483648")
                .run(
                        context -> assertThat(context.getBean(PublishingConfig.class).getMaxOverrideSize())
                                .isEqualTo(2147483648L));
    }

    @Test
    void refusesANonPositiveMaxOverrideSize() {
        contextRunner.withPropertyValues("ovsx.publishing.max-override-size=0")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("ovsx.publishing.max-override-size"));
    }

    @Test
    void refusesAnOverrideCeilingBelowTheDefaultContentSize() {
        contextRunner
                .withPropertyValues(
                        "ovsx.publishing.max-content-size=1048576",
                        "ovsx.publishing.max-override-size=1024")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("must not be smaller than"));
    }

    @Test
    void refusesANonPositiveMaxContentSize() {
        contextRunner.withPropertyValues("ovsx.publishing.max-content-size=-1")
                .run(
                        context -> assertThat(context)
                                .hasFailed()
                                .getFailure()
                                .rootCause()
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("ovsx.publishing.max-content-size"));
    }

    @Configuration
    @EnableConfigurationProperties(PublishingConfig.class)
    static class TestConfig {
    }
}
