/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.adapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.eclipse.openvsx.AbstractPostgresContainerTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof, against the real running application (real embedded Jetty, real
 * {@link VSCodeGalleryContentTypeAdvice} and {@link VSCodeGalleryCompressionConfig} wiring - not the
 * synthetic servlet {@link VSCodeGalleryCompressionTest} uses), that a real request to
 * {@code /vscode/gallery/extensionquery} comes back compressed.
 * <p>
 * {@code server.compression.mime-types} is deliberately left at the bare {@code application/json}
 * that a Spring Boot 3-era config would have had - the point of {@link VSCodeGalleryCompressionConfig}
 * is that the Gallery API's versioned Content-Type gets compressed anyway, without an operator ever
 * adding that literal. See <a href="https://github.com/eclipse-openvsx/openvsx/issues/2071">#2071</a>.
 * <p>
 * {@code @Isolated}: queries for the fixed "editorconfig" extension {@link org.eclipse.openvsx.IntegrationTest}
 * publishes, colliding with it under concurrent execution.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "server.compression.enabled=true",
        "server.compression.mime-types=application/json",
        "server.compression.min-response-size=1B",
        "ovsx.databasesearch.enabled=true"
    }
)
@Isolated
class VSCodeGalleryExtensionQueryCompressionIntegrationTest extends AbstractPostgresContainerTest {

    private static final String QUERY_BODY = """
            {"filters":[{"criteria":[{"filterType":10,"value":"editorconfig"}],"pageNumber":1,"pageSize":50}],"flags":950}""";

    @LocalServerPort
    int port;

    @Test
    void extensionQueryResponseIsCompressed() throws IOException, InterruptedException {
        var response = postExtensionQuery(true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
        // The whole point of VSCodeGalleryContentTypeAdvice: a fixed, known Content-Type that
        // VSCodeGalleryCompressionConfig can make compressible without any operator-side config.
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(
                        contentType -> assertThat(contentType)
                                .startsWith("application/json;api-version="));

        var decoded = new String(
                new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(decoded).contains("\"results\"");
    }

    @Test
    void extensionQueryResponseIsNotCompressedWithoutAcceptEncoding() throws IOException, InterruptedException {
        var response = postExtensionQuery(false);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"results\"");
    }

    private HttpResponse<byte[]> postExtensionQuery(boolean acceptGzip) throws IOException, InterruptedException {
        var requestBuilder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/vscode/gallery/extensionquery"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json;api-version=3.0-preview.1")
                .POST(HttpRequest.BodyPublishers.ofString(QUERY_BODY, StandardCharsets.UTF_8));
        if (acceptGzip) {
            requestBuilder.header("Accept-Encoding", "gzip");
        }

        return HttpClient.newHttpClient().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
