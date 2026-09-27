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

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    description = "A personal access token, sent as `Authorization: Bearer <token>`. "
            + "See the operation's `token` parameter description for fallbacks."
)
public class DocumentationConfig {

    /**
     * Prefixed onto the summary of a {@link PreviewOperation}, so that it is visible in the operation list
     * without having to expand the operation.
     */
    static final String PREVIEW_PREFIX = "[Preview] ";

    /**
     * Prepended to the description of a {@link PreviewOperation}. Kept here rather than repeated on each
     * operation, so that all of them say the same thing.
     */
    static final String PREVIEW_NOTE = """
            **Preview** -- this endpoint may still change in a later release. Its parameters and the shape \
            of its response are not covered by the usual compatibility expectations yet, so keep track of \
            what you depend on and follow the release notes.

            """;

    @Bean
    public GroupedOpenApi registry(
            OpenApiCustomizer sortSchemasAlphabetically,
            OpenApiCustomizer addRateLimitResponse,
            OperationCustomizer previewOperation
    ) {
        var description = "This API provides metadata of VS Code extensions in the Open VSX Registry as well as means to publish extensions.";
        return GroupedOpenApi.builder()
                .group("registry")
                .displayName("Registry API")
                .pathsToMatch("/api/**")
                .addOpenApiCustomizer(
                        openApi -> openApi.getInfo().title("Open VSX Registry API").description(description))
                .addOpenApiCustomizer(sortSchemasAlphabetically)
                .addOpenApiCustomizer(addRateLimitResponse)
                .addOperationCustomizer(previewOperation)
                .build();
    }

    @Bean
    public GroupedOpenApi vscode(
            OpenApiCustomizer sortSchemasAlphabetically,
            OpenApiCustomizer addRateLimitResponse,
            OperationCustomizer previewOperation
    ) {
        var description = "Provides a compatibility layer between VS Code based editors and the Open VSX Registry.";
        return GroupedOpenApi.builder()
                .group("vscode-adapter")
                .displayName("VSCode Adapter")
                .pathsToMatch("/vscode/**")
                .addOpenApiCustomizer(
                        openApi -> openApi.getInfo().title("Open VSX VSCode Adapter").description(description))
                .addOpenApiCustomizer(sortSchemasAlphabetically)
                .addOpenApiCustomizer(addRateLimitResponse)
                .addOperationCustomizer(previewOperation)
                .build();
    }

    @Bean
    public GroupedOpenApi admin(
            OpenApiCustomizer sortSchemasAlphabetically,
            OperationCustomizer previewOperation
    ) {
        var description = "This API provides administration features for the Open VSX Registry.";
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin API")
                .pathsToMatch("/admin/api/**", "/admin/report")
                .addOpenApiCustomizer(openApi -> openApi.getInfo().title("Open VSX Admin API").description(description))
                .addOpenApiCustomizer(sortSchemasAlphabetically)
                .addOperationCustomizer(previewOperation)
                .build();
    }

    /**
     * Marks up the operations annotated with {@link PreviewOperation}: the summary is prefixed, the note
     * above is prepended to the description, and {@code x-preview} is set for consumers reading the
     * OpenAPI document rather than the rendered page.
     * <p>
     * Applying it twice leaves an operation as it was, as the customizer is registered per group and
     * springdoc also picks up customizer beans on its own.
     */
    @Bean
    public OperationCustomizer previewOperation() {
        return (operation, handlerMethod) -> {
            if (!handlerMethod.hasMethodAnnotation(PreviewOperation.class)) {
                return operation;
            }

            var summary = Objects.toString(operation.getSummary(), "");
            if (summary.startsWith(PREVIEW_PREFIX)) {
                return operation;
            }

            operation.setSummary(PREVIEW_PREFIX + summary);
            operation.setDescription(PREVIEW_NOTE + Objects.toString(operation.getDescription(), ""));
            operation.addExtension("x-preview", true);
            return operation;
        };
    }

    @Bean
    public OpenApiCustomizer sortSchemasAlphabetically() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            openApi.getComponents().setSchemas(new TreeMap<>(schemas));
        };
    }

    /**
     * Schemas must be built through the typed subclasses rather than {@code new Schema<>().type(...)}.
     * The latter only sets the legacy string {@code type}, which an OpenAPI 3.1 document does not
     * serialize at all - springdoc 3.x emits 3.1 by default - leaving the header with no type. The
     * typed subclasses populate both that field and the 3.1 {@code types} set.
     */
    @Bean
    public OpenApiCustomizer addRateLimitResponse() {
        var limitLimitHeader = new Header()
                .description("Number of requests that can be made in a given amount of time")
                .schema(new IntegerSchema().format("int32"));
        var limitRemainingHeader = new Header()
                .description("Remaining number of requests left in the current time window")
                .schema(new IntegerSchema().format("int32"));
        var limitResetHeader = new Header()
                .description("Number of seconds until the rate limit tokens will be fully filled to its maximum")
                .schema(new IntegerSchema().format("int32"));
        var retryAfterHeader = new Header()
                .description("Number of seconds to wait after receiving a 429 response")
                .schema(new IntegerSchema().format("int32"));

        var response = new ApiResponse()
                .description("A client has sent too many requests in a given amount of time")
                .headers(
                        Map.of(
                                "X-RateLimit-Limit",
                                limitLimitHeader,
                                "X-RateLimit-Remaining",
                                limitRemainingHeader,
                                "X-RateLimit-Reset",
                                limitResetHeader,
                                "Retry-After",
                                retryAfterHeader));

        return openApi -> openApi.getPaths()
                .forEach(
                        (path, item) -> item.readOperations()
                                .forEach(operation -> {
                                    var responses = operation.getResponses();
                                    if (responses == null) {
                                        responses = new ApiResponses();
                                    }

                                    // add default rate limit headers present in all responses
                                    responses.forEach((status, r) -> {
                                        r.addHeaderObject("X-RateLimit-Limit", limitLimitHeader);
                                        r.addHeaderObject("X-RateLimit-Remaining", limitRemainingHeader);
                                    });

                                    responses.addApiResponse("429", response);
                                    operation.setResponses(responses);
                                }));
    }

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(
                        new Info()
                                .termsOfService("https://www.eclipse.org/legal/termsofuse.php")
                                .version("0.1")
                                .license(
                                        new License()
                                                .name("Eclipse Public License 2.0")
                                                .url("https://www.eclipse.org/legal/epl-2.0/")));
    }
}
