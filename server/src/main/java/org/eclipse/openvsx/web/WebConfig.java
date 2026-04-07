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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.mirror.MirrorExtensionHandlerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.Optional;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private MirrorExtensionHandlerInterceptor mirrorInterceptor;

    @Value("${ovsx.webui.url:}")
    String webuiUrl;

    @Value("${ovsx.webui.frontendRoutes:/extension/**,/namespace/**,/user-settings/**,/admin-dashboard/**}")
    String[] frontendRoutes;

    public WebConfig(Optional<MirrorExtensionHandlerInterceptor> mirrorExtensionHandlerInterceptor) {
        mirrorExtensionHandlerInterceptor.ifPresent(service -> this.mirrorInterceptor = service);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!StringUtils.isEmpty(webuiUrl) && URI.create(webuiUrl).isAbsolute()) {
            // The Web UI is given with an absolute URL, so we need to enable CORS with credentials.
            var authorizedEndpoints = new String[] {
                "/user/**",
                "/logout",
                "/api/*/*/review/**",
                "/api/user/publish",
                "/api/user/namespace/create",
                "/admin/**"
            };
            for (var endpoint : authorizedEndpoints) {
                registry.addMapping(endpoint)
                        .allowedOrigins(webuiUrl)
                        .allowedMethods("GET", "HEAD", "POST", "DELETE")
                        .allowCredentials(true);
            }
            registry.addMapping("/login-providers")
                    .allowedOrigins(webuiUrl);
            registry.addMapping("/documents/**")
                    .allowedOrigins("*");
            registry.addMapping("/api/**")
                    .allowedOrigins("*");
            registry.addMapping("/vscode/**")
                    .allowedOrigins("*");
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (var route : frontendRoutes) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if(mirrorInterceptor != null) {
            registry.addInterceptor(mirrorInterceptor)
                    .addPathPatterns(
                            "/vscode/asset/**",
                            "/vscode/item",
                            "/vscode/gallery/publishers/**",
                            "/vscode/unpkg/**"
                    );
        }
    }
}
