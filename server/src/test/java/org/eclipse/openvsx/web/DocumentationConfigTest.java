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
package org.eclipse.openvsx.web;

import java.lang.reflect.Method;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationConfigTest {

    private final Operation operation = new Operation()
            .summary("Provides a feed")
            .description("One entry per transition.");

    @Test
    void shouldMarkAPreviewOperation() {
        customize(operation, "preview");

        // Visible in the operation list without expanding it, and in the description once expanded.
        assertThat(operation.getSummary()).isEqualTo("[Preview] Provides a feed");
        assertThat(operation.getDescription())
                .startsWith("**Preview**")
                .endsWith("One entry per transition.");
        // for consumers reading the OpenAPI document rather than the rendered page
        assertThat(operation.getExtensions()).containsEntry("x-preview", true);
    }

    @Test
    void shouldLeaveAStableOperationAlone() {
        customize(operation, "stable");

        assertThat(operation.getSummary()).isEqualTo("Provides a feed");
        assertThat(operation.getDescription()).isEqualTo("One entry per transition.");
        assertThat(operation.getExtensions()).isNull();
    }

    @Test
    void shouldMarkAPreviewOperationOnlyOnce() {
        // The customizer is registered per group and springdoc also picks up customizer beans on its own,
        // so being applied twice must not prefix the summary twice.
        customize(operation, "preview");
        customize(operation, "preview");

        assertThat(operation.getSummary()).isEqualTo("[Preview] Provides a feed");
        assertThat(operation.getDescription()).containsOnlyOnce("**Preview**");
    }

    // springdoc 3.x emits OpenAPI 3.1 by default, which does not serialize the legacy string `type` a
    // bare `new Schema<>().type(...)` sets - the header then documents no type at all. The rate limit
    // headers have to survive both spec versions, since which one is emitted is a springdoc default.
    @Test
    void shouldGiveTheRateLimitHeadersATypeInEitherSpecVersion() throws Exception {
        var openApi = new OpenAPI().paths(
                new Paths().addPathItem("/api/-/search", new PathItem().get(new Operation())));

        new DocumentationConfig().addRateLimitResponse().customise(openApi);

        var schema = openApi.getPaths()
                .get("/api/-/search")
                .getGet()
                .getResponses()
                .get("429")
                .getHeaders()
                .get("X-RateLimit-Limit")
                .getSchema();

        assertThat(Json.mapper().writeValueAsString(schema)).contains("\"type\":\"integer\"");
        assertThat(Json31.mapper().writeValueAsString(schema)).contains("\"type\":\"integer\"");
    }

    private void customize(Operation operation, String methodName) {
        Method method;
        try {
            method = Handlers.class.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException exc) {
            throw new AssertionError(exc);
        }

        new DocumentationConfig().previewOperation()
                .customize(operation, new HandlerMethod(new Handlers(), method));
    }

    /**
     * Stands in for a controller, so that the customizer sees the annotation the way springdoc hands it
     * over: on the handler method.
     */
    static class Handlers {

        @PreviewOperation
        public void preview() {
        }

        public void stable() {
        }
    }
}
