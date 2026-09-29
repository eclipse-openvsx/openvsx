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

package org.eclipse.openvsx.json;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.ExtensionVersionState;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeEntryJsonTest {

    /**
     * The states the feed reports are the names of {@link ExtensionVersionState}, but the constants here
     * have to spell them out: they feed the {@code allowableValues} of an annotation, which only takes
     * compile-time constants, and an enum name is not one. This is what keeps the two from drifting.
     */
    @Test
    void shouldDeclareEveryState() {
        var declared = Stream.of(ChangeEntryJson.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getName().startsWith("STATE_"))
                .map(field -> {
                    try {
                        return field.get(null);
                    } catch (IllegalAccessException exc) {
                        throw new AssertionError(exc);
                    }
                })
                .toList();

        assertThat(declared)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ExtensionVersionState.values()).map(Enum::name).toList());
    }

    /**
     * The schema is what tells a consumer which states it has to handle, so a state missing from it is a
     * state a consumer will not expect.
     */
    @Test
    void shouldOfferEveryStateInTheSchema() throws NoSuchFieldException {
        var schema = ChangeEntryJson.class.getDeclaredField("state").getAnnotation(Schema.class);

        assertThat(schema.allowableValues())
                .containsExactlyInAnyOrder(
                        Arrays.stream(ExtensionVersionState.values()).map(Enum::name).toArray(String[]::new));
    }
}
