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
package org.eclipse.openvsx.cache.bucket4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.giffing.bucket4j.spring.boot.starter.config.cache.CacheUpdateEvent;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClusterClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

public class JedisClusterCacheListener<K, V> extends JedisPubSub {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisClusterCacheListener.class);

    private final RedisClusterClient redisClusterClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String updateChannel;
    private final JavaType deserializeType;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param redisClusterClient The cluster to use for listening/publishing events
     * @param cacheName The name of the cache. This is used as prefix for the event channels
     * @param keyType The type of the key. This is required for parsing events and should match the K of this class.
     * @param valueType The type of the value. This is required for parsing events and should match the V of this class.
     */
    public JedisClusterCacheListener(
            RedisClusterClient redisClusterClient,
            String cacheName,
            Class<K> keyType,
            Class<V> valueType,
            ApplicationEventPublisher eventPublisher
    ) {
        this.redisClusterClient = redisClusterClient;
        this.updateChannel = cacheName.concat(":update");
        this.deserializeType = jsonMapper.getTypeFactory()
                .constructParametricType(CacheUpdateEvent.class, keyType, valueType);
        this.eventPublisher = eventPublisher;
        subscribe();
    }

    public void subscribe() {
        Thread thread = new Thread(() -> {
            AtomicInteger reconnectBackoffTimeMillis = new AtomicInteger(1000);
            // Using a NamedThreadFactory for creating a Daemon thread, so it will never block the jvm from closing.
            ScheduledExecutorService executorService = Executors
                    .newSingleThreadScheduledExecutor(new NamedThreadFactory("reset-reconnect-backoff-thread"));
            ScheduledFuture<?> resetTask = null;

            while (!Thread.currentThread().isInterrupted() && isUp()) {
                try {
                    // Schedule a reset of the backoff after 10 seconds.
                    // This is done in a different thread since subscribe is a blocking call.
                    resetTask = executorService
                            .schedule(() -> reconnectBackoffTimeMillis.set(1000), 10000, TimeUnit.MILLISECONDS);

                    redisClusterClient.subscribe(this, updateChannel);
                } catch (Exception e) {
                    LOGGER.error(
                            "Failed to connect the Jedis subscriber, attempting to reconnect in {} seconds. " +
                                    "Exception was: {}",
                            (reconnectBackoffTimeMillis.get() / 1000),
                            e.getMessage());

                    // Cancel the reset of the backoff
                    if (resetTask != null) {
                        resetTask.cancel(true);
                        resetTask = null;
                    }

                    // Wait before trying to reconnect and increase the backoff duration
                    try {
                        Thread.sleep(reconnectBackoffTimeMillis.get());
                        // exponentially increase the backoff with a max of 30 seconds
                        reconnectBackoffTimeMillis.set(Math.min((reconnectBackoffTimeMillis.get() * 2), 30000));
                    } catch (InterruptedException ignored) {
                        // ignored, already interrupted so the while loop will stop
                    }
                }
            }
        }, "JedisSubscriberThread");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isUp() {
        return redisClusterClient.ping().equals("PONG");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (channel.equals(updateChannel)) {
            onCacheUpdateEvent(message);
        } else {
            LOGGER.debug("Unsupported cache event received of type ");
        }
    }

    private void onCacheUpdateEvent(String message) {
        try {
            CacheUpdateEvent<K, V> event = jsonMapper.readValue(message, deserializeType);
            this.eventPublisher.publishEvent(event);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
