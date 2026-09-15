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

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ServerUrlConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(
                    context -> context.getBeanFactory()
                            .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(ServerUrlConfig.class);

    @Test
    void hasNoServerUrlByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(config(context).hasServerUrl()).isFalse();
        });
    }

    @Test
    void trustsTheLoopbackAndPrivateRangesByDefault() {
        runner.run(context -> {
            var config = config(context);
            assertThat(config.isTrustedProxy("127.0.0.1")).isTrue();
            assertThat(config.isTrustedProxy("::1")).isTrue();
            assertThat(config.isTrustedProxy("10.4.3.2")).isTrue();
            assertThat(config.isTrustedProxy("172.18.0.3")).isTrue();
            assertThat(config.isTrustedProxy("192.168.1.20")).isTrue();
            assertThat(config.isTrustedProxy("fd00::1")).isTrue();
        });
    }

    @Test
    void trustsNoPublicAddressByDefault() {
        runner.run(context -> {
            var config = config(context);
            assertThat(config.isTrustedProxy("203.0.113.7")).isFalse();
            assertThat(config.isTrustedProxy("8.8.8.8")).isFalse();
            assertThat(config.isTrustedProxy("2001:db8::1")).isFalse();
            // 172.32 is outside 172.16/12, and the neighbouring ranges are a classic off-by-one.
            assertThat(config.isTrustedProxy("172.32.0.1")).isFalse();
            assertThat(config.isTrustedProxy("11.0.0.1")).isFalse();
        });
    }

    @Test
    void trustsNothingItCannotCompare() {
        runner.run(context -> {
            var config = config(context);
            assertThat(config.isTrustedProxy(null)).isFalse();
            assertThat(config.isTrustedProxy("")).isFalse();
            assertThat(config.isTrustedProxy("proxy.internal")).isFalse();
        });
    }

    @Test
    void splitsTheServerUrlIntoWhatTheForwardedHeadersWouldHaveSaid() {
        runner.withPropertyValues("ovsx.server.url=https://open-vsx.org").run(context -> {
            var config = config(context);
            assertThat(config.hasServerUrl()).isTrue();
            assertThat(config.getScheme()).isEqualTo("https");
            assertThat(config.getHostAndPort()).isEqualTo("open-vsx.org");
            assertThat(config.getPrefix()).isEmpty();
        });
    }

    @Test
    void keepsAPortAndAPathFromTheServerUrl() {
        runner.withPropertyValues("ovsx.server.url=http://example.com:8080/openvsx").run(context -> {
            var config = config(context);
            assertThat(config.getScheme()).isEqualTo("http");
            assertThat(config.getHostAndPort()).isEqualTo("example.com:8080");
            assertThat(config.getPrefix()).isEqualTo("/openvsx");
        });
    }

    @Test
    void stripsATrailingSlashFromTheServerUrl() {
        runner.withPropertyValues("ovsx.server.url=https://example.com/").run(context -> {
            assertThat(config(context).getPrefix()).isEmpty();
        });
    }

    @Test
    void treatsABlankServerUrlAsUnset() {
        runner.withPropertyValues("ovsx.server.url=   ").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(config(context).hasServerUrl()).isFalse();
        });
    }

    @Test
    void refusesToStartOnAServerUrlThatIsNotAbsolute() {
        runner.withPropertyValues("ovsx.server.url=/openvsx")
                .run(
                        context -> assertThat(context).getFailure()
                                .hasStackTraceContaining("ovsx.server.url must be an absolute URL with a host"));
    }

    @Test
    void refusesToStartOnAServerUrlThatIsNotHttp() {
        runner.withPropertyValues("ovsx.server.url=ftp://example.com")
                .run(
                        context -> assertThat(context).getFailure()
                                .hasStackTraceContaining("ovsx.server.url must use http or https"));
    }

    @Test
    void refusesToStartOnAServerUrlCarryingAQuery() {
        runner.withPropertyValues("ovsx.server.url=https://example.com/openvsx?x=1")
                .run(
                        context -> assertThat(context).getFailure()
                                .hasStackTraceContaining("must not have a query or a fragment"));
    }

    @Test
    void refusesToStartOnATrustedProxyThatIsNotAnAddress() {
        runner.withPropertyValues("ovsx.server.trusted-proxies=10.0.0.0/8,proxy.internal")
                .run(
                        context -> assertThat(context).getFailure()
                                .hasStackTraceContaining(
                                        "ovsx.server.trusted-proxies contains 'proxy.internal', which is not an IP"
                                                + " address or CIDR range"));
    }

    @Test
    void believesEveryPeerOnAStar() {
        runner.withPropertyValues("ovsx.server.trusted-proxies=*").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(config(context).isTrustedProxy("203.0.113.7")).isTrue();
        });
    }

    @Test
    void trustsNobodyOnAnEmptyList() {
        runner.withPropertyValues("ovsx.server.trusted-proxies=").run(context -> {
            assertThat(context).hasNotFailed();
            var config = config(context);
            assertThat(config.isTrustedProxy("127.0.0.1")).isFalse();
            assertThat(config.isTrustedProxy("10.0.0.1")).isFalse();
        });
    }

    /**
     * Nothing else may read the forwarded headers before they are resolved, and the filter has to be
     * holding a configuration that is already initialized when it does.
     */
    @Test
    void registersTheFilterFirstInTheChainOverAnInitializedConfiguration() {
        runner.withPropertyValues("ovsx.server.url=https://openvsx.example").run(context -> {
            assertThat(context).hasNotFailed();
            var registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            assertThat(registration.getFilter()).isInstanceOf(TrustedForwardedHeaderFilter.class);

            // The filter is handed the configuration from inside a proxied @Configuration class, so run a
            // request through the registered instance rather than trust that it got an initialized one.
            var request = new MockHttpServletRequest("GET", "/vscode/gallery/redhat/java/latest");
            request.setRemoteAddr("10.0.0.5");
            request.addHeader(TrustedForwardedHeaderFilter.HOST, "evil.attacker.example");
            var filtered = new HttpServletRequest[1];
            registration.getFilter()
                    .doFilter(
                            request,
                            new MockHttpServletResponse(),
                            (req, res) -> filtered[0] = (HttpServletRequest) req);

            assertThat(filtered[0].getHeader(TrustedForwardedHeaderFilter.HOST)).isEqualTo("openvsx.example");
        });
    }

    private ServerUrlConfig config(AssertableApplicationContext context) {
        return context.getBean(ServerUrlConfig.class);
    }
}
