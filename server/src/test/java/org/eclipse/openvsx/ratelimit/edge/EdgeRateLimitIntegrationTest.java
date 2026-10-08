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
package org.eclipse.openvsx.ratelimit.edge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import redis.clients.jedis.RedisClusterClient;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.CustomerService;
import org.eclipse.openvsx.ratelimit.RateLimitService;
import org.eclipse.openvsx.ratelimit.config.EdgeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The origin half of edge rate limiting, from the Fastly log batch to the block written for the
 * edge, and the filter's handling of requests the edge forwards.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "ovsx.rate-limit.enabled=true",
        "ovsx.rate-limit.filters[0].url=^(?!/actuator/|/internal/).*",
        "ovsx.rate-limit.edge.enabled=true",
        "ovsx.rate-limit.edge.shared-secret=" + EdgeRateLimitIntegrationTest.SECRET
    }
)
class EdgeRateLimitIntegrationTest extends AbstractPostgresContainerTest {

    static final String SECRET = "s3cret";

    @LocalServerPort
    int port;

    // not TestRestTemplate: its Apache client retries a 429 after sleeping for its Retry-After, by
    // when the bucket has refilled and the retry succeeds
    HttpClient http = HttpClient.newHttpClient();

    // several beans loop on subscribe() in the background, which races per-test stubbing, so the
    // behaviour is fixed up front: every SETNX wins, i.e. no batch is a redelivery. Stub-only, as
    // recording those loops' invocations would exhaust the heap.
    @TestBean
    RedisClusterClient redisClusterClient;

    static RedisClusterClient redisClusterClient() {
        return mock(
                RedisClusterClient.class,
                withSettings().stubOnly().defaultAnswer(
                        invocation -> "set".equals(invocation.getMethod().getName())
                                ? "OK"
                                : RETURNS_DEFAULTS.answer(invocation)));
    }

    @MockitoBean
    ProxyManager<byte[]> proxyManager;

    @MockitoBean
    RateLimitService rateLimitService;

    @MockitoBean
    CustomerService customerService;

    @MockitoBean
    EdgeKvClient edgeKvClient;

    Bucket bucket;

    @BeforeEach
    void setUp() {
        bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(2).refillGreedy(2, Duration.ofMinutes(5)).build())
                .build();
        when(rateLimitService.getBucket(any()))
                .thenAnswer(_ -> RateLimitService.BucketPair.of(bucket, new RateLimitService.MinimumBandwidth(2, 0)));

        var acme = new Customer();
        acme.setName("acme");
        when(customerService.getCustomerByName("acme")).thenReturn(Optional.of(acme));
        when(customerService.getCustomerByIpAddress(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void usageBatchExhaustingTheTier_blocksTheCustomerAtTheEdge() {
        var now = Instant.now().getEpochSecond();

        var response = postUsage(SECRET, record("acme") + record("acme") + record("acme"));

        assertThat(response.statusCode()).isEqualTo(204);
        verify(edgeKvClient).put(eq("block:acme"), argThat(epoch -> Long.parseLong(epoch) > now));
    }

    @Test
    void usageBatchWithinTheTier_doesNotBlock() {
        postUsage(SECRET, record("acme"));

        assertThat(bucket.getAvailableTokens()).isEqualTo(1);
        verify(edgeKvClient, never()).put(eq("block:acme"), anyString());
    }

    @Test
    void usageBatchWithoutTheSecret_isForbidden() {
        var response = postUsage("guess", record("acme") + record("acme") + record("acme"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(bucket.getAvailableTokens()).isEqualTo(2);
    }

    @Test
    void requestForwardedByTheEdge_isNotDebitedTwice() {
        var response = get("/api/-/search", SECRET, "acme");

        assertThat(response.statusCode()).isNotEqualTo(429);
        assertThat(bucket.getAvailableTokens()).isEqualTo(2);
    }

    @Test
    void requestForwardedByTheEdge_isRejectedOnceTheBucketIsExhausted() {
        bucket.consumeIgnoringRateLimits(2);

        var response = get("/api/-/search", SECRET, "acme");

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After").orElse(null)).isNotNull();
    }

    @Test
    void forgedEdgeHeaders_areCountedOnTheOrigin() {
        var response = get("/api/-/search", "guess", "acme");

        assertThat(response.headers().firstValue("X-RateLimit-Limit").orElse(null)).isEqualTo("2");
        assertThat(bucket.getAvailableTokens()).isEqualTo(1);
    }

    @Test
    void everyPathIsLimited_exceptOperationalOnes() {
        assertThat(get("/login", null, null).headers().firstValue("X-RateLimit-Limit").orElse(null)).isEqualTo("2");
        assertThat(get("/actuator/health", null, null).headers().firstValue("X-RateLimit-Limit").orElse(null)).isNull();
    }

    private HttpResponse<String> postUsage(String secret, String batch) {
        return send(
                HttpRequest.newBuilder(uri(EdgeUsageAPI.PATH))
                        .header(EdgeProperties.HEADER_SECRET, secret)
                        .POST(HttpRequest.BodyPublishers.ofString(batch)));
    }

    private HttpResponse<String> get(String path, @Nullable String secret, @Nullable String customer) {
        var request = HttpRequest.newBuilder(uri(path)).GET();
        if (secret != null && customer != null) {
            request.header(EdgeProperties.HEADER_SECRET, secret).header(EdgeProperties.HEADER_CUSTOMER, customer);
        }
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String record(String customer) {
        return "{\"ts\":\"2026-09-25T10:00:00Z\",\"customer\":\"" + customer
                + "\",\"ip\":\"1.1.1.1\",\"url\":\"/vscjava/pack/1.0.0/file/pack.vsix\",\"status\":200}\n";
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}