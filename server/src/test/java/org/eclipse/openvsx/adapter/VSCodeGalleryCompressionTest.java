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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.WebServer;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof against the real embedded Jetty {@code CompressionHandler} (used by the
 * {@code spring-boot-jetty} auto-configuration since the Spring Boot 4 migration) that:
 * <ul>
 *     <li>the bare {@code application/json} entry in {@code server.compression.mime-types}, which
 *     used to be enough under Spring Boot 3 / Jetty 12.0's {@code GzipHandler}, is <b>not</b> enough
 *     any more once the response carries {@link VSCodeGalleryContentTypeAdvice}'s versioned
 *     Content-Type;</li>
 *     <li>adding that exact literal restores compression;</li>
 *     <li>so does {@link VSCodeGalleryCompressionConfig}'s customizer alone, with the literal still
 *     absent from the configured mime types - proving the config-only fix is not the only way.</li>
 * </ul>
 * See <a href="https://github.com/eclipse-openvsx/openvsx/issues/2071">#2071</a>. Deliberately talks
 * to a real {@link WebServer} rather than {@code MockMvc}, since MockMvc never touches the actual
 * Jetty handler chain that does the compression.
 */
class VSCodeGalleryCompressionTest {

    private static final String GALLERY_CONTENT_TYPE = "application/json;api-version="
            + IVSCodeService.GALLERY_API_VERSION;

    // Comfortably over any minResponseSize used below.
    private static final String RESPONSE_BODY = "{\"value\":\"" + "x".repeat(4096) + "\"}";

    private WebServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void bareApplicationJsonAloneNoLongerCompressesTheVersionedContentType() throws Exception {
        var response = requestGalleryResponseCompressedWith("application/json");

        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
    }

    // Same "application/json"-only configuration as the test above - the literal is deliberately
    // absent from server.compression.mime-types - but with VSCodeGalleryCompressionConfig's
    // customizer also applied, proving that config alone is not the only way to fix this.
    @Test
    void theCompressionCustomizerRestoresCompressionWithoutTheLiteralInConfig() throws Exception {
        var response = requestGalleryResponseCompressedWith(true, "application/json");

        assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
    }

    @Test
    void addingTheVersionedLiteralRestoresCompression() throws Exception {
        var response = requestGalleryResponseCompressedWith("application/json", GALLERY_CONTENT_TYPE);

        assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
        // Not just a spoofed header - the body is genuinely gzip-compressed and decodes back to the
        // original JSON, and (being 4KB of repeated characters) noticeably smaller on the wire.
        assertThat(response.body().length).isLessThan(RESPONSE_BODY.length());
        assertThat(
                new String(
                        new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes(),
                        StandardCharsets.UTF_8))
                .isEqualTo(RESPONSE_BODY);
    }

    private HttpResponse<byte[]> requestGalleryResponseCompressedWith(String... configuredMimeTypes)
            throws IOException, InterruptedException {
        return requestGalleryResponseCompressedWith(false, configuredMimeTypes);
    }

    private HttpResponse<byte[]> requestGalleryResponseCompressedWith(
            boolean withCompressionCustomizer,
            String... configuredMimeTypes
    ) throws IOException, InterruptedException {
        var compression = new Compression();
        compression.setEnabled(true);
        compression.setMimeTypes(configuredMimeTypes);
        compression.setMinResponseSize(DataSize.ofBytes(10));

        var factory = new JettyServletWebServerFactory(0);
        factory.setCompression(compression);
        if (withCompressionCustomizer) {
            new VSCodeGalleryCompressionConfig().vsCodeGalleryCompressionCustomizer().customize(factory);
        }
        server = factory.getWebServer(
                servletContext -> servletContext
                        .addServlet("gallery", new HttpServlet() {
                            @Override
                            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                                // What VSCodeGalleryContentTypeAdvice ends up writing as Content-Type.
                                resp.setContentType(GALLERY_CONTENT_TYPE);
                                resp.getOutputStream().write(RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
                            }
                        })
                        .addMapping("/*"));
        server.start();

        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/"))
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
