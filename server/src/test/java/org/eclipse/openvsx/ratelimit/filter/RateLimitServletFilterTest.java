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
package org.eclipse.openvsx.ratelimit.filter;

import java.time.Duration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.IdentityService;
import org.eclipse.openvsx.ratelimit.RateLimitService;
import org.eclipse.openvsx.ratelimit.ResolvedIdentity;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.eclipse.openvsx.ratelimit.config.RateLimitFilterProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitServletFilterTest {

    UsageStatsService usageStats = mock(UsageStatsService.class);
    IdentityService identities = mock(IdentityService.class);
    RateLimitService rateLimits = mock(RateLimitService.class);
    FilterChain chain = mock(FilterChain.class);

    RateLimitServletFilter filter;
    Customer acme = new Customer();
    Bucket bucket = Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(2).refillGreedy(2, Duration.ofMinutes(5)).build())
            .build();

    @BeforeEach
    void setUp() {
        acme.setName("acme");
        var properties = new RateLimitFilterProperties();
        properties.setHttpStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        filter = new RateLimitServletFilter(properties, usageStats, identities, rateLimits);
        when(rateLimits.getBucket(any()))
                .thenReturn(RateLimitService.BucketPair.of(bucket, new RateLimitService.MinimumBandwidth(2, 0)));
    }

    @Test
    void edgeCountedRequest_isServedWithoutDebitingTheBucket() throws Exception {
        whenResolved(true);

        var response = new MockHttpServletResponse();
        filter.doFilter(request(), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(bucket.getAvailableTokens()).isEqualTo(2);
        verifyNoInteractions(usageStats);
    }

    @Test
    void edgeCountedRequest_isRejectedOnceTheBucketIsExhausted() throws Exception {
        whenResolved(true);
        bucket.consumeIgnoringRateLimits(2);

        var response = new MockHttpServletResponse();
        filter.doFilter(request(), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void originCountedRequest_debitsTheBucketAsBefore() throws Exception {
        whenResolved(false);

        filter.doFilter(request(), new MockHttpServletResponse(), chain);

        assertThat(bucket.getAvailableTokens()).isEqualTo(1);
        verify(usageStats).incrementUsage(acme);
    }

    private void whenResolved(boolean countedAtEdge) {
        when(identities.resolveIdentity(any()))
                .thenReturn(new ResolvedIdentity("1.2.3.4", "customer_acme", acme, null, null, countedAtEdge));
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/-/search");
        request.setServletPath("/api/-/search");
        return request;
    }
}