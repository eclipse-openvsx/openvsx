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
import com.giffing.bucket4j.spring.boot.starter.config.cache.CacheUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.RedisClusterClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class JedisClusterCacheManager<K, V> implements CacheManager<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisClusterCacheManager.class);

    private final RedisClusterClient redisClusterClient;
    private final String cacheName;
    private final Class<V> valueType;
    private final JsonMapper jsonMapper;
    private final String updateChannel;

    /**
     * @param redisClusterClient The RedisClusterClient to use for reading/writing data to the cache
     * @param cacheName The name of the cache.
     * @param valueType The type of the data. This is required for parsing and should always match the V of this class.
     */
    public JedisClusterCacheManager(RedisClusterClient redisClusterClient, String cacheName, Class<V> valueType) {
        this.redisClusterClient = redisClusterClient;
        this.cacheName = cacheName;
        this.valueType = valueType;

        this.jsonMapper = JsonMapper.builder().build();
        this.updateChannel = cacheName.concat(":update");
    }

    @Override
    public V getValue(K key) {
        try {
            String serializedValue = redisClusterClient.hget(cacheName, jsonMapper.writeValueAsString(key));
            return serializedValue != null ? jsonMapper.readValue(serializedValue, this.valueType) : null;
        } catch (JacksonException e) {
            LOGGER.warn(
                    "Exception occurred while retrieving key '{}' from cache '{}'. Message: {}",
                    key,
                    cacheName,
                    e.getMessage());
            return null;
        }
    }

    @Override
    public void setValue(K key, V value) {
        try {
            V oldValue = getValue(key);

            String serializedKey = jsonMapper.writeValueAsString(key);
            String serializedValue = jsonMapper.writeValueAsString(value);
            redisClusterClient.hset(this.cacheName, serializedKey, serializedValue);

            // publish an update event if the key already existed
            if (oldValue != null) {
                CacheUpdateEvent<K, V> updateEvent = new CacheUpdateEvent<>(key, oldValue, value);
                redisClusterClient.publish(this.updateChannel, jsonMapper.writeValueAsString(updateEvent));
            }
        } catch (JacksonException e) {
            LOGGER.warn(
                    "Exception occurred while setting key '{}' in cache '{}'. Message: {}",
                    key,
                    cacheName,
                    e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
