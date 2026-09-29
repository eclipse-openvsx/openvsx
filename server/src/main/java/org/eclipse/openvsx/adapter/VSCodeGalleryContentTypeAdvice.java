/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.adapter;

import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * The real VS Code Marketplace tags its Gallery API JSON responses with an {@code api-version}
 * media type parameter, e.g. {@code Content-Type: application/json;api-version=3.0-preview.1}, and
 * VS Code clients rely on it being present for compatibility. Spring's own content negotiation would
 * get us most of the way there on its own - a requested media type with more parameters is considered
 * "more specific" than the plain type declared via {@code produces}, so a client sending
 * {@code Accept: application/json;api-version=3.0-preview.1} already gets that parameter echoed back
 * verbatim.
 * <p>
 * But relying on that echo has two problems: it depends entirely on what the client happened to send
 * (a client asking for plain {@code application/json}, or omitting {@code api-version}, gets no
 * version marker at all) and, since it makes the response {@code Content-Type} client-controlled, it
 * turns compression into an unbounded matching problem - Jetty 12.1's {@code CompressionHandler} (used
 * since the Spring Boot 4 migration) matches {@code Content-Type} against
 * {@code server.compression.mime-types} with an exact string comparison, after stripping only the
 * {@code charset} parameter. There is no way to list every {@code api-version} value a client might
 * send, so compression would only ever work for whichever exact strings happen to be enumerated, see
 * <a href="https://github.com/eclipse-openvsx/openvsx/issues/2071">#2071</a>.
 * <p>
 * This advice sidesteps that by always stamping the fixed, server-controlled
 * {@link IVSCodeService#GALLERY_API_VERSION} onto every JSON response from {@link VSCodeAPI},
 * regardless of what the client's {@code Accept} header asked for. That keeps the resulting
 * {@code Content-Type} a single, known literal -
 * {@code application/json;api-version=} + {@link IVSCodeService#GALLERY_API_VERSION} - which must be
 * added to {@code server.compression.mime-types} alongside {@code application/json} for compression
 * to apply to these responses.
 */
@ControllerAdvice(assignableTypes = VSCodeAPI.class)
public class VSCodeGalleryContentTypeAdvice implements ResponseBodyAdvice<Object> {

    static final MediaType GALLERY_JSON = new MediaType(
            MediaType.APPLICATION_JSON,
            Map.of("api-version", IVSCodeService.GALLERY_API_VERSION));

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        // Only touch JSON responses - VSCodeAPI also serves binary assets (application/octet-stream)
        // and those must be left alone.
        if (selectedContentType != null && selectedContentType.equalsTypeAndSubtype(MediaType.APPLICATION_JSON)) {
            response.getHeaders().setContentType(GALLERY_JSON);
        }

        return body;
    }
}
