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
package org.eclipse.openvsx.scanning;

import jakarta.persistence.EntityManager;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import org.eclipse.openvsx.accesstoken.AccessTokenConfig;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.mail.MailService;
import org.eclipse.openvsx.repositories.RepositoryService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled rule must keep recognising the token values the server actually generates.
 */
class OpenVsxTokenSecretRuleTest {

    private static final String RULE_ID = "openvsx-pat-prefixed";

    private SecretRule rule;
    private AccessTokenService tokens;

    @BeforeEach
    void setUp() {
        rule = new SecretRuleLoader().load("classpath:scanning/secret-detection-custom-rules.yaml").stream()
                .filter(r -> r.getId().equals(RULE_ID))
                .findFirst()
                .orElseThrow();
        var config = Mockito.mock(AccessTokenConfig.class);
        Mockito.when(config.getPrefix()).thenReturn("ovsx");
        tokens = new AccessTokenService(
                config,
                Mockito.mock(EntityManager.class),
                Mockito.mock(RepositoryService.class),
                Mockito.mock(MailService.class),
                Mockito.mock(DSLContext.class));
    }

    @ParameterizedTest
    @EnumSource(value = PersonalAccessTokenType.class, names = { "LLT", "LLP", "TPT" })
    void matchesGeneratedTokens(PersonalAccessTokenType type) {
        var value = tokens.generateTokenValue(type);

        var matcher = rule.getPattern().matcher("OVSX_PAT=" + value + "\n");

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo(value);
        assertThat(rule.getKeywords()).anyMatch(value::startsWith);
    }

    @Test
    void stillMatchesTheLegacyUuidForm() {
        var value = "ovsxat_123e4567-e89b-12d3-a456-426614174000";

        assertThat(rule.getPattern().matcher(value).find()).isTrue();
        assertThat(rule.getKeywords()).anyMatch(value::startsWith);
    }

    @Test
    void doesNotMatchATruncatedToken() {
        var value = tokens.generateTokenValue(PersonalAccessTokenType.LLP);

        assertThat(rule.getPattern().matcher(value.substring(0, value.length() - 1)).find()).isFalse();
    }
}
