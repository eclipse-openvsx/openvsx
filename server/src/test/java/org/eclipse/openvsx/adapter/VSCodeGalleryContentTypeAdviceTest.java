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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression test for https://github.com/eclipse-openvsx/openvsx/issues/2071 - see
 * {@link VSCodeGalleryContentTypeAdvice} for the full explanation.
 */
class VSCodeGalleryContentTypeAdviceTest {

    private final VSCodeGalleryContentTypeAdvice advice = new VSCodeGalleryContentTypeAdvice();
    private final HttpHeaders headers = new HttpHeaders();
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);

    @Test
    void appliesToAnyReturnType() {
        assertThat(advice.supports(null, JacksonJsonHttpMessageConverter.class)).isTrue();
    }

    // Independent of whatever the client's Accept header asked for (or omitted): the response
    // always carries the server's own, fixed api-version - matching the real VS Code Marketplace
    // and keeping the Content-Type a single known literal for compression to match against.
    @Test
    void alwaysStampsTheFixedGalleryApiVersionOnAJsonResponse() {
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(new Object(), null, MediaType.APPLICATION_JSON, null, request, response);

        assertThat(headers.getContentType().toString())
                .isEqualTo("application/json;api-version=" + IVSCodeService.GALLERY_API_VERSION);
    }

    @Test
    void overridesAnEchoedAcceptParameterWithTheFixedVersion() {
        when(response.getHeaders()).thenReturn(headers);
        // What Spring's content negotiation alone would have picked, had the client asked for a
        // different (or malformed) api-version.
        var echoed = new MediaType("application", "json", Map.of("api-version", "9.9-bogus"));

        advice.beforeBodyWrite(new Object(), null, echoed, null, request, response);

        assertThat(headers.getContentType().toString())
                .isEqualTo("application/json;api-version=" + IVSCodeService.GALLERY_API_VERSION);
    }

    @Test
    void leavesANonJsonContentTypeUntouched() {
        advice.beforeBodyWrite(new Object(), null, MediaType.APPLICATION_OCTET_STREAM, null, request, response);

        // VSCodeAPI also serves binary assets through the same advice scope - must not be touched.
        verifyNoInteractions(response);
    }

    @Test
    void handlesAMissingSelectedContentType() {
        advice.beforeBodyWrite(new Object(), null, null, null, request, response);

        verifyNoInteractions(response);
    }
}
