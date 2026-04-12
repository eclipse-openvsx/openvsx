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
package org.eclipse.openvsx.ratelimit.cache;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnBean(RateLimitConfig.class)
public class RateLimitCacheService extends JedisPubSub {

    public static final String CACHE_MANAGER = "rateLimitCacheManager";
    public static final String CACHE_CUSTOMER = "ratelimit.customer";
    public static final String CACHE_TIER = "ratelimit.tier";
    public static final String CACHE_TOKEN = "ratelimit.token";

    private static final String CONFIG_UPDATE_CHANNEL = "ratelimit.config";

    private final Logger logger = LoggerFactory.getLogger(RateLimitCacheService.class);

    private final JedisCluster jedisCluster;
    private final CacheManager cacheManager;
    private final ConfigCacheUpdateListener configCacheListener;
    private final ApplicationEventPublisher eventPublisher;

    public RateLimitCacheService(
            JedisCluster jedisCluster,
            @Qualifier(CACHE_MANAGER) CacheManager cacheManager,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jedisCluster = jedisCluster;
        this.cacheManager = cacheManager;
        this.configCacheListener = new ConfigCacheUpdateListener(jedisCluster);
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initialize() {
        configCacheListener.startSubscriber();
    }

    @PreDestroy
    public void shutdown() {
        configCacheListener.shutdown();
    }

    public void publishConfigUpdate(String cacheName, String key) {
        logger.debug("Publish update rate-limit config {}: {}", cacheName, key);
        jedisCluster.publish(CONFIG_UPDATE_CHANNEL, cacheName + ":" + key);
    }

    public void evictCustomerCache() {
        logger.debug("Evict customer cache");
        var cache = cacheManager.getCache(CACHE_CUSTOMER);
        if (cache != null) {
            cache.clear();
        }

        eventPublisher.publishEvent(new ConfigurationChanged());
    }

    public void evictCustomer(String[] customerIds) {
        logger.debug("Evict {} customer(s)", customerIds.length);
        var cache = cacheManager.getCache(CACHE_CUSTOMER);
        if (cache != null) {
            for (var id : customerIds) {
                cache.evict(Long.valueOf(id));
            }
        }

        eventPublisher.publishEvent(new ConfigurationChanged());
    }

    public void evictTierCache() {
        logger.debug("Evict tier cache");
        var cache = cacheManager.getCache(CACHE_TIER);
        if (cache != null) {
            cache.clear();
        }
    }

    public void evictTokenCache() {
        logger.debug("Evict token cache");
        var cache = cacheManager.getCache(CACHE_TOKEN);
        if (cache != null) {
            cache.clear();
        }
    }

    public void evictTokens(String[] tokens) {
        logger.debug("Evict {} token(s)", tokens.length);
        var cache = cacheManager.getCache(CACHE_TOKEN);
        if (cache != null) {
            for (var token : tokens) {
                cache.evict(token);
            }
        }
    }

    private class ConfigCacheUpdateListener extends JedisPubSub {
        private final JedisCluster jedisCluster;

        // Redis subscriber state
        private volatile Thread subscriberThread;
        private volatile boolean running = true;

        public ConfigCacheUpdateListener(JedisCluster jedisCluster) {
            this.jedisCluster = jedisCluster;
        }

        void startSubscriber() {
            subscriberThread = new Thread(this::subscribeLoop, "RateLimitConfigSubscriber");
            subscriberThread.setDaemon(true);
            subscriberThread.start();
        }

        void shutdown() {
            running = false;
            if (isSubscribed()) {
                unsubscribe();
            }
            if (subscriberThread != null) {
                subscriberThread.interrupt();
            }
        }

        private void subscribeLoop() {
            AtomicInteger backoffMs = new AtomicInteger(1000);
            try (var executor = Executors.newSingleThreadScheduledExecutor(
                    new NamedThreadFactory("rate-limit-config-subscriber-reconnect")
            )) {
                while (running && !Thread.currentThread().isInterrupted()) {
                    ScheduledFuture<?> resetTask = null;
                    try {
                        resetTask = executor.schedule(() -> backoffMs.set(1000), 10, TimeUnit.SECONDS);
                        logger.debug("Subscribing to rate-limit config update channel");
                        jedisCluster.subscribe(this, CONFIG_UPDATE_CHANNEL);
                    } catch (Exception e) {
                        if (!running) break;
                        logger.warn("Rate-limit config subscriber disconnected, reconnecting in {}s: {}",
                                backoffMs.get() / 1000, e.getMessage());
                        if (resetTask != null) resetTask.cancel(true);
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

        @Override
        public void onMessage(String channel, String message) {
            if (CONFIG_UPDATE_CHANNEL.equals(channel)) {
                logger.debug("Received rate-limit config update notification from another pod");

                String[] arr = message.split(":");
                if (arr.length == 0) {
                    logger.error("could not process config update: {}", message);
                    return;
                }

                var cacheType = arr[0];
                var cacheKeys = arr.length > 1 ? arr[1] : "";

                switch (cacheType) {
                    case CACHE_CUSTOMER:
                        if (cacheKeys.isBlank()) {
                            evictCustomerCache();
                        } else {
                            evictCustomer(cacheKeys.split(","));
                        }
                        break;

                    case CACHE_TIER:
                        evictTierCache();
                        evictCustomerCache();
                        break;

                    case CACHE_TOKEN:
                        if (cacheKeys.isBlank()) {
                            evictTokenCache();
                        } else {
                            evictTokens(cacheKeys.split(","));
                        }
                        break;

                    default:
                        logger.warn("Received unknown message {}", message);
                        break;
                }
            }
        }
    }
}
