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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.RedisClusterClient;
import redis.clients.jedis.params.SetParams;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.CustomerService;
import org.eclipse.openvsx.ratelimit.IdentityService;
import org.eclipse.openvsx.ratelimit.RateLimitService;
import org.eclipse.openvsx.ratelimit.ResolvedIdentity;
import org.eclipse.openvsx.ratelimit.UsageStatsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EdgeUsageServiceTest {

    RedisClusterClient redis = mock(RedisClusterClient.class);
    CustomerService customers = mock(CustomerService.class);
    IdentityService identities = mock(IdentityService.class);
    RateLimitService rateLimits = mock(RateLimitService.class);
    UsageStatsService usageStats = mock(UsageStatsService.class);
    EdgeStateService state = mock(EdgeStateService.class);

    EdgeUsageService service = new EdgeUsageService(
            redis,
            customers,
            identities,
            rateLimits,
            usageStats,
            state,
            new SimpleMeterRegistry());

    Customer acme = customer("acme");
    Bucket bucket;

    @BeforeEach
    void setUp() {
        bucket = bucketWithCapacity(10);
        when(redis.set(anyString(), anyString(), any(SetParams.class))).thenReturn("OK");
        when(customers.getCustomerByName("acme")).thenReturn(Optional.of(acme));
        var identity = new ResolvedIdentity("edge", "customer_acme", acme, null, null, true);
        when(identities.forCustomer(eq(acme), anyString(), eq(true))).thenReturn(identity);
        when(rateLimits.getBucket(identity)).thenAnswer(_ -> RateLimitService.BucketPair.of(bucket, null));
    }

    @Test
    void batch_debitsTheCustomerBucketByItsRequestCount() {
        service.ingest(lines("acme", "acme", "acme"));

        assertThat(bucket.getAvailableTokens()).isEqualTo(7);
        verify(usageStats).incrementUsage(acme, 3);
        verify(state, never()).block(anyString(), any());
    }

    @Test
    void redeliveredBatch_isIgnored() {
        when(redis.set(anyString(), anyString(), any(SetParams.class))).thenReturn("OK", (String) null);

        var batch = lines("acme", "acme");
        service.ingest(batch);
        service.ingest(batch);

        assertThat(bucket.getAvailableTokens()).isEqualTo(8);
        verify(usageStats, times(1)).incrementUsage(acme, 2);
    }

    @Test
    void exhaustedBucket_blocksTheCustomerUntilItRefills() {
        bucket = bucketWithCapacity(2);

        var before = Instant.now();
        service.ingest(lines("acme", "acme", "acme"));

        var resetAt = ArgumentCaptor.forClass(Instant.class);
        verify(state).block(eq("acme"), resetAt.capture());
        assertThat(resetAt.getValue()).isAfter(before).isBefore(before.plus(Duration.ofHours(1).plusSeconds(1)));
        // served requests are debited past zero, so the debt keeps the customer blocked
        assertThat(bucket.getAvailableTokens()).isEqualTo(-1);
    }

    @Test
    void malformedAndUnknownRecords_areSkipped() {
        when(customers.getCustomerByName("nobody")).thenReturn(Optional.empty());

        service.ingest("not json\n{\"customer\":\"\"}\n{\"ip\":\"1.2.3.4\"}\n" + lines("nobody", "acme"));

        assertThat(bucket.getAvailableTokens()).isEqualTo(9);
        verify(usageStats).incrementUsage(acme, 1);
        verifyNoMoreInteractions(usageStats);
    }

    @Test
    void unblockRefilled_unblocksOnlyCustomersWithTokens() {
        var drained = customer("drained");
        var drainedBucket = bucketWithCapacity(1);
        drainedBucket.consumeIgnoringRateLimits(1);
        var drainedIdentity = new ResolvedIdentity("edge", "customer_drained", drained, null, null, true);
        when(customers.getCustomerByName("drained")).thenReturn(Optional.of(drained));
        when(customers.getCustomerByName("gone")).thenReturn(Optional.empty());
        when(identities.forCustomer(eq(drained), anyString(), eq(true))).thenReturn(drainedIdentity);
        when(rateLimits.getBucket(drainedIdentity)).thenReturn(RateLimitService.BucketPair.of(drainedBucket, null));
        when(state.blockedCustomers()).thenReturn(Set.of("acme", "drained", "gone"));

        service.unblockRefilled();

        verify(state).unblock("acme");
        verify(state).unblock("gone");
        verify(state, never()).unblock("drained");
    }

    private static String lines(String... customers) {
        var batch = new StringBuilder();
        for (var customer : customers) {
            batch.append("{\"ts\":\"2026-09-25T10:00:00Z\",\"customer\":\"")
                    .append(customer)
                    .append("\",\"ip\":\"1.1.1.1\",\"url\":\"/api/-/search\",\"status\":200}\n");
        }
        return batch.toString();
    }

    private static Bucket bucketWithCapacity(long capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(5)).build())
                .build();
    }

    private static Customer customer(String name) {
        var customer = new Customer();
        customer.setName(name);
        return customer;
    }
}