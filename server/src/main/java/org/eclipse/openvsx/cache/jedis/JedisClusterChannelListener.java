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
package org.eclipse.openvsx.cache.jedis;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClusterClient;

public abstract class JedisClusterChannelListener extends JedisPubSub {
    private final Logger logger = LoggerFactory.getLogger(JedisClusterChannelListener.class);

    private final RedisClusterClient redisClusterClient;
    private final String channelName;
    private final String listenerName;

    // Redis subscriber state
    private volatile Thread subscriberThread;
    private volatile boolean running = true;

    public JedisClusterChannelListener(RedisClusterClient redisClusterClient, String channelName, String listenerName) {
        this.redisClusterClient = redisClusterClient;
        this.channelName = channelName;
        this.listenerName = listenerName;
    }

    public void startSubscriber() {
        subscriberThread = new Thread(this::subscribeLoop, listenerName + "Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void shutdown() {
        running = false;
        if (isSubscribed()) {
            unsubscribe();
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
    }

    public abstract void onMessage(String channel, String message);

    private void subscribeLoop() {
        AtomicInteger backoffMs = new AtomicInteger(1000);
        try (
                var executor = Executors.newSingleThreadScheduledExecutor(
                        new NamedThreadFactory("rate-limit-config-subscriber-reconnect"))
        ) {
            while (running && !Thread.currentThread().isInterrupted()) {
                ScheduledFuture<?> resetTask = null;
                try {
                    resetTask = executor.schedule(() -> backoffMs.set(1000), 10, TimeUnit.SECONDS);
                    logger.debug("Subscribing to redis channel {}", channelName);
                    redisClusterClient.subscribe(this, channelName);
                } catch (Exception e) {
                    if (!running) {
                        break;
                    }
                    logger.warn(
                            "Redis pubsub subscriber for channel {} disconnected, reconnecting in {}s: {}",
                            channelName,
                            backoffMs.get() / 1000,
                            e.getMessage());
                    if (resetTask != null) {
                        resetTask.cancel(true);
                    }
                    try {
                        Thread.sleep(backoffMs.get());
                        backoffMs.set(Math.min(backoffMs.get() * 2, 30000));
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
            executor.shutdownNow();
        }
    }
}
