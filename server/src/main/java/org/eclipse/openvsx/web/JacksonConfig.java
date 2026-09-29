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

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class JacksonConfig {

    // Jackson 3 (used by Spring Boot 4 for @RequestBody handling) flipped the default of
    // FAIL_ON_NULL_FOR_PRIMITIVES from false to true, unlike the Jackson 2 engine this app ran
    // on under Spring Boot 3. Several request DTOs have primitive int/long/boolean fields -
    // e.g. ExtensionQueryParam (the VS Code gallery query protocol, which this app does not
    // control the shape of) and ReviewJson.rating - that used to silently accept an explicit
    // JSON null for such a field (coercing it to 0/false) and now reject the whole request
    // with a 400 instead. Restore the previous, permissive behavior globally rather than
    // patching every affected DTO one by one.
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
