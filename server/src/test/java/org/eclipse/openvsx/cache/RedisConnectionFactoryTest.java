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
package org.eclipse.openvsx.cache;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Spring cache must share the Redis client the rest of the server uses, and that client's
 * version is pinned apart from the one Spring Boot manages, so this runs it against a real server.
 */
@Tag("integration")
@Testcontainers
class RedisConnectionFactoryTest {

    @Container
    static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1"))
            .withExposedPorts(6379);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class));

    @Test
    void redisCacheRunsOnJedis() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=" + VALKEY.getHost(),
                        "spring.data.redis.port=" + VALKEY.getMappedPort(6379))
                .run(context -> {
                    var connectionFactory = context.getBean(RedisConnectionFactory.class);
                    assertThat(connectionFactory).isInstanceOf(JedisConnectionFactory.class);

                    var cache = RedisCacheManager.builder(CacheConfig.redisCacheWriter(connectionFactory))
                            .build()
                            .getCache("test");
                    assertThat(cache).isNotNull();
                    cache.put("key", "value");
                    assertThat(cache.get("key", String.class)).isEqualTo("value");

                    cache.clear();
                    assertThat(cache.get("key")).isNull();
                });
    }
}
