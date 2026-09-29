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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.json.ReviewJson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigTest {

    private static final String REVIEW_WITH_NULL_RATING = "{\"rating\": null}";

    // Sanity check documenting the Jackson 3 default this config exists to work around: an
    // explicit JSON null for a primitive field is rejected, whereas the Jackson 2 engine this
    // app ran on under Spring Boot 3 silently coerced it to the primitive's default value.
    @Test
    void jackson3DefaultRejectsNullForPrimitiveFields() {
        var mapper = JsonMapper.builder().build();

        assertThatThrownBy(() -> mapper.readValue(REVIEW_WITH_NULL_RATING, ReviewJson.class))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("FAIL_ON_NULL_FOR_PRIMITIVES");
    }

    // Regression: several request DTOs reachable from a client - most notably
    // ExtensionQueryParam, the VS Code gallery query protocol this app does not control the
    // shape of - have primitive int/long/boolean fields that used to tolerate an explicit
    // JSON null. Without this customizer, such a request now fails with a 400 instead of
    // being accepted like it was under Spring Boot 3.
    @Test
    void customizerRestoresTheOldPermissiveBehavior() {
        var builder = JsonMapper.builder();
        new JacksonConfig().jsonMapperBuilderCustomizer().customize(builder);
        var mapper = builder.build();

        var review = mapper.readValue(REVIEW_WITH_NULL_RATING, ReviewJson.class);

        assertThat(review.getRating()).isZero();
    }
}
