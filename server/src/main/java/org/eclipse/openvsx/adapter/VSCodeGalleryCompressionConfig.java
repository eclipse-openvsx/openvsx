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

import org.eclipse.jetty.compression.server.CompressionConfig;
import org.eclipse.jetty.compression.server.CompressionHandler;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jetty.ConfigurableJettyWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes {@link VSCodeGalleryContentTypeAdvice}'s fixed {@code application/json;api-version=...}
 * Content-Type compressible without requiring an operator to add that exact literal to
 * {@code server.compression.mime-types} - see
 * <a href="https://github.com/eclipse-openvsx/openvsx/issues/2071">#2071</a> for why the literal is
 * otherwise needed at all (Jetty 12.1's {@code CompressionHandler} does an exact string match on
 * {@code Content-Type}, after stripping only the {@code charset} parameter).
 * <p>
 * Rather than duplicating whatever {@code server.compression.*} already installed, this reads back
 * the {@link CompressionConfig} the {@code spring-boot-jetty} auto-configuration already built for
 * the default path (from {@code server.compression.mime-types} et al.) and replaces it with an
 * equivalent one that additionally includes the Gallery API's fixed Content-Type. If compression is
 * disabled ({@code server.compression.enabled=false}), no {@link CompressionHandler} is installed at
 * all and this is a no-op.
 * <p>
 * This trades a config-only fix for a code-only one: it adds a compile-time dependency on
 * {@code jetty-compression-server} (otherwise only a transitive runtime dependency) and reaches into
 * Jetty-specific classes that a Tomcat/Undertow deployment wouldn't have, which is an acceptable
 * trade for a project that has already committed to Jetty exclusively. The upside is that no
 * deployment's {@code server.compression.mime-types} needs to know about the VS Code Gallery API's
 * versioned Content-Type at all - it always works, regardless of configuration.
 * <p>
 * Guarded by {@link ConditionalOnClass} on both Jetty-specific types this class touches, so that if
 * Jetty (and its compression module) were ever absent from the classpath - a Tomcat/Undertow
 * deployment, or a test slice that doesn't pull in {@code spring-boot-starter-jetty} - this
 * configuration is skipped instead of failing to load with a {@code NoClassDefFoundError}.
 */
@Configuration
@ConditionalOnClass({ CompressionHandler.class, ConfigurableJettyWebServerFactory.class })
public class VSCodeGalleryCompressionConfig {

    private static final PathSpec DEFAULT_PATH_SPEC = PathSpec.from("/");

    @Bean
    WebServerFactoryCustomizer<ConfigurableJettyWebServerFactory> vsCodeGalleryCompressionCustomizer() {
        return factory -> factory.addServerCustomizers(server -> {
            var compressionHandler = server.getDescendant(CompressionHandler.class);
            if (compressionHandler == null) {
                return;
            }

            var existing = compressionHandler.getConfiguration(DEFAULT_PATH_SPEC);
            var builder = CompressionConfig.builder();
            if (existing != null) {
                existing.getCompressIncludeMimeTypes().forEach(builder::compressIncludeMimeType);
                existing.getCompressExcludeMimeTypes().forEach(builder::compressExcludeMimeType);
                existing.getCompressIncludeMethods().forEach(builder::compressIncludeMethod);
                existing.getCompressExcludeMethods().forEach(builder::compressExcludeMethod);
                existing.getCompressIncludeEncodings().forEach(builder::compressIncludeEncoding);
                existing.getCompressExcludeEncodings().forEach(builder::compressExcludeEncoding);
                existing.getCompressIncludePaths().forEach(builder::compressIncludePath);
                existing.getCompressExcludePaths().forEach(builder::compressExcludePath);
                builder.compressPreferredEncodings(existing.getCompressPreferredEncodings());
            }
            builder.compressIncludeMimeType(VSCodeGalleryContentTypeAdvice.GALLERY_JSON.toString());
            compressionHandler.putConfiguration(DEFAULT_PATH_SPEC, builder.build());
        });
    }
}
