/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ListOfStringConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> data) {
        return (data == null || data.isEmpty()) ? null : String.join(",", data);
    }

    @Override
    public List<String> convertToEntityAttribute(String raw) {
        return (raw == null)
                ? new ArrayList<>()
                : new ArrayList<>(
                        Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                                .collect(Collectors.toCollection(ArrayList::new)));
    }

}
