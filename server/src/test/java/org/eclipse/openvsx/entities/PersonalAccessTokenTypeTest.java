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
package org.eclipse.openvsx.entities;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalAccessTokenTypeTest {

    @Test
    void onlyThePublishingTypesArePublishOnly() {
        var publishOnly = Arrays.stream(PersonalAccessTokenType.values())
                .filter(PersonalAccessTokenType::isPublishOnly)
                .toList();

        assertThat(publishOnly).containsExactlyInAnyOrder(PersonalAccessTokenType.LLP, PersonalAccessTokenType.TPT);
    }

    @Test
    void theLongLivedTypesAreTheOnesAUserManagesAndIsNotifiedAbout() {
        assertThat(PersonalAccessTokenType.LONG_LIVED)
                .containsExactly(PersonalAccessTokenType.LLT, PersonalAccessTokenType.LLP);
        assertThat(PersonalAccessTokenType.LONG_LIVED).allSatisfy(type -> {
            assertThat(type.isNotify()).isTrue();
            assertThat(type.isEphemeral()).isFalse();
            assertThat(type.isOneTime()).isFalse();
        });
    }

    @Test
    void everyTypeHasItsOwnMarker() {
        var markers = Arrays.stream(PersonalAccessTokenType.values())
                .map(PersonalAccessTokenType::getTokenMarker)
                .toList();

        assertThat(markers).doesNotHaveDuplicates();
    }
}
