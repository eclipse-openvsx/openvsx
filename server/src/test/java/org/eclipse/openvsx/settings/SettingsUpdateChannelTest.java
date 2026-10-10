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
package org.eclipse.openvsx.settings;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClusterClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SettingsUpdateChannelTest {

    private final RedisClusterClient redis = Mockito.mock(RedisClusterClient.class);
    private final SettingsCache cache = Mockito.mock(SettingsCache.class);

    @Test
    void publishGoesOutOnTheSettingsChannel() {
        new SettingsUpdateChannel(redis, cache).publish();

        verify(redis).publish(eq("settings.update"), anyString());
    }

    @Test
    void publishWithoutRedisIsANoOp() {
        new SettingsUpdateChannel(null, cache).publish();

        verify(redis, never()).publish(anyString(), anyString());
    }

    @Test
    void aMessageFromAnotherNodeClearsTheCache() throws Exception {
        var listener = new AtomicReference<JedisPubSub>();
        var subscribed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            subscribed.countDown();
            release.await();
            return null;
        }).when(redis).subscribe(any(JedisPubSub.class), eq("settings.update"));

        var channel = new SettingsUpdateChannel(redis, cache);
        channel.initialize();
        try {
            assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();

            listener.get().onMessage("settings.update", "1");
            verify(cache).clear();

            listener.get().onMessage("some.other.channel", "1");
            verify(cache, Mockito.times(1)).clear();
        } finally {
            release.countDown();
            channel.shutdown();
        }
    }
}
