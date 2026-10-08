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

import java.time.Instant;
import java.util.Set;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.RedisClusterClient;

import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.ratelimit.config.RateLimitProperties;

/**
 * What the edge is told: the customer configuration and which customers are blocked until when.
 * <p>
 * Writes never throw. A Fastly outage leaves the edge with stale state, so enforcement falls back
 * to what the origin filter sees, rather than failing the usage ingestion that called in here.
 */
@Service
@ConditionalOnBean(RateLimitConfig.class)
@ConditionalOnProperty(prefix = RateLimitProperties.PROPERTY_PREFIX, name = "edge.enabled", havingValue = "true")
public class EdgeStateService {

    static final String CONFIG_KEY = "ratelimit-config";
    static final String BLOCK_KEY_PREFIX = "block:";

    // shared across nodes: the node that blocks a customer is not necessarily the one that sweeps
    private static final String BLOCKED_SET = "ratelimit.edge.blocked";

    private final Logger logger = LoggerFactory.getLogger(EdgeStateService.class);

    private final EdgeKvClient kv;
    private final RedisClusterClient redis;
    private final Counter writeFailures;

    public EdgeStateService(EdgeKvClient kv, RedisClusterClient redis, MeterRegistry registry) {
        this.kv = kv;
        this.redis = redis;
        this.writeFailures = Counter.builder("openvsx.edge.kv.write.failures")
                .description("Writes to the edge key-value store that failed")
                .register(registry);
    }

    public void publishConfig(String config) {
        put(CONFIG_KEY, config);
    }

    /**
     * Blocks {@code customer} at the edge until {@code resetAt}. The edge compares the stored epoch
     * with its own clock, so an entry nobody deletes stops blocking by itself.
     */
    public void block(String customer, Instant resetAt) {
        if (put(BLOCK_KEY_PREFIX + customer, Long.toString(resetAt.getEpochSecond()))) {
            redis.sadd(BLOCKED_SET, customer);
        }
    }

    public void unblock(String customer) {
        try {
            kv.delete(BLOCK_KEY_PREFIX + customer);
            redis.srem(BLOCKED_SET, customer);
        } catch (RuntimeException e) {
            writeFailures.increment();
            logger.error("could not unblock customer {} at the edge", customer, e);
        }
    }

    public Set<String> blockedCustomers() {
        return redis.smembers(BLOCKED_SET);
    }

    private boolean put(String key, String value) {
        try {
            kv.put(key, value);
            return true;
        } catch (RuntimeException e) {
            writeFailures.increment();
            logger.error("could not write {} to the edge", key, e);
            return false;
        }
    }
}