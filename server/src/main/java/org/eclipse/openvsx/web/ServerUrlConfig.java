/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Where this registry believes it is reachable, and whose word it takes for it.
 * <p>
 * The absolute URLs in a response - download links, icons, the API URLs of an extension - are built
 * from a base URL, and that base URL is derived per request from the {@code X-Forwarded-Host},
 * {@code X-Forwarded-Proto} and {@code X-Forwarded-Prefix} headers. Those are just request headers:
 * anything that can reach the server can set them, and the responses they end up in are cached under
 * keys that do not include the host, so one forged request could serve attacker-chosen URLs to everyone
 * else for the lifetime of the cache entry.
 * <p>
 * This holds the two settings that close that off, and {@link TrustedForwardedHeaderFilter} applies
 * them.
 */
@Configuration
public class ServerUrlConfig {

    /**
     * The absolute URL this registry is reachable at, e.g. {@code https://open-vsx.org}, or
     * {@code https://example.com/openvsx} when it is served under a path.
     * <p>
     * Set this. It is the only setting that makes the base URL independent of the request: the
     * {@code X-Forwarded-*} headers are then ignored entirely, whoever sends them and however the proxy
     * in front is configured, and every node in a cluster agrees on what it emits.
     * <p>
     * Empty by default, which keeps the base URL derived from the request - correct only as far as
     * {@code ovsx.server.trusted-proxies} is correct for the deployment.
     * <p>
     * Property: {@code ovsx.server.url}
     * Default: empty - derive the base URL from the request
     */
    @Value("${ovsx.server.url:}")
    String serverUrl;

    /**
     * The peers whose {@code X-Forwarded-Host}, {@code X-Forwarded-Proto} and {@code X-Forwarded-Prefix}
     * headers are honoured, as IP addresses or CIDR ranges, comma separated. Only consulted when
     * {@code ovsx.server.url} is empty.
     * <p>
     * Defaults to the loopback and private ranges - the same set Tomcat's {@code RemoteIpValve} trusts
     * by default - because that is where a reverse proxy sits in a container or cluster deployment. A
     * request arriving from anywhere else is a client talking to this server directly, and its forwarded
     * headers are ignored.
     * <p>
     * Needs extending for a proxy that reaches this server from a public address; a cloud load
     * balancer that is not on the same private network is the usual case. {@code *} restores the old
     * behaviour of believing every peer, and is unsafe on anything reachable from the open web - prefer
     * {@code ovsx.server.url}, which does not depend on where a request came from at all.
     * <p>
     * Property: {@code ovsx.server.trusted-proxies}
     * Default: {@code 127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,fc00::/7,fe80::/10}
     */
    @Value(
        "${ovsx.server.trusted-proxies:"
                + "127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,fc00::/7,fe80::/10}"
    )
    String[] trustedProxies;

    private @Nullable String scheme;
    private @Nullable String hostAndPort;
    private @Nullable String prefix;
    private boolean trustEveryPeer;
    private List<IpAddressMatcher> trustedProxyMatchers = List.of();

    /**
     * Registered first in the filter chain deliberately: it settles what the {@code X-Forwarded-*} headers
     * say, and everything after it - the security chain included - sees the resolved values rather than
     * whatever the client sent.
     */
    @Bean
    public FilterRegistrationBean<TrustedForwardedHeaderFilter> trustedForwardedHeaderFilter() {
        var registrationBean = new FilterRegistrationBean<TrustedForwardedHeaderFilter>();
        registrationBean.setFilter(new TrustedForwardedHeaderFilter(this));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }

    @PostConstruct
    void init() {
        if (StringUtils.isNotBlank(serverUrl)) {
            var uri = parseServerUrl(serverUrl.trim());
            scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            hostAndPort = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
            prefix = StringUtils.stripEnd(StringUtils.defaultString(uri.getPath()), "/");
        }

        var matchers = new ArrayList<IpAddressMatcher>();
        for (var proxy : trustedProxies) {
            if (StringUtils.isBlank(proxy)) {
                continue;
            }

            var trimmed = proxy.trim();
            if (trimmed.equals("*")) {
                trustEveryPeer = true;
                continue;
            }

            try {
                matchers.add(new IpAddressMatcher(trimmed));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "ovsx.server.trusted-proxies contains '" + trimmed
                                + "', which is not an IP address or CIDR range",
                        e);
            }
        }

        trustedProxyMatchers = List.copyOf(matchers);
    }

    private URI parseServerUrl(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("ovsx.server.url is not a valid URL: " + value, e);
        }

        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalStateException("ovsx.server.url must be an absolute URL with a host: " + value);
        }
        // Only these two reach the URL builder in UrlUtil, and a base URL is a place to fetch from
        // rather than a request of its own, so a query or a fragment on it is a configuration mistake.
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalStateException("ovsx.server.url must use http or https: " + value);
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("ovsx.server.url must not have a query or a fragment: " + value);
        }

        return uri;
    }

    /**
     * Whether the base URL is configured rather than derived from the request.
     */
    public boolean hasServerUrl() {
        return scheme != null;
    }

    /**
     * The scheme of {@code ovsx.server.url}, or {@code null} when it is not set.
     */
    public @Nullable String getScheme() {
        return scheme;
    }

    /**
     * The host of {@code ovsx.server.url}, with its port when that is not the scheme's default, or
     * {@code null} when it is not set.
     */
    public @Nullable String getHostAndPort() {
        return hostAndPort;
    }

    /**
     * The path {@code ovsx.server.url} is served under - empty for a registry at the root of its host -
     * or {@code null} when it is not set.
     */
    public @Nullable String getPrefix() {
        return prefix;
    }

    /**
     * Whether forwarded headers from {@code remoteAddr} are to be believed.
     */
    public boolean isTrustedProxy(@Nullable String remoteAddr) {
        if (trustEveryPeer) {
            return true;
        }
        if (StringUtils.isBlank(remoteAddr)) {
            return false;
        }

        for (var matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(remoteAddr)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Not an address we can compare - a hostname, or something Tomcat could not resolve to a
                // literal. Nothing to trust it on.
                return false;
            }
        }

        return false;
    }
}
