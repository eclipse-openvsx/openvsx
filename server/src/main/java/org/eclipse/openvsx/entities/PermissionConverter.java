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
     * A value this build has no constant for reads as {@code null}, i.e. as no permission at all.
     * <p>
     * Throwing here would not stay contained: the collection is EAGER on the path
     * {@code UserService#findLoggedInUser} takes, which runs on essentially every authenticated
     * request, and {@code AdminAccessAuthorizationManager} loads the same row inside the security
     * filter chain. One row left behind by a rolled-back release would turn into a 500 on every
     * request that user makes, recoverable only by hand. Dropping the grant denies access, which
     * is the safe direction, and the warning says which row to look at.
     */
    @Override
    public Permission convertToEntityAttribute(String value) {
        try {
            return Permission.valueOfIgnoreCase(value);
        } catch (IllegalArgumentException ignored) {
            logger.warn("Ignoring unknown permission {} stored in user_data_permission", value);
            return null;
        }
    }
}
