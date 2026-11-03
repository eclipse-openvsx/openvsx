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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.admin.BuiltInPermissions;
import org.eclipse.openvsx.admin.PermissionRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionConverterTest {

    private final PermissionConverter converter = new PermissionConverter();

    @Test
    void roundTripsEveryConstant() {
        for (var info : new PermissionRegistry(List.of(new BuiltInPermissions())).all()) {
            var permission = info.permission();
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(permission)))
                    .isEqualTo(permission);
        }
    }

    @Test
    void readsNullAsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}
