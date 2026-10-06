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
