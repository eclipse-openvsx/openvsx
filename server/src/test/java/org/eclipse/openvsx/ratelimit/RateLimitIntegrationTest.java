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
package org.eclipse.openvsx.ratelimit;

import java.net.URI;
import java.time.Duration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import redis.clients.jedis.RedisClusterClient;

import org.eclipse.openvsx.AbstractPostgresContainerTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "ovsx.rate-limit.enabled=true",
        "ovsx.rate-limit.filters[0].url=/(api|vscode)/.*"
    }
)
@AutoConfigureTestRestTemplate
class RateLimitIntegrationTest extends AbstractPostgresContainerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockitoBean
    RedisClusterClient redisClusterClient;

    @MockitoBean
    ProxyManager<byte[]> proxyManager;

    @MockitoBean
    RateLimitService rateLimitService;

    @MockitoBean
    IdentityService identityService;

    @BeforeEach
    void setUp() {
        var bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofHours(1)).build())
                .build();
        Mockito.when(rateLimitService.getBucket(any()))
                .thenReturn(RateLimitService.BucketPair.of(bucket, new RateLimitService.MinimumBandwidth(100, 0)));

        var identity = new ResolvedIdentity("1.2.3.4", "ip_1.2.3.4", null, null, null);
        Mockito.when(identityService.resolveIdentity(any())).thenReturn(identity);
    }

    @Test
    void normalApiPath_isRateLimited() {
        var response = restTemplate.getForEntity(uri("/api/-/search"), String.class);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    void normalVscodePath_isRateLimited() {
        var response = restTemplate.getForEntity(uri("/vscode/gallery/extensionquery"), String.class);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    void percentEncodedApiPath_isRateLimited() {
        var response = restTemplate.getForEntity(uri("/%61pi/-/search"), String.class);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    void percentEncodedVscodePath_isRateLimited() {
        var response = restTemplate.getForEntity(uri("/%76scode/gallery/extensionquery"), String.class);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    void nonApiPath_isNotRateLimited() {
        var response = restTemplate.getForEntity(uri("/login"), String.class);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isNull();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
