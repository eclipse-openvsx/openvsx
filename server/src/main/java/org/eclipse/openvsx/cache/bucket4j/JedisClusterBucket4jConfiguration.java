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

import com.giffing.bucket4j.spring.boot.starter.config.cache.CacheManager;
import com.giffing.bucket4j.spring.boot.starter.config.cache.SyncCacheResolver;
import com.giffing.bucket4j.spring.boot.starter.config.condition.ConditionalOnBucket4jEnabled;
import com.giffing.bucket4j.spring.boot.starter.config.condition.ConditionalOnCache;
import com.giffing.bucket4j.spring.boot.starter.config.condition.ConditionalOnFilterConfigCacheEnabled;
import com.giffing.bucket4j.spring.boot.starter.config.condition.ConditionalOnSynchronousPropertyCondition;
import com.giffing.bucket4j.spring.boot.starter.context.properties.Bucket4JBootProperties;
import com.giffing.bucket4j.spring.boot.starter.context.properties.Bucket4JConfiguration;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClusterClient;

@Configuration
@ConditionalOnBucket4jEnabled
@ConditionalOnSynchronousPropertyCondition
@ConditionalOnClass(JedisBasedProxyManager.JedisBasedProxyManagerBuilder.class)
@ConditionalOnBean(RedisClusterClient.class)
@ConditionalOnCache("redis-cluster-jedis")
public class JedisClusterBucket4jConfiguration {

    public final RedisClusterClient redisClusterClient;
    private final String configCacheName;

    public JedisClusterBucket4jConfiguration(RedisClusterClient redisClusterClient, Bucket4JBootProperties properties) {
        this.redisClusterClient = redisClusterClient;
        this.configCacheName = properties.getFilterConfigCacheName();
    }

    @Bean
    @ConditionalOnMissingBean(SyncCacheResolver.class)
    public SyncCacheResolver bucket4RedisResolver() {
        return new JedisClusterCacheResolver(redisClusterClient);
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnFilterConfigCacheEnabled
    public CacheManager<String, Bucket4JConfiguration> configCacheManager() {
        return new JedisClusterCacheManager<>(redisClusterClient, configCacheName, Bucket4JConfiguration.class);
    }

    @Bean
    @ConditionalOnFilterConfigCacheEnabled
    public JedisClusterCacheListener<String, Bucket4JConfiguration> configCacheListener(
            ApplicationEventPublisher eventPublisher
    ) {
        return new JedisClusterCacheListener<>(
                redisClusterClient,
                configCacheName,
                String.class,
                Bucket4JConfiguration.class,
                eventPublisher);
    }
}
