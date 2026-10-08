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
import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClusterClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EdgeStateServiceTest {

    RedisClusterClient redis = mock(RedisClusterClient.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void block_writesTheResetEpochAndRemembersTheCustomer() {
        var kv = new FakeKv();
        var service = new EdgeStateService(kv, redis, registry);

        service.block("acme", Instant.ofEpochSecond(1_800_000_000L));

        assertThat(kv.entries).containsEntry("block:acme", "1800000000");
        verify(redis).sadd("ratelimit.edge.blocked", "acme");
    }

    @Test
    void unblock_deletesTheEntryAndForgetsTheCustomer() {
        var kv = new FakeKv();
        kv.entries.put("block:acme", "1800000000");
        var service = new EdgeStateService(kv, redis, registry);

        service.unblock("acme");

        assertThat(kv.entries).doesNotContainKey("block:acme");
        verify(redis).srem("ratelimit.edge.blocked", "acme");
    }

    @Test
    void failingStore_doesNotPropagateAndKeepsTheBookkeepingConsistent() {
        var kv = new FakeKv();
        kv.failing = true;
        var service = new EdgeStateService(kv, redis, registry);

        assertThatCode(() -> {
            service.block("acme", Instant.now());
            service.unblock("acme");
            service.publishConfig("{}");
        }).doesNotThrowAnyException();

        // an entry that never reached the edge must not be tracked, nor one that is still there forgotten
        verify(redis, never()).sadd(anyString(), anyString());
        verify(redis, never()).srem(anyString(), anyString());
        assertThat(registry.counter("openvsx.edge.kv.write.failures").count()).isEqualTo(3);
    }

    static class FakeKv implements EdgeKvClient {
        final Map<String, String> entries = new HashMap<>();
        boolean failing;

        @Override
        public void put(String key, String value) {
            fail();
            entries.put(key, value);
        }

        @Override
        public void delete(String key) {
            fail();
            entries.remove(key);
        }

        private void fail() {
            if (failing) {
                throw new IllegalStateException("edge store unavailable");
            }
        }
    }
}