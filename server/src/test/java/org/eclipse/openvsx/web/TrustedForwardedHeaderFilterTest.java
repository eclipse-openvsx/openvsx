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

import java.util.Collections;
import java.util.function.Consumer;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.eclipse.openvsx.util.UrlUtil;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedForwardedHeaderFilterTest {

    /**
     * A peer that is not a reverse proxy on a private network. Everything it claims about the host is a
     * claim by a client, and the response it gets is the one that ends up in a shared cache entry.
     */
    private static final String UNTRUSTED_PEER = "203.0.113.7";

    private static final String TRUSTED_PROXY = "10.0.0.5";

    @Test
    void derivesTheHostFromTheRequestWhenNothingIsForwarded() {
        withDefaults(config -> {
            var request = request(UNTRUSTED_PEER);
            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    @Test
    void ignoresAForgedHostFromAnUntrustedPeer() {
        withDefaults(config -> {
            var request = request(UNTRUSTED_PEER);
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    @Test
    void ignoresAForgedPrefixFromAnUntrustedPeer() {
        withDefaults(config -> {
            var request = request(UNTRUSTED_PEER);
            request.addHeader(TrustedForwardedHeaderFilter.PREFIX, "/attacker-controlled");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    /**
     * {@code UrlUtil} rejects a scheme it cannot build a URL for by throwing, which on an unfiltered
     * request made a header enough to turn any of these endpoints into a 500.
     */
    @Test
    void ignoresAnUnsupportedSchemeFromAnUntrustedPeer() {
        withDefaults(config -> {
            var request = request(UNTRUSTED_PEER);
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "ftp");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    @Test
    void honoursTheHostForwardedByATrustedProxy() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "openvsx.example");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    @Test
    void honoursThePrefixForwardedByATrustedProxy() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "example.com");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");
            request.addHeader(TrustedForwardedHeaderFilter.PREFIX, "/openvsx");

            assertThat(baseUrl(request, config)).isEqualTo("https://example.com/openvsx");
        });
    }

    @Test
    void honoursTheForwardedPort() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "example.com:8443");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://example.com:8443");
        });
    }

    /**
     * The loopback default is what keeps a development setup, and a proxy on the same host, working
     * without configuring anything.
     */
    @Test
    void trustsLoopbackByDefault() {
        withDefaults(config -> {
            var request = request("127.0.0.1", "localhost", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "openvsx.example");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    /**
     * A proxy that appends to the header rather than overwriting it leaves the client's own value in
     * front of its own, so the first value is attacker-supplied even though the peer is trusted.
     */
    @Test
    void takesTheLastValueWhenAProxyAppendsToTheHeader() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example, openvsx.example");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "http, https");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    @Test
    void takesTheLastValueWhenTheHeaderArrivesOnSeveralLines() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "openvsx.example");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
        });
    }

    /**
     * A path may contain a comma, so the prefix is taken whole rather than as a list.
     */
    @Test
    void doesNotSplitThePrefixOnCommas() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "example.com");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");
            request.addHeader(TrustedForwardedHeaderFilter.PREFIX, "/a,b");

            assertThat(baseUrl(request, config)).isEqualTo("https://example.com/a,b");
        });
    }

    @Test
    void aConfiguredServerUrlOverridesEveryHeader() {
        withConfig(
                config -> {
                    // From a trusted proxy, so only the configured URL can be what rules this out.
                    var request = request(TRUSTED_PROXY, "server", 8080, "http");
                    request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example");
                    request.addHeader(TrustedForwardedHeaderFilter.PROTO, "http");
                    request.addHeader(TrustedForwardedHeaderFilter.PREFIX, "/attacker-controlled");

                    assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
                },
                "ovsx.server.url=https://openvsx.example");
    }

    @Test
    void aConfiguredServerUrlKeepsItsPathAndPort() {
        withConfig(
                config -> {
                    var request = request(UNTRUSTED_PEER, "server", 8080, "http");
                    assertThat(baseUrl(request, config)).isEqualTo("https://example.com:8443/openvsx");
                },
                "ovsx.server.url=https://example.com:8443/openvsx/");
    }

    @Test
    void aConfiguredServerUrlAppliesWithoutAnyForwardedHeaders() {
        withConfig(
                config -> {
                    var request = request(UNTRUSTED_PEER, "server", 8080, "http");
                    assertThat(baseUrl(request, config)).isEqualTo("https://openvsx.example");
                },
                "ovsx.server.url=https://openvsx.example");
    }

    @Test
    void aStarInTrustedProxiesBelievesEveryPeer() {
        withConfig(
                config -> {
                    var request = request(UNTRUSTED_PEER, "server", 8080, "http");
                    request.addHeader(TrustedForwardedHeaderFilter.HOST, "whatever.example");
                    request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

                    assertThat(baseUrl(request, config)).isEqualTo("https://whatever.example");
                },
                "ovsx.server.trusted-proxies=*");
    }

    /**
     * An IPv6 literal is bracketed and made of colons, so the port split has to look for one after
     * the closing bracket rather than for the last colon in the value.
     */
    @Test
    void keepsAnIpv6HostForwardedByATrustedProxyIntact() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "[2001:db8::1]");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://[2001:db8::1]");
        });
    }

    @Test
    void keepsAnIpv6HostAndPortForwardedByATrustedProxyIntact() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY, "server", 8080, "http");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "[2001:db8::1]:8443");
            request.addHeader(TrustedForwardedHeaderFilter.PROTO, "https");

            assertThat(baseUrl(request, config)).isEqualTo("https://[2001:db8::1]:8443");
        });
    }

    @Test
    void aConfiguredServerUrlMayBeAnIpv6Literal() {
        withConfig(
                config -> {
                    var request = request(UNTRUSTED_PEER, "server", 8080, "http");
                    assertThat(baseUrl(request, config)).isEqualTo("https://[2001:db8::1]");
                },
                "ovsx.server.url=https://[2001:db8::1]");
    }

    @Test
    void aConfiguredServerUrlMayBeAnIpv6LiteralWithAPort() {
        withConfig(
                config -> {
                    var request = request(UNTRUSTED_PEER, "server", 8080, "http");
                    assertThat(baseUrl(request, config)).isEqualTo("https://[2001:db8::1]:8443");
                },
                "ovsx.server.url=https://[2001:db8::1]:8443");
    }

    @Test
    void hidesTheHeadersItIgnoresFromEveryWayOfReadingThem() {
        withDefaults(config -> {
            var request = request(UNTRUSTED_PEER);
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example");
            request.addHeader("X-Real-Ip", "203.0.113.7");

            var filtered = filter(request, config);
            assertThat(filtered.getHeader("x-forwarded-host")).isNull();
            assertThat(Collections.list(filtered.getHeaders(TrustedForwardedHeaderFilter.HOST))).isEmpty();
            assertThat(Collections.list(filtered.getHeaderNames()))
                    .doesNotContain(TrustedForwardedHeaderFilter.HOST)
                    .contains("X-Real-Ip");
        });
    }

    @Test
    void leavesTheResolvedHeadersReadableAndOtherHeadersAlone() {
        withDefaults(config -> {
            var request = request(TRUSTED_PROXY);
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example, openvsx.example");
            request.addHeader("X-Real-Ip", "203.0.113.7");

            var filtered = filter(request, config);
            assertThat(filtered.getHeader(TrustedForwardedHeaderFilter.HOST)).isEqualTo("openvsx.example");
            assertThat(Collections.list(filtered.getHeaders("x-forwarded-host"))).containsExactly("openvsx.example");
            assertThat(Collections.list(filtered.getHeaderNames()))
                    .contains(TrustedForwardedHeaderFilter.HOST, "X-Real-Ip");
            assertThat(filtered.getHeader("X-Real-Ip")).isEqualTo("203.0.113.7");
        });
    }

    private MockHttpServletRequest request(String remoteAddr) {
        return request(remoteAddr, "openvsx.example", 443, "https");
    }

    private MockHttpServletRequest request(String remoteAddr, String serverName, int port, String scheme) {
        var request = new MockHttpServletRequest("GET", "/vscode/gallery/redhat/java/latest");
        request.setRemoteAddr(remoteAddr);
        request.setServerName(serverName);
        request.setServerPort(port);
        request.setScheme(scheme);

        return request;
    }

    /**
     * The base URL the endpoints would build, read the way they read it - off the bound request rather
     * than from one passed around.
     */
    private String baseUrl(MockHttpServletRequest request, ServerUrlConfig config) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(filter(request, config)));
        try {
            return UrlUtil.getBaseUrl();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private HttpServletRequest filter(MockHttpServletRequest request, ServerUrlConfig config) {
        var filtered = new HttpServletRequest[1];
        try {
            new TrustedForwardedHeaderFilter(config)
                    .doFilter(
                            request,
                            new MockHttpServletResponse(),
                            (req, res) -> filtered[0] = (HttpServletRequest) req);
        } catch (Exception e) {
            throw new AssertionError("the filter itself failed", e);
        }

        assertThat(filtered[0]).as("the filter did not continue the chain").isNotNull();
        return filtered[0];
    }

    private void withDefaults(Consumer<ServerUrlConfig> test) {
        withConfig(test);
    }

    /**
     * Binds {@link ServerUrlConfig} the way the application does, so the defaults under test are the ones
     * that ship rather than a copy of them.
     */
    private void withConfig(Consumer<ServerUrlConfig> test, String... properties) {
        new ApplicationContextRunner()
                .withInitializer(
                        context -> context.getBeanFactory()
                                .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ServerUrlConfig.class)
                .withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(ServerUrlConfig.class));
                });
    }
}
