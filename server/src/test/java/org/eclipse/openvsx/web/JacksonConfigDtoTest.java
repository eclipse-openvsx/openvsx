/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.stream.Stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.adapter.ExtensionQueryParam;
import org.eclipse.openvsx.json.ChangeNamespaceJson;
import org.eclipse.openvsx.json.CustomerJson;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.json.QueryParamJson;
import org.eclipse.openvsx.json.ReviewJson;
import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.json.TierJson;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code JsonMapper} bean Spring actually wires into {@code @RequestBody}
 * handling - not a hand-built one - carries {@link JacksonConfig}'s customizer, for every
 * request DTO found to have a primitive int/long/boolean field reachable from a client. A
 * client sending an explicit JSON {@code null} for any of these must not 400.
 */
@JsonTest
@Import(JacksonConfig.class)
@MockitoBean(types = SimpleMeterRegistry.class)
class JacksonConfigDtoTest {

    @Autowired
    JsonMapper objectMapper;

    static Stream<Arguments> affectedDtos() {
        return Stream.of(
                // POST /api/{namespace}/{extension}/review
                Arguments.of("{\"rating\": null}", ReviewJson.class),
                // POST /api/-/query (deprecated but still live)
                Arguments.of("{\"includeAllVersions\": null}", QueryParamJson.class),
                // AdminAPI#updateSettings
                Arguments.of("{\"readOnly\": null}", SettingsJson.class),
                // AdminAPI#changeNamespace
                Arguments.of(
                        "{\"removeOldNamespace\": null, \"mergeIfNewNamespaceAlreadyExists\": null}",
                        ChangeNamespaceJson.class),
                // UserAPI#updateNamespaceDetails
                Arguments.of("{\"verified\": null}", NamespaceDetailsJson.class),
                // RateLimitAPI#createTier / #updateTier
                Arguments.of("{\"capacity\": null, \"duration\": null}", TierJson.class),
                // RateLimitAPI#createCustomer / #updateCustomer - primitives nested under .tier
                Arguments.of(
                        "{\"tier\": {\"capacity\": null, \"duration\": null}}",
                        CustomerJson.class),
                // VSCodeAPI#extensionQuery - the VS Code gallery query protocol; this app does
                // not control the shape of what a VS Code/VSCodium client sends here
                Arguments.of("{\"flags\": null}", ExtensionQueryParam.class),
                Arguments.of(
                        "{\"filters\": [{\"pageNumber\": null, \"pageSize\": null, \"sortBy\": null, \"sortOrder\": null}]}",
                        ExtensionQueryParam.class),
                Arguments.of(
                        "{\"filters\": [{\"criteria\": [{\"filterType\": null}]}]}",
                        ExtensionQueryParam.class));
    }

    @ParameterizedTest
    @MethodSource("affectedDtos")
    void requestBodyJsonMapperAcceptsAnExplicitNullForPrimitiveFields(String json, Class<?> dtoType) {
        assertThat(objectMapper.readValue(json, dtoType)).isNotNull();
    }

    // Sanity check that the fix is actually exercised above, not a mapper that would have
    // accepted the null anyway: the review's rating still ends up coerced to its primitive
    // default rather than, say, silently dropping the field.
    @Test
    void nullPrimitiveIsCoercedToItsDefaultValue() {
        var review = objectMapper.readValue("{\"rating\": null}", ReviewJson.class);

        assertThat(review.getRating()).isZero();
    }
}
