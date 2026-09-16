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
package org.eclipse.openvsx.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the binding of {@code ovsx.webui.*}. These used to be read with {@code @Value}, which matches a
 * property key literally, so the spellings an existing deployment may have in its configuration file are
 * worth asserting rather than assuming.
 */
class WebUiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(
                    context -> context.getBeanFactory()
                            .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(TestConfig.class);

    @Test
    void servesTheFrontendRoutesTheWebUiActuallyHas() {
        contextRunner.run(
                context -> assertThat(context.getBean(WebUiProperties.class).getFrontendRoutes())
                        .containsExactly(
                                "/extension/**",
                                "/namespace/**",
                                "/search",
                                "/user-settings/**",
                                "/publish",
                                "/admin-dashboard/**"));
    }

    @Test
    void bindsTheRoutesAsAList() {
        contextRunner.withPropertyValues("ovsx.webui.frontend-routes=/a/**,/b").run(
                context -> assertThat(
                        context.getBean(WebUiProperties.class).getFrontendRoutes()).containsExactly("/a/**", "/b"));
    }

    @Test
    void keepsReadingTheCamelCaseSpellingOfTheRoutes() {
        // The documented spelling is kebab case, but ovsx.webui.frontendRoutes is what deployments
        // configured for years while this was a @Value, and relaxed binding is what keeps them working.
        contextRunner.withPropertyValues("ovsx.webui.frontendRoutes=/a/**,/b").run(
                context -> assertThat(
                        context.getBean(WebUiProperties.class).getFrontendRoutes()).containsExactly("/a/**", "/b"));
    }

    @Test
    void keepsReadingTheCamelCaseSpellingOfTheAdditionalRoutes() {
        contextRunner.withPropertyValues("ovsx.webui.additionalRoutes=/custom/**").run(
                context -> assertThat(
                        context.getBean(WebUiProperties.class).getAdditionalRoutes()).containsExactly("/custom/**"));
    }

    @Test
    void servesNoAdditionalRoutesUnlessTheDeploymentNamesThem() {
        contextRunner
                .run(context -> assertThat(context.getBean(WebUiProperties.class).getAdditionalRoutes()).isEmpty());
    }

    @Test
    void hasNoWebUiUrlByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(WebUiProperties.class).getUrl()).isEmpty());
    }

    @Configuration
    @EnableConfigurationProperties(WebUiProperties.class)
    static class TestConfig {
    }
}
