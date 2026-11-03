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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionTest {

    @Test
    void constructorNormalizesTheName() {
        assertThat(new Permission(" Manage_Widgets ")).isEqualTo(Permission.of("manage_widgets"));
    }

    @Test
    void normalizesIndependentlyOfTheDefaultLocale() {
        var original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr"));
            assertThat(new Permission("MANAGE_EXTENSIONS").name()).isEqualTo("manage_extensions");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void rejectsANullOrBlankName() {
        assertThatThrownBy(() -> new Permission(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Permission(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofKeepsNullAsNull() {
        assertThat(Permission.of(null)).isNull();
    }

    @Test
    void aUserWithAPermissionSurvivesJavaSerialization() throws Exception {
        var user = new UserData();
        user.getPermissions().add(Permission.MANAGE_SCANS);

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(user);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(((UserData) in.readObject()).getPermissions()).containsExactly(Permission.MANAGE_SCANS);
        }
    }
}
