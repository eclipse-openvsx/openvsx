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

import java.time.Duration;

import com.giffing.bucket4j.spring.boot.starter.config.cache.AbstractCacheResolverTemplate;
import com.giffing.bucket4j.spring.boot.starter.config.cache.SyncCacheResolver;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AbstractProxyManager;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import redis.clients.jedis.RedisClusterClient;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JedisClusterCacheResolver extends AbstractCacheResolverTemplate<byte[]> implements SyncCacheResolver {

    private final RedisClusterClient redisClusterClient;

    public JedisClusterCacheResolver(RedisClusterClient redisClusterClient) {
        this.redisClusterClient = redisClusterClient;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public byte[] castStringToCacheKey(String key) {
        return key.getBytes(UTF_8);
    }

    @Override
    public AbstractProxyManager<byte[]> getProxyManager(String cacheName) {
        var clientSideConfig = ClientSideConfig.getDefault().withExpirationAfterWriteStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)));

        return JedisBasedProxyManager.builderFor(redisClusterClient)
                .withClientSideConfig(clientSideConfig)
                .build();
    }
}
