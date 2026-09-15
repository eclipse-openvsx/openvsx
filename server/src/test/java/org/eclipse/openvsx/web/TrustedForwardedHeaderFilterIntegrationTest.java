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

import java.util.ArrayList;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.servlet.RegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import org.eclipse.openvsx.AbstractPostgresContainerTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unit tests exercise the filter directly, which says nothing about whether the assembled chain
 * puts it where it has to be. These run against a booted context over a real socket.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "ovsx.server.url=https://openvsx.example"
)
@AutoConfigureTestRestTemplate
class TrustedForwardedHeaderFilterIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ApplicationContext context;

    /**
     * {@code /login-providers} answers with an absolute URL built from {@code UrlUtil.getBaseUrl}, so
     * what it returns is what a forged header would have reached.
     */
    @Test
    void aForgedHostDoesNotReachTheUrlsInAResponse() {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-Host", "evil.attacker.example");
        headers.add("X-Forwarded-Proto", "https");
        headers.add("X-Forwarded-Prefix", "/attacker-controlled");

        var response = restTemplate
                .exchange("/login-providers", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getBody())
                .contains("https://openvsx.example/oauth2/authorization/")
                .doesNotContain("evil.attacker.example")
                .doesNotContain("attacker-controlled");
    }

    /**
     * Everything the container ends up running, ordered the way Spring Boot orders it. A filter
     * registered ahead of this one would read the headers as they arrived.
     * <p>
     * Strictly ahead: Boot registers its character encoding filter at the same order, and the two are
     * therefore in no defined order relative to each other, which is harmless because it does not read
     * these headers.
     */
    @Test
    void nothingIsRegisteredAheadOfIt() {
        var ours = context.getBean("trustedForwardedHeaderFilter", RegistrationBean.class);
        var ahead = new ArrayList<String>();
        context.getBeansOfType(RegistrationBean.class).forEach((name, bean) -> {
            if (bean != ours && bean.getOrder() < ours.getOrder()) {
                ahead.add(name + " at " + bean.getOrder());
            }
        });
        context.getBeansOfType(Filter.class).forEach((name, filter) -> {
            if (filter instanceof Ordered ordered && ordered.getOrder() < ours.getOrder()) {
                ahead.add(name + " at " + ordered.getOrder());
            }
        });

        assertThat(ahead).isEmpty();
    }
}
