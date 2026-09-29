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
package org.eclipse.openvsx.mail;

import java.time.LocalDateTime;
import java.util.Map;

import org.jobrunr.utils.mapper.jackson3.Jackson3JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobRunr persists job arguments with Jackson 3, using a {@code PolymorphicTypeValidator}
 * that only allows a fixed, narrow set of subtypes (e.g. {@code ConcurrentHashMap}) for
 * fields whose declared type is non-concrete, such as {@code Map<String, Object>}.
 */
@ExtendWith(SpringExtension.class)
class SendMailJobRequestTest {

    @Autowired
    Jackson3JsonMapper mapper;

    @Test
    void testVariablesMapFailsToRoundTrip() {
        var variables = Map.<String, Object>of(
                "name",
                "Jane Doe",
                "tokenName",
                "My Token",
                "expiryDate",
                LocalDateTime.now());
        var request = new SendMailJobRequest(
                "from@example.com",
                "to@example.com",
                "subject",
                "template.html",
                variables);

        var json = mapper.serialize(request);

        var result = mapper.deserialize(json, SendMailJobRequest.class);

        assertThat(result.getFrom()).isEqualTo(request.getFrom());
        assertThat(result.getTo()).isEqualTo(request.getTo());
        assertThat(result.getSubject()).isEqualTo(request.getSubject());
        assertThat(result.getTemplate()).isEqualTo(request.getTemplate());
        assertThat(result.getVariables()).isEqualTo(request.getVariables());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public Jackson3JsonMapper jobRunrJsonMapper() {
            // allow certain subtypes as they are used in SendMailJobRequest
            var typeValidator = BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType("java.util.")
                    .allowIfSubType("java.time.");

            return new Jackson3JsonMapper(typeValidator);
        }
    }
}
