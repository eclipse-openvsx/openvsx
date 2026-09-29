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

package org.eclipse.openvsx.util;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangesCursorTest {

    @Test
    void shouldRoundTripAPosition() {
        var cursor = new ChangesCursor(LocalDateTime.parse("2026-01-14T09:30:11"), 1234L);

        assertThat(ChangesCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void shouldRoundTripASubSecondInstant() {
        // The column keeps microseconds, and a cursor that dropped them would resume before the entry it
        // names and report it a second time.
        var cursor = new ChangesCursor(LocalDateTime.parse("2026-01-14T09:30:11.123456"), 1234L);

        assertThat(ChangesCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void shouldRoundTripAWholeMinute() {
        // A timestamp with no seconds is rendered without them, so the two parts have to stay separable
        // by something an ISO-8601 date and time cannot contain.
        var cursor = new ChangesCursor(LocalDateTime.parse("2026-01-14T09:30"), 1L);

        assertThat(ChangesCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void shouldNotBeUrlEncoded() {
        // Consumers pass a cursor back as a query parameter, so it must survive that without escaping.
        var encoded = new ChangesCursor(LocalDateTime.parse("2026-01-14T09:30:11"), 1234L).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "",
            "not a cursor",
            // valid base64url, but not a position
            "Zm9vX2Jhcg",
            // a position whose id was truncated away
            "MjAyNi0wMS0xNFQwOTozMDoxMQ",
            // an id that is not a number
            "MjAyNi0wMS0xNFQwOTozMDoxMV9hYmM"
        }
    )
    void shouldRejectAValueItDidNotProduce(String value) {
        // Silently accepting one would resume from somewhere else in the feed, skipping entries without
        // the consumer noticing.
        assertThatThrownBy(() -> ChangesCursor.decode(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
