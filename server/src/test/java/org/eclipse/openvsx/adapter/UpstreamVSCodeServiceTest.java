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

import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UrlConfigService;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies that proxied responses use our own response headers rather than
 * trusting the upstream registry's headers verbatim.
 */
public class UpstreamVSCodeServiceTest {

    @Test
    void browseDoesNotTrustUpstreamContentType() {
        var nonRedirecting = new RestTemplate();
        var server = MockRestServiceServer.bindTo(nonRedirecting).build();
        server.expect(requestTo("http://upstream.example/vscode/unpkg/foo/bar/1.0.0/extension/readme.md"))
                .andRespond(withSuccess("### blablabla", MediaType.TEXT_MARKDOWN));

        server.expect(requestTo("http://upstream.example/vscode/unpkg/foo/bar/1.0.0/extension/readme.html"))
                .andRespond(withSuccess("<script>alert(1)</script>", MediaType.TEXT_HTML));

        var urlConfig = Mockito.mock(UrlConfigService.class);
        Mockito.when(urlConfig.getUpstreamUrl()).thenReturn("http://upstream.example");

        var service = new UpstreamVSCodeService(
                new RestTemplate(),
                Optional.empty(),
                nonRedirecting,
                urlConfig,
                Mockito.mock(ExtensionValidator.class));

        var response = service.browse("foo", "bar", "1.0.0", null, "extension/readme.md");

        assertEquals(
                "text/plain;charset=UTF-8",
                Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertNotNull(
                response.getHeaders().getFirst("Content-Security-Policy"),
                "proxied files must carry a Content-Security-Policy");

        response = service.browse("foo", "bar", "1.0.0", null, "extension/readme.html");

        assertEquals(
                "text/plain;charset=UTF-8",
                Objects.requireNonNull(response.getHeaders().getContentType()).toString());
        assertNotNull(
                response.getHeaders().getFirst("Content-Security-Policy"),
                "proxied files must carry a Content-Security-Policy");
    }
}
