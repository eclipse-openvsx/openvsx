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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
class PermissionConverter implements AttributeConverter<Permission, String> {

    private static final Logger logger = LoggerFactory.getLogger(PermissionConverter.class);

    @Override
    public String convertToDatabaseColumn(Permission permission) {
        return permission != null ? permission.toString() : null;
    }

    /**
     * An invalid stored value reads as no permission: the collection is loaded on essentially every
     * authenticated request, so throwing would turn one bad row into a 500 on all of that user's requests.
     */
    @Override
    public Permission convertToEntityAttribute(String value) {
        try {
            return Permission.of(value);
        } catch (IllegalArgumentException ignored) {
            logger.warn("Ignoring invalid permission '{}' stored in user_data_permission", value);
            return null;
        }
    }
}
