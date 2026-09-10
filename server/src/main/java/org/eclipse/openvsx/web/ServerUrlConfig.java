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
 * Where this registry is reachable, which the absolute URLs in a response are built from.
 * {@link TrustedForwardedHeaderFilter} applies these settings; doc/configuration.md, Server URL says which
 * to use for which deployment.
 */
@Configuration
public class ServerUrlConfig {

    /**
     * When set, the base URL is this and the {@code X-Forwarded-*} headers are ignored entirely.
     * <p>
     * Property: {@code ovsx.server.url}
     * Default: empty - derive the base URL from the request
     */
    @Value("${ovsx.server.url:}")
    String serverUrl;

    /**
     * The peers whose forwarded headers are read, as IP addresses or CIDR ranges. Only consulted when
     * {@code ovsx.server.url} is empty. The default is the loopback and private ranges, the same set
     * Tomcat's {@code RemoteIpValve} trusts; {@code *} reads them from every peer.
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
     * First in the chain, so everything after it - the security chain included - sees resolved headers.
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

    public boolean hasServerUrl() {
        return scheme != null;
    }

    public @Nullable String getScheme() {
        return scheme;
    }

    public @Nullable String getHostAndPort() {
        return hostAndPort;
    }

    public @Nullable String getPrefix() {
        return prefix;
    }

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
