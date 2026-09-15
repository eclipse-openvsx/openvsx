/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.cdn;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.web.SurrogateKey;
import org.eclipse.openvsx.web.SurrogateKeyInterceptor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Driven through the interceptor that tags responses as well, since what the advice does depends on
 * what that put there.
 */
class SurrogateCacheControlAdviceTest {

    private static final String A_WEEK = "max-age=604800";

    private MockMvc mockMvc(boolean purgingConfigured) {
        var config = new CdnPurgeConfig();
        if (purgingConfigured) {
            ReflectionTestUtils.setField(config, "provider", "fastly");
            ReflectionTestUtils.setField(config, "fastlyServiceId", "svc-1");
            ReflectionTestUtils.setField(config, "fastlyApiToken", "token-1");
        }
        var advice = new SurrogateCacheControlAdvice(new SingletonProvider<>(config));
        ReflectionTestUtils.setField(advice, "duration", Duration.ofDays(7));
        return MockMvcBuilders.standaloneSetup(new StubApi())
                .addInterceptors(new SurrogateKeyInterceptor())
                .setControllerAdvice(advice)
                .build();
    }

    @Test
    void letsTheCdnKeepAPurgeableResponse() throws Exception {
        mockMvc(true).perform(get("/api/redhat/java"))
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/java ns/redhat"))
                .andExpect(header().string(SurrogateCacheControlAdvice.HEADER, A_WEEK));
    }

    // Nothing would drop it, so nothing may keep it for a week.
    @Test
    void leavesResponsesAloneWithoutAConfiguredProvider() throws Exception {
        mockMvc(false).perform(get("/api/redhat/java"))
                .andExpect(header().doesNotExist(SurrogateCacheControlAdvice.HEADER));
    }

    // No key means no way to purge it: /api/-/search and friends are about no single extension.
    @Test
    void leavesAnUntaggedResponseAlone() throws Exception {
        mockMvc(true).perform(get("/api/-/search"))
                .andExpect(header().doesNotExist(SurrogateCacheControlAdvice.HEADER));
    }

    // An endpoint that never said a shared cache may hold this - an authenticated check, say - has
    // not been asked whether a CDN may hold it for a week either.
    @Test
    void leavesAResponseAloneThatWasNeverMarkedPublic() throws Exception {
        mockMvc(true).perform(get("/api/redhat/verify-pat"))
                .andExpect(header().string(SurrogateKey.HEADER, "ns/redhat"))
                .andExpect(header().doesNotExist(SurrogateCacheControlAdvice.HEADER));
    }

    @Test
    void leavesAFailedResponseAlone() throws Exception {
        mockMvc(true).perform(get("/api/redhat/missing"))
                .andExpect(header().doesNotExist(SurrogateCacheControlAdvice.HEADER));
    }

    @Test
    void leavesAWriteAlone() throws Exception {
        mockMvc(true).perform(post("/api/redhat/java/review"))
                .andExpect(header().doesNotExist(SurrogateCacheControlAdvice.HEADER));
    }

    @RestController
    private static class StubApi {

        @GetMapping("/api/{namespace}/{extension}")
        ResponseEntity<String> extension(@PathVariable String namespace, @PathVariable String extension) {
            if ("missing".equals(extension)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noCache().cachePublic())
                        .body("not found");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePublic()).body(extension);
        }

        @GetMapping("/api/{namespace}/verify-pat")
        ResponseEntity<String> verifyToken(@PathVariable String namespace) {
            return ResponseEntity.ok(namespace);
        }

        @GetMapping("/api/-/search")
        ResponseEntity<String> search() {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                    .body("[]");
        }

        @PostMapping("/api/{namespace}/{extension}/review")
        ResponseEntity<String> review(@PathVariable String namespace, @PathVariable String extension) {
            return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePublic()).body("ok");
        }
    }

    /** The one bean an {@link ObjectProvider} has to hand back here. */
    private record SingletonProvider<T>(T value) implements ObjectProvider<T> {

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }
    }
}
