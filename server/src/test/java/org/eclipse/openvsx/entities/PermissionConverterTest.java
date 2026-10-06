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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PermissionConverterTest {

    private final PermissionConverter converter = new PermissionConverter();

    @Test
    void roundTripsEveryConstant() {
        for (var permission : Permission.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(permission)))
                    .isEqualTo(permission);
        }
    }

    @Test
    void readsNullAsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    /**
     * A grant left behind by a release that has since been rolled back. The collection is EAGER on
     * the path every authenticated request takes, so throwing here would lock that user out of the
     * whole site rather than just out of the capability the row names.
     */
    @Test
    void readsAValueThisBuildDoesNotKnowAsNoPermission() {
        assertThat(converter.convertToEntityAttribute("manage_webhooks")).isNull();
    }

    @Test
    void aUserCarryingAnUnknownPermissionStaysUsable() {
        var user = new UserData();
        user.getPermissions().add(converter.convertToEntityAttribute("manage_webhooks"));
        user.getPermissions().add(Permission.MANAGE_CACHES);

        assertThatCode(user::getPermissionsAsStrings).doesNotThrowAnyException();
        assertThat(user.getPermissionsAsStrings()).containsExactly("manage_caches");
        assertThat(user.hasPermission(Permission.MANAGE_CACHES)).isTrue();
        assertThat(user.hasPermission(Permission.MANAGE_SCANS)).isFalse();
    }
}
