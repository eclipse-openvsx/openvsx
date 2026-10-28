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
package org.eclipse.openvsx.cache.bucket4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import javax.cache.configuration.MutableConfiguration;

import com.giffing.bucket4j.spring.boot.starter.config.cache.Bucket4jCacheConfiguration;
import com.giffing.bucket4j.spring.boot.starter.config.filter.servlet.Bucket4JAutoConfigurationServletFilter;
import com.github.benmanes.caffeine.jcache.CacheManagerImpl;
import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jetty.autoconfigure.servlet.JettyServletWebServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bucket4j Spring Boot starter against Spring Boot upgrades: its servlet filter
 * auto-configuration is skipped silently when it does not match the Boot version.
 */
@SpringBootTest(
    classes = Bucket4jStarterTest.Config.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "bucket4j.enabled=true",
        "bucket4j.cache-to-use=jcache",
        "bucket4j.filters[0].cache-name=buckets",
        "bucket4j.filters[0].url=/ping",
        "bucket4j.filters[0].rate-limits[0].bandwidths[0].capacity=1",
        "bucket4j.filters[0].rate-limits[0].bandwidths[0].time=1",
        "bucket4j.filters[0].rate-limits[0].bandwidths[0].unit=hours"
    }
)
class Bucket4jStarterTest {

    @LocalServerPort
    int port;

    @Test
    void filterRateLimitsRequests() throws Exception {
        assertThat(get("/ping")).isEqualTo(200);
        assertThat(get("/ping")).isEqualTo(429);
    }

    private int get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration(
        {
            JettyServletWebServerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            CacheAutoConfiguration.class,
            Bucket4jCacheConfiguration.class,
            Bucket4JAutoConfigurationServletFilter.class
        }
    )
    @Import({ JCacheBucket4jConfiguration.class, PingController.class })
    static class Config {

        @Bean
        JCacheCacheManager jCacheCacheManager() {
            var cacheManager = new CacheManagerImpl(
                    new CaffeineCachingProvider(),
                    false,
                    URI.create("bucket4j-starter-test"),
                    Thread.currentThread().getContextClassLoader(),
                    new Properties());
            cacheManager.createCache("buckets", new MutableConfiguration<>());
            return new JCacheCacheManager(cacheManager);
        }
    }

    @RestController
    static class PingController {

        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }
}
