/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.web;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.eclipse.openvsx.mirror.MirrorExtensionHandlerInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private MirrorExtensionHandlerInterceptor mirrorInterceptor;

    @Value("${ovsx.webui.url:}")
    String webuiUrl;

    @Value(
        "${ovsx.webui.frontendRoutes:/extension/**,/namespace/**,/search,/user-settings/**,/publish,/admin-dashboard/**}"
    )
    String[] frontendRoutes;

    /**
     * The origins allowed to read the public, unauthenticated surface - the registry API, the VS Code
     * gallery adapter and the static documents - from a browser. Comma separated; {@code *} for any.
     * <p>
     * Any by default, because that surface exists to be consumed: a public registry is read by clients
     * that are not its own Web UI, and the ones running in a browser (VS Code for the Web, Gitpod, Theia,
     * anything querying the gallery from page script) get nothing back without these headers. Clients
     * that are not browsers - VS Code desktop fetches the gallery from its node extension host - never
     * consult them and are unaffected by whatever this says.
     * <p>
     * Worth narrowing, or emptying, on a registry that is not meant to be read from the open web. The
     * endpoints are unauthenticated, so this is not what keeps their contents private - anything that can
     * reach them can read them - but on an instance reachable only from an internal network it does stop
     * a page in an employee's browser being used to reach it from outside. Set to an empty value to
     * register no public CORS mappings at all.
     * <p>
     * Deliberately not derived from {@code ovsx.webui.url}: where the Web UI is served from has nothing
     * to do with which third parties may read the API, and tying the two together left a registry serving
     * its UI from the same origin as its API emitting no CORS headers for the public surface at all.
     * <p>
     * Property: {@code ovsx.cors.public-origins}
     * Default: {@code *}
     */
    @Value("${ovsx.cors.public-origins:*}")
    String[] publicCorsOrigins;

    public WebConfig(Optional<MirrorExtensionHandlerInterceptor> mirrorExtensionHandlerInterceptor) {
        mirrorExtensionHandlerInterceptor.ifPresent(service -> this.mirrorInterceptor = service);
    }

    /**
     * Registration order is load-bearing. {@code UrlBasedCorsConfigurationSource} walks its mappings in the
     * order they were added and returns the configuration of the <em>first</em> pattern that matches - it
     * neither combines them nor prefers the more specific one. Every credentialed mapping below is also
     * matched by the {@code /api/**} catch-all, so the credentialed ones have to be registered first or
     * they would silently lose their configuration to it.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var webuiOrigin = webuiOrigin();
        if (webuiOrigin != null) {
            // The Web UI is on an origin of its own, so we need to enable CORS with credentials. Where it
            // is served from the same origin as the API these are unnecessary: a same-origin request does
            // not use CORS at all.
            var authorizedEndpoints = new String[] {
                "/user/**",
                "/logout",
                "/api/*/*/review/**",
                "/api/user/publish",
                "/api/user/namespace/create",
                "/api/-/trusted-publishing/status",
                "/admin/**"
            };
            for (var endpoint : authorizedEndpoints) {
                registry.addMapping(endpoint)
                        .allowedOrigins(webuiOrigin)
                        .allowedMethods("GET", "HEAD", "POST", "DELETE", "PUT")
                        .allowCredentials(true);
            }
            registry.addMapping("/login-providers")
                    .allowedOrigins(webuiOrigin);
        }

        // The public, unauthenticated surface, on its own setting rather than on wherever the Web UI
        // happens to live - see publicCorsOrigins above for why the two were never related. Never with
        // credentials, whatever it names: it is the pairing of a permissive origin with credentials that
        // turns any origin into an authenticated reader, and nothing here needs a session.
        var publicOrigins = publicCorsOrigins();
        if (publicOrigins.length > 0) {
            registry.addMapping("/api/**")
                    .allowedOrigins(publicOrigins);
            registry.addMapping("/vscode/**")
                    .allowedOrigins(publicOrigins);
            registry.addMapping("/documents/**")
                    .allowedOrigins(publicOrigins);
        }
    }

    /**
     * The configured public origins, without the blanks.
     * <p>
     * A trailing or doubled comma leaves an empty element behind, and Spring trims each one, so an
     * all-whitespace entry arrives blank too. Registering a mapping for a blank origin would match no
     * request and only obscure what the setting actually allows.
     */
    private String[] publicCorsOrigins() {
        if (publicCorsOrigins == null) {
            return new String[0];
        }

        return Arrays.stream(publicCorsOrigins).filter(StringUtils::isNotBlank).toArray(String[]::new);
    }

    /**
     * The origin the Web UI is served from, or {@code null} when it does not have one of its own.
     * <p>
     * Derived rather than used as configured, because the two are not the same thing.
     * {@code allowedOrigins} is matched against the browser's {@code Origin} header, which is a
     * serialized origin - a scheme, a host and a non-default port, and never a path.
     * {@code ovsx.webui.url} is a URL and may carry one, for a registry served at
     * {@code https://example.com/openvsx} say; {@code CorsConfiguration.checkOrigin} trims one trailing
     * slash and then compares what is left exactly, so a configured path meant the credentialed mappings
     * matched nothing and CORS quietly did not apply to them at all.
     * <p>
     * A default port is dropped for the same reason: a browser leaves it out of the header it sends.
     */
    private @Nullable String webuiOrigin() {
        if (StringUtils.isEmpty(webuiUrl)) {
            return null;
        }

        var uri = URI.create(webuiUrl);
        // Relative, or absolute but with no authority to take a host from - neither names an origin, so
        // there is no separate Web UI to allow.
        if (!uri.isAbsolute() || uri.getHost() == null) {
            return null;
        }

        var scheme = uri.getScheme();
        var origin = new StringBuilder(scheme).append("://").append(uri.getHost());
        var defaultPort = switch (scheme.toLowerCase(Locale.ROOT)) {
            case "https" -> 443;
            case "http" -> 80;
            default -> -1;
        };
        if (uri.getPort() != -1 && uri.getPort() != defaultPort) {
            origin.append(':').append(uri.getPort());
        }

        return origin.toString();
    }

    /**
     * Marks every cookie this application sets {@code SameSite=Lax}.
     *
     * <p>Without the attribute the decision falls to the browser, and browsers do not agree: Chromium
     * treats an unmarked cookie as Lax, others have not always. That left the session cookie's behaviour
     * on a cross-site {@code fetch} - whether it rides along and makes the request an authenticated one -
     * a property of the visitor's browser rather than of this server. It is the last thing standing
     * between a misconfigured CORS layer and a credentialed cross-origin read of {@code /user/**}, which
     * is not a job to leave to a default.
     *
     * <p>Lax and not Strict deliberately. Strict withholds the cookie from cross-site top-level
     * navigations too, which is exactly what the return leg of an OAuth2 login is: the provider redirects
     * the browser back here, and Spring Security needs the session to find the authorization request it
     * saved. Lax sends the cookie on that navigation and withholds it from the subresource requests -
     * {@code fetch}, {@code XMLHttpRequest}, images, frames - that an attacking page would have to use.
     */
    @Bean
    CookieSameSiteSupplier laxCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.ofLax();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (var route : frontendRoutes) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (mirrorInterceptor != null) {
            registry.addInterceptor(mirrorInterceptor)
                    .addPathPatterns(
                            "/vscode/asset/**",
                            "/vscode/item",
                            "/vscode/gallery/publishers/**",
                            "/vscode/unpkg/**");
        }
    }
}
