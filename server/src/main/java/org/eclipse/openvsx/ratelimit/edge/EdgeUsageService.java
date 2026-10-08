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
import java.util.HashMap;
import java.util.Map;

import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.RedisClusterClient;
import redis.clients.jedis.params.SetParams;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.ratelimit.CustomerService;
import org.eclipse.openvsx.ratelimit.IdentityService;
import org.eclipse.openvsx.ratelimit.RateLimitService;
import org.eclipse.openvsx.ratelimit.UsageStatsService;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;

/**
 * Charges the requests the edge served on the origin's behalf to the customers it attributed them
 * to, and blocks a customer at the edge once its bucket is in debt.
 */
@Service
@ConditionalOnBean(RateLimitConfig.class)
@ConditionalOnProperty(prefix = RateLimitProperties.PROPERTY_PREFIX, name = "edge.enabled", havingValue = "true")
public class EdgeUsageService {

    private static final String BATCH_KEY_PREFIX = "ratelimit.edge.batch:";
    private static final Duration BATCH_TTL = Duration.ofMinutes(10);
    // ponytail: fixed cap for a debt bucket4j reports as unpayable; derive it from the tier if it bites
    private static final Duration MAX_BLOCK = Duration.ofHours(1);
    // the bucket is keyed by customer, so the address is informational only
    private static final String EDGE_ADDRESS = "edge";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Logger logger = LoggerFactory.getLogger(EdgeUsageService.class);

    private final RedisClusterClient redis;
    private final CustomerService customers;
    private final IdentityService identities;
    private final RateLimitService rateLimits;
    private final UsageStatsService usageStats;
    private final EdgeStateService state;

    private final Counter accepted;
    private final Counter skipped;
    private final Counter duplicateBatches;

    public EdgeUsageService(
            RedisClusterClient redis,
            CustomerService customers,
            IdentityService identities,
            RateLimitService rateLimits,
            UsageStatsService usageStats,
            EdgeStateService state,
            MeterRegistry registry
    ) {
        this.redis = redis;
        this.customers = customers;
        this.identities = identities;
        this.rateLimits = rateLimits;
        this.usageStats = usageStats;
        this.state = state;
        this.accepted = Counter.builder("openvsx.edge.usage.records")
                .tag("result", "accepted")
                .description("Edge usage records charged to a customer")
                .register(registry);
        this.skipped = Counter.builder("openvsx.edge.usage.records")
                .tag("result", "skipped")
                .description("Edge usage records that could not be charged")
                .register(registry);
        this.duplicateBatches = Counter.builder("openvsx.edge.usage.duplicate.batches")
                .description("Edge usage batches ignored as redeliveries")
                .register(registry);
    }

    /**
     * Ingests one batch of newline-delimited JSON records, each one request the edge attributed to
     * the customer in its {@code customer} field.
     */
    public void ingest(String batch) {
        if (!isFirstDelivery(batch)) {
            duplicateBatches.increment();
            return;
        }

        countByCustomer(batch).forEach(this::charge);
    }

    /**
     * Unblocks the customers whose bucket has tokens again, and those that no longer exist.
     */
    public void unblockRefilled() {
        for (var name : state.blockedCustomers()) {
            var bucket = customers.getCustomerByName(name).map(this::bucket).orElse(null);
            if (bucket == null || bucket.getAvailableTokens() > 0) {
                state.unblock(name);
            }
        }
    }

    /**
     * Fastly retries a batch as a whole, so one key per batch catches every redelivery.
     */
    private boolean isFirstDelivery(String batch) {
        var key = BATCH_KEY_PREFIX + EdgeConfigService.sha256Hex(batch);
        return "OK".equals(redis.set(key, "1", SetParams.setParams().nx().ex(BATCH_TTL.toSeconds())));
    }

    private Map<String, Long> countByCustomer(String batch) {
        var counts = new HashMap<String, Long>();
        batch.lines().filter(line -> !line.isBlank()).forEach(line -> {
            var customer = parseCustomer(line);
            if (customer != null) {
                counts.merge(customer, 1L, Long::sum);
            } else {
                skipped.increment();
            }
        });
        return counts;
    }

    private @Nullable String parseCustomer(String line) {
        try {
            var customer = MAPPER.readTree(line).path("customer");
            return customer.isString() ? StringUtils.trimToNull(customer.asString()) : null;
        } catch (JacksonException e) {
            logger.warn("could not parse edge usage record '{}'", line);
            return null;
        }
    }

    private void charge(String name, long requests) {
        var customer = customers.getCustomerByName(name);
        if (customer.isEmpty()) {
            logger.warn("edge reported {} requests for unknown customer {}", requests, name);
            skipped.increment(requests);
            return;
        }

        accepted.increment(requests);
        usageStats.incrementUsage(customer.get(), requests);

        var bucket = bucket(customer.get());
        if (bucket == null) {
            return;
        }

        // the requests were already served, so they are debited even past zero; the debt is
        // what keeps the customer blocked until it is paid back
        var penalty = bucket.consumeIgnoringRateLimits(requests);
        if (penalty > 0) {
            var wait = penalty == Long.MAX_VALUE ? MAX_BLOCK : Duration.ofNanos(Math.min(penalty, MAX_BLOCK.toNanos()));
            state.block(name, Instant.now().plus(wait));
        }
    }

    private @Nullable Bucket bucket(Customer customer) {
        return rateLimits.getBucket(identities.forCustomer(customer, EDGE_ADDRESS, true)).bucket();
    }
}