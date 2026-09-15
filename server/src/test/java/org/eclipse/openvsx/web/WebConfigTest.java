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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.Cookie;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CORS mappings this application registers, and which of them depend on where the Web UI lives.
 * <p>
 * Asserted against the registry rather than over HTTP because registration <em>order</em> is part of the
 * behaviour: {@code UrlBasedCorsConfigurationSource} returns the configuration of the first pattern that
 * matches a request and stops looking, so what the map holds and the order it holds it in are together
 * the whole of the policy.
 */
class WebConfigTest {

    private static final String UI_ORIGIN = "https://ui.example.com";

    /** {@link CorsRegistry#getCorsConfigurations()} is protected, and this test is what wants to read it. */
    private static class ReadableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    /** With the public origins left at what the property defaults to; see {@link #defaultsToAnyOrigin()}. */
    private static Map<String, CorsConfiguration> mappingsFor(String webuiUrl) {
        return mappingsFor(webuiUrl, "*");
    }

    private static Map<String, CorsConfiguration> mappingsFor(String webuiUrl, String... publicOrigins) {
        var config = new WebConfig(Optional.empty());
        config.webuiUrl = webuiUrl;
        config.publicCorsOrigins = publicOrigins;
        var registry = new ReadableCorsRegistry();
        config.addCorsMappings(registry);
        return registry.configurations();
    }

    /**
     * The property default, asserted through a context because the tests above construct {@link WebConfig}
     * directly and never see it. Any origin, because the public surface exists to be consumed and a
     * registry that wants it closed can say so; see the field's own documentation.
     */
    @Test
    void defaultsToAnyOrigin() {
        new ApplicationContextRunner()
                .withInitializer(
                        context -> context.getBeanFactory()
                                .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(WebConfig.class)
                .run(
                        context -> assertThat(context.getBean(WebConfig.class).publicCorsOrigins)
                                .containsExactly("*"));
    }

    // An operator who wants the public surface closed to the open web empties the setting, and then there
    // is nothing to register rather than a mapping that allows nothing.
    @Test
    void registersNoPublicMappingsWhenTheSettingIsEmpty() {
        var noOrigins = new String[0];

        assertThat(mappingsFor("", noOrigins)).isEmpty();
        assertThat(mappingsFor(UI_ORIGIN, noOrigins))
                .doesNotContainKeys("/api/**", "/vscode/**", "/documents/**")
                .containsKey("/user/**");
    }

    @Test
    void allowsOnlyTheOriginsTheSettingNames() {
        var mappings = mappingsFor("", "https://vscode.dev", "https://gitpod.io");

        assertThat(mappings.get("/api/**").getAllowedOrigins())
                .containsExactly("https://vscode.dev", "https://gitpod.io");
        assertThat(mappings.get("/api/**").checkOrigin("https://elsewhere.example")).isNull();
        assertThat(mappings.get("/api/**").getAllowCredentials()).isNull();
    }

    // Same reasoning as the pepper keyring: a trailing or doubled comma leaves a blank element behind,
    // and a mapping for a blank origin would match nothing while obscuring what is actually allowed.
    @Test
    void ignoresBlankEntriesInTheSetting() {
        assertThat(mappingsFor("", "https://vscode.dev", "", " ").get("/api/**").getAllowedOrigins())
                .containsExactly("https://vscode.dev");
    }

    // The registry API, the VS Code gallery adapter and the static documents are public, and the browser
    // clients that read them - vscode.dev, Gitpod, anything querying the gallery from page script - need
    // these headers whether or not the Web UI happens to sit on its own origin. Gating them on that left
    // a same-origin deployment serving none at all.
    @Test
    void offersThePublicApiToEveryOriginWithoutAWebUiUrl() {
        var mappings = mappingsFor("");

        assertThat(mappings).containsOnlyKeys("/api/**", "/vscode/**", "/documents/**");
        assertThat(mappings.values())
                .allSatisfy(config -> {
                    assertThat(config.getAllowedOrigins()).containsExactly("*");
                    // Never credentials on a wildcard origin: a browser refuses the combination, and it is
                    // the combination that would make any origin an authenticated reader.
                    assertThat(config.getAllowCredentials()).isNull();
                });
    }

    // A relative value names no origin, so there is no cross-origin Web UI to allow.
    @Test
    void treatsARelativeWebUiUrlAsNoSeparateOrigin() {
        assertThat(mappingsFor("/")).containsOnlyKeys("/api/**", "/vscode/**", "/documents/**");
    }

    @Test
    void allowsTheWebUiOriginWithCredentialsWhenItIsAbsolute() {
        var mappings = mappingsFor(UI_ORIGIN);

        assertThat(mappings).containsKey("/user/**");
        var userMapping = mappings.get("/user/**");
        assertThat(userMapping.getAllowedOrigins()).containsExactly(UI_ORIGIN);
        assertThat(userMapping.getAllowCredentials()).isTrue();
    }

    /**
     * {@code ovsx.webui.url} is a URL; {@code allowedOrigins} is matched against the browser's
     * {@code Origin} header, which never carries a path. A registry whose UI is served under one - at
     * {@code https://example.com/openvsx} - configured a value that could match no browser's header, and
     * the credentialed mappings silently applied to nothing.
     */
    @Test
    void derivesAnOriginFromAWebUiUrlThatCarriesAPath() {
        var mappings = mappingsFor("https://ui.example.com/openvsx");

        var userMapping = mappings.get("/user/**");
        assertThat(userMapping.getAllowedOrigins()).containsExactly(UI_ORIGIN);
        assertThat(userMapping.checkOrigin(UI_ORIGIN)).isEqualTo(UI_ORIGIN);
    }

    // A browser leaves a default port out of the Origin it sends, so a configured one has to come out
    // here too or it would be compared against a header that never has it.
    @Test
    void dropsADefaultPortFromTheDerivedOrigin() {
        assertThat(mappingsFor("https://ui.example.com:443/").get("/user/**").checkOrigin(UI_ORIGIN))
                .isEqualTo(UI_ORIGIN);
    }

    // And keeps one that is not the default - which is the development setup, where the UI runs on 3000.
    @Test
    void keepsANonDefaultPortInTheDerivedOrigin() {
        assertThat(mappingsFor("http://localhost:3000").get("/user/**").getAllowedOrigins())
                .containsExactly("http://localhost:3000");
    }

    @Test
    void neverAllowsCredentialsFromAnOriginItWasNotGiven() {
        var mappings = mappingsFor(UI_ORIGIN);

        assertThat(mappings)
                .allSatisfy((pattern, config) -> {
                    if (Boolean.TRUE.equals(config.getAllowCredentials())) {
                        assertThat(config.getAllowedOrigins()).containsExactly(UI_ORIGIN);
                        assertThat(config.checkOrigin("https://attacker.example")).isNull();
                    }
                });
    }

    /**
     * The trap in hoisting the public mappings: {@code /api/user/publish} and the other credentialed
     * {@code /api} endpoints are all matched by {@code /api/**} as well, and the first pattern registered
     * is the one that answers. Registered the other way round they would quietly serve the public,
     * credential-less configuration instead, and nothing about the request would look wrong.
     */
    @Test
    void registersTheCredentialedApiEndpointsAheadOfTheCatchAll() {
        var patterns = List.copyOf(mappingsFor(UI_ORIGIN).keySet());

        assertThat(patterns).contains("/api/**");
        var catchAll = patterns.indexOf("/api/**");
        assertThat(patterns.subList(0, catchAll))
                .as("credentialed /api mappings must be registered before the catch-all that also matches them")
                .contains(
                        "/api/user/publish",
                        "/api/user/namespace/create",
                        "/api/*/*/review/**",
                        "/api/-/trusted-publishing/status");
    }

    // Left to the browser, an unmarked cookie is Lax in Chromium and has not always been elsewhere. It is
    // what stops a cross-site fetch from carrying the session, so it is stated rather than assumed.
    @Test
    void marksCookiesLaxRatherThanLeavingItToTheBrowser() {
        var supplier = new WebConfig(Optional.empty()).laxCookieSameSiteSupplier();

        assertThat(supplier.getSameSite(new jakarta.servlet.http.Cookie("JSESSIONID", "value")))
                .isEqualTo(Cookie.SameSite.LAX);
    }
}
