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
package org.eclipse.openvsx.cache;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.openvsx.cache.CacheService.CACHE_EXTENSION_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Clearing by pattern is what every eviction does here, so which command Redis is asked to walk its
 * keyspace with is not an implementation detail: {@code KEYS} blocks the server until it has walked
 * all of it.
 */
class RedisCacheManagerScanTest {

    private final RedisKeyCommands keyCommands = Mockito.mock(RedisKeyCommands.class);
    private final RedisConnection connection = Mockito.mock(RedisConnection.class);
    private final RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);

    private RedisCache extensionJsonCache() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.keyCommands()).thenReturn(keyCommands);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = Mockito.mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);

        var ttl = Duration.ofHours(1);
        var manager = (RedisCacheManager) new CacheConfig()
                .redisCacheManager(connectionFactory, ttl, ttl, ttl, ttl, ttl, ttl, ttl, ttl, ttl);
        manager.afterPropertiesSet();
        return (RedisCache) manager.getCache(CACHE_EXTENSION_JSON);
    }

    @Test
    void walksTheKeyspaceInBatchesRatherThanBlockingOnKeys() {
        extensionJsonCache().clear("foo.bar:*");

        verify(keyCommands).scan(any(ScanOptions.class));
        verify(keyCommands, never()).keys(any());
    }

    @Test
    void asksForThePatternItWasGiven() {
        var options = org.mockito.ArgumentCaptor.forClass(ScanOptions.class);

        extensionJsonCache().clear("foo.bar:*");

        verify(keyCommands).scan(options.capture());
        // the cache name prefixes the pattern, which is why RedisCache#clear(String) builds it
        assertThat(new String(options.getValue().getPattern())).isEqualTo(CACHE_EXTENSION_JSON + "::foo.bar:*");
    }
}
