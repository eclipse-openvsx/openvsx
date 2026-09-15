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
package org.eclipse.openvsx.cdn;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import org.eclipse.openvsx.web.SurrogateKey;

/**
 * Lets a CDN keep a response until the registry purges it, rather than until it expires.
 * <p>
 * {@code Surrogate-Control} is read by the CDN and stripped before the response reaches anyone
 * else, which is the point of using it instead of raising {@code Cache-Control}: a browser cache
 * cannot be purged, so a long time there would pin an old version in front of a reader with no way
 * to reach them. Endpoints keep the {@code Cache-Control} they have - most of the ones this
 * applies to say {@code no-cache}, so browsers still revalidate every time - except that the
 * revalidation is now answered by the CDN instead of by the registry.
 * <p>
 * Applied to a response only where all of the following hold, which is what makes the long time
 * safe rather than merely long:
 * <ul>
 *     <li>purging is configured, so something will actually drop it;</li>
 *     <li>it carries a {@link SurrogateKey}, so there is a key to drop it by - that is what
 *     restricts this to the responses about one extension or namespace;</li>
 *     <li>its endpoint marked it {@code public}, so the endpoint has already decided a shared
 *     cache may hold it. An authenticated response says nothing of the sort and is left alone;</li>
 *     <li>it is a {@code GET} that succeeded.</li>
 * </ul>
 */
@ControllerAdvice
public class SurrogateCacheControlAdvice implements ResponseBodyAdvice<Object> {

    public static final String HEADER = "Surrogate-Control";

    /**
     * Resolved rather than injected, because this advice is picked up by {@code @WebMvcTest} slices
     * where the configuration bean is not: a context without it is a context with no CDN.
     */
    private final ObjectProvider<CdnPurgeConfig> purgeConfig;

    /**
     * How long the CDN may keep a purgeable response, {@code ovsx.cdn.surrogate-cache-duration}.
     * A week by default: what the CDN holds is dropped when it stops being true, so this bounds how
     * long a purge that never landed stays visible rather than how fresh the registry looks.
     */
    @Value("${ovsx.cdn.surrogate-cache-duration:P7D}")
    private Duration duration;

    public SurrogateCacheControlAdvice(ObjectProvider<CdnPurgeConfig> purgeConfig) {
        this.purgeConfig = purgeConfig;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        var config = purgeConfig.getIfAvailable();
        return config != null && config.isEnabled() && !duration.isNegative() && !duration.isZero();
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        // before the body is written, so the headers are still ours to set
        if (isPurgeable(request, response)) {
            response.getHeaders().set(HEADER, "max-age=" + duration.toSeconds());
        }
        return body;
    }

    private boolean isPurgeable(ServerHttpRequest request, ServerHttpResponse response) {
        if (!HttpMethod.GET.equals(request.getMethod()) || !isSuccessful(response)) {
            return false;
        }
        var headers = response.getHeaders();
        return headers.containsHeader(SurrogateKey.HEADER) && isPublic(headers);
    }

    private static boolean isSuccessful(ServerHttpResponse response) {
        return !(response instanceof ServletServerHttpResponse servletResponse)
                || HttpStatus.valueOf(servletResponse.getServletResponse().getStatus()).is2xxSuccessful();
    }

    private static boolean isPublic(HttpHeaders headers) {
        var cacheControl = headers.getCacheControl();
        return cacheControl != null && cacheControl.contains("public");
    }
}
