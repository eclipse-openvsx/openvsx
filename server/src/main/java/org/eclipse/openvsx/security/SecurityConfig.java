/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx.security;

import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import org.eclipse.openvsx.web.WebUiProperties;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Declared here rather than component-scanned so that it exists wherever this configuration does -
     * the {@code @WebMvcTest} slices stand up the filter chain without scanning for components.
     */
    @Bean
    public AdminAccessAuthorizationManager adminAccessAuthorizationManager(
            ObjectProvider<EntityManager> entityManagers
    ) {
        return new AdminAccessAuthorizationManager(entityManagers);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            OAuth2UserServices userServices,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            WebUiProperties webUi,
            AdminAccessAuthorizationManager adminAccess
    ) throws Exception {
        var filterChain = http.authorizeHttpRequests(
                registry -> registry
                        .requestMatchers(
                                pathMatchers(
                                        "/*",
                                        "/login/**",
                                        "/oauth2/**",
                                        "/login-providers",
                                        "/user",
                                        "/user/auth-error",
                                        "/logout",
                                        "/actuator/health/**",
                                        "/actuator/metrics",
                                        "/actuator/metrics/**",
                                        "/actuator/prometheus",
                                        "/v3/api-docs/**",
                                        "/swagger-resources/**",
                                        "/swagger-ui/**",
                                        "/webjars/**"))
                        .permitAll()
                        .requestMatchers(
                                pathMatchers(
                                        "/api/*/*/review",
                                        "/api/*/*/review/delete",
                                        "/api/user/publish",
                                        "/api/user/namespace/create"))
                        .authenticated()
                        .requestMatchers(
                                pathMatchers(
                                        "/api/**",
                                        "/vscode/**",
                                        "/documents/**",
                                        "/admin/api/**",
                                        "/admin/report",
                                        "/admin/search-explain"))
                        .permitAll()
                        // Coarse gate only - the caller must have some admin access. Which capability a given
                        // endpoint needs is decided per-handler by AdminService's checkPermission/checkAdminUser.
                        .requestMatchers(pathMatchers("/admin/**"))
                        .access(adminAccess)
                        .requestMatchers(pathMatchers(webUi.getFrontendRoutes()))
                        .permitAll()
                        .requestMatchers(pathMatchers(webUi.getAdditionalRoutes()))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .cors(configurer -> configurer.configure(http))
                .csrf(
                        configurer -> configurer.ignoringRequestMatchers(
                                pathMatchers(
                                        "/api/-/publish",
                                        "/api/-/namespace/create",
                                        "/api/-/query",
                                        "/api/-/trusted-publishing/token",
                                        // authenticated with a personal access token, not with a session
                                        "/api/*/*/delete",
                                        "/vscode/**",
                                        "/admin/api/**")))
                .exceptionHandling(configurer -> configurer.authenticationEntryPoint(new Http403ForbiddenEntryPoint()));

        if (userServices.canLogin()) {
            var webuiUrl = webUi.getUrl();
            var redirectUrl = StringUtils.isEmpty(webuiUrl) ? "/" : webuiUrl;
            var returnTo = new LoginReturnTo(webUi.getFrontendRoutes(), webUi.getAdditionalRoutes());
            filterChain.oauth2Login(configurer -> {
                configurer.defaultSuccessUrl(redirectUrl);
                configurer.successHandler(new CustomAuthenticationSuccessHandler(redirectUrl));
                clientRegistrations.ifAvailable(
                        registrations -> configurer.authorizationEndpoint(
                                endpoint -> endpoint.authorizationRequestResolver(
                                        new ReturnToAuthorizationRequestResolver(registrations, returnTo))));
                configurer.failureUrl(redirectUrl + "?auth-error");
                configurer.userInfoEndpoint(
                        customizer -> customizer.oidcUserService(userServices.getOidc())
                                .userService(userServices.getOauth2()));
            })
                    .logout(configurer -> configurer.logoutSuccessUrl(redirectUrl));
        }

        return filterChain.build();
    }

    private RequestMatcher[] pathMatchers(String... patterns) {
        var pathMatchers = new RequestMatcher[patterns.length];
        for (var i = 0; i < patterns.length; i++) {
            pathMatchers[i] = PathPatternRequestMatcher.withDefaults().matcher(patterns[i]);
        }

        return pathMatchers;
    }
}
