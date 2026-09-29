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

import org.eclipse.jetty.compression.server.CompressionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VSCodeGalleryCompressionConfig} is guarded by {@code @ConditionalOnClass} so that a
 * hypothetical deployment without Jetty on the classpath (e.g. Tomcat/Undertow) skips it instead of
 * failing to start with a {@code NoClassDefFoundError}.
 */
class VSCodeGalleryCompressionConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VSCodeGalleryCompressionConfig.class);

    @Test
    void registersTheCustomizerWhenJettyIsOnTheClasspath() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(VSCodeGalleryCompressionConfig.class));
    }

    @Test
    void isSkippedWithoutJettysCompressionSupportOnTheClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(CompressionHandler.class))
                .run(context -> assertThat(context).doesNotHaveBean(VSCodeGalleryCompressionConfig.class));
    }
}
