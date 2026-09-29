/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.entities;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenConverterTest {

    private AuthTokenConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AuthTokenConverter();
    }

    @Test
    void convertNullToDatabaseColumnReturnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertNullToEntityAttributeReturnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTripPreservesAllFields() {
        var token = new AuthToken(
                "access-token-value",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T11:00:00Z"),
                Set.of("read", "write"),
                "refresh-token-value",
                Instant.parse("2026-01-08T10:00:00Z"));

        var json = converter.convertToDatabaseColumn(token);
        var restored = converter.convertToEntityAttribute(json);

        assertThat(restored).isNotNull();
        assertThat(restored.accessToken()).isEqualTo(token.accessToken());
        assertThat(restored.issuedAt()).isEqualTo(token.issuedAt());
        assertThat(restored.expiresAt()).isEqualTo(token.expiresAt());
        assertThat(restored.scopes()).isEqualTo(token.scopes());
        assertThat(restored.refreshToken()).isEqualTo(token.refreshToken());
        assertThat(restored.refreshExpiresAt()).isEqualTo(token.refreshExpiresAt());
    }

    @Test
    void roundTripWithNullOptionalFields() {
        var token = new AuthToken("access-only", null, null, null, null, null);

        var json = converter.convertToDatabaseColumn(token);
        var restored = converter.convertToEntityAttribute(json);

        assertThat(restored).isNotNull();
        assertThat(restored.accessToken()).isEqualTo("access-only");
        assertThat(restored.issuedAt()).isNull();
        assertThat(restored.expiresAt()).isNull();
        assertThat(restored.scopes()).isNull();
        assertThat(restored.refreshToken()).isNull();
        assertThat(restored.refreshExpiresAt()).isNull();
    }

    @Test
    void convertToDatabaseColumnProducesJson() {
        var token = new AuthToken("my-token", null, null, null, null, null);
        var json = converter.convertToDatabaseColumn(token);
        assertThat(json).contains("my-token");
    }

    @Test
    void deserializeFromJackson2Timestamps() {
        String json = """
                {
                    "accessToken":"test-token",
                    "issuedAt":1782716836.174727396,
                    "expiresAt":1782717136.174727396,
                    "scopes":[],
                    "refreshToken":null,
                    "refreshExpiresAt":null
                }
                """;

        var restored = converter.convertToEntityAttribute(json);

        assertThat(restored).isNotNull();
        assertThat(restored.accessToken()).isEqualTo("test-token");
        assertThat(restored.issuedAt()).isEqualTo(Instant.parse("2026-06-29T07:07:16.174727396Z"));
        assertThat(restored.expiresAt()).isEqualTo(Instant.parse("2026-06-29T07:12:16.174727396Z"));
        assertThat(restored.scopes()).isEmpty();
        assertThat(restored.refreshToken()).isNull();
        assertThat(restored.refreshExpiresAt()).isNull();
    }
}
