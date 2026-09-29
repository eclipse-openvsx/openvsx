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

import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.common.util.StringUtils;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;

public class JedisUtil {

    private JedisUtil() {
    }

    public static JedisClientConfig getClientConfig(DataRedisProperties properties) {
        var configBuilder = DefaultJedisClientConfig.builder();
        var username = properties.getUsername();
        if (StringUtils.isNotEmpty(username)) {
            configBuilder.user(username);
        }
        var password = properties.getPassword();
        if (StringUtils.isNotEmpty(password)) {
            configBuilder.password(password);
        }

        configBuilder.ssl(properties.getSsl().isEnabled());
        return configBuilder.build();
    }

    public static Set<HostAndPort> getNodes(DataRedisProperties properties) {
        return properties.getCluster().getNodes().stream()
                .map(HostAndPort::from)
                .collect(Collectors.toSet());
    }
}
