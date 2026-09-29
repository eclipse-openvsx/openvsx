/*******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.web.server.ServerErrorException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class AuthTokenConverter implements AttributeConverter<AuthToken, String> {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(AuthToken data) {
        if (data == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(data);
        } catch (JacksonException exc) {
            throw new ServerErrorException("Failed to serialize AuthToken to DB column.", exc);
        }
    }

    @Override
    public AuthToken convertToEntityAttribute(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return jsonMapper.readValue(raw, AuthToken.class);
        } catch (JacksonException exc) {
            throw new ServerErrorException("Failed to parse AuthToken from DB column.", exc);
        }
    }
}
