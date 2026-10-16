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
package org.eclipse.openvsx.publisheragreement;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class PublisherAgreementConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PublisherAgreementConfig.class);

    @Test
    void noopIsTheDefault() {
        runner.run(
                context -> assertThat(context.getBean(PublisherAgreementService.class))
                        .isInstanceOf(NoopPublisherAgreementService.class));
    }

    @Test
    void primaryDeploymentBeanReplacesTheDefault() {
        runner.withUserConfiguration(DeploymentConfig.class).run(context -> {
            assertThat(context.getBeansOfType(PublisherAgreementService.class)).hasSize(2);
            assertThat(context.getBean(PublisherAgreementService.class))
                    .isSameAs(context.getBean(DeploymentConfig.class).custom);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class DeploymentConfig {
        final PublisherAgreementService custom = Mockito.mock(PublisherAgreementService.class);

        @Bean
        @Primary
        PublisherAgreementService custom() {
            return custom;
        }
    }
}
