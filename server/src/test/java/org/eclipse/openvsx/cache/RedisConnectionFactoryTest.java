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

import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Spring cache must share the Redis client the rest of the server uses, and that client's
 * version is pinned apart from the one Spring Boot manages, so this runs it against a real server.
 * Tagged "integration", so {@code unitTests} skips it; {@code test}, and so CI's {@code check}, runs it.
 * Only standalone host/port is covered, not cluster, SSL, password or pool settings.
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

    // Spring Data Redis 4.0.x is built against Jedis 7.0.0, we run 7.4.1: a mismatch would show as
    // NoSuchMethodError only at runtime, so cover pipelining, SCAN and pub/sub against a real server.
    @Test
    void pipeliningScanAndPubSubWork() throws Exception {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=" + VALKEY.getHost(),
                        "spring.data.redis.port=" + VALKEY.getMappedPort(6379))
                .run(context -> {
                    var factory = context.getBean(RedisConnectionFactory.class);
                    var template = new StringRedisTemplate(factory);

                    template.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
                        c.stringCommands().set("scan:a".getBytes(), "1".getBytes());
                        c.stringCommands().set("scan:b".getBytes(), "2".getBytes());
                        return null;
                    });
                    var keys = new java.util.ArrayList<String>();
                    try (var cursor = template.scan(ScanOptions.scanOptions().match("scan:*").build())) {
                        cursor.forEachRemaining(keys::add);
                    }
                    assertThat(keys).containsExactlyInAnyOrder("scan:a", "scan:b");

                    var received = new CopyOnWriteArrayList<String>();
                    var container = new RedisMessageListenerContainer();
                    container.setConnectionFactory(factory);
                    container.addMessageListener(
                            (m, p) -> received.add(new String(m.getBody())),
                            new ChannelTopic("ch"));
                    container.afterPropertiesSet();
                    container.start();
                    try {
                        org.awaitility.Awaitility.await().untilAsserted(() -> {
                            template.convertAndSend("ch", "hi");
                            assertThat(received).contains("hi");
                        });
                    } finally {
                        container.destroy();
                    }
                });
    }
}
