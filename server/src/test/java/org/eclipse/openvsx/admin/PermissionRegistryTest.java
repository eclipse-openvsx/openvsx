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
package org.eclipse.openvsx.admin;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.Permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionRegistryTest {

    private static PermissionProvider provider(String... names) {
        return () -> Arrays.stream(names)
                .map(n -> new PermissionInfo(Permission.of(n), n))
                .toList();
    }

    @Test
    void rejectsDuplicateName() {
        var providers = List.of(new BuiltInPermissions(), provider("Manage_Caches"));

        assertThatThrownBy(() -> new PermissionRegistry(providers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manage_caches");
    }

    @Test
    void rejectsNameOver32Chars() {
        var providers = List.of(provider("a".repeat(33)));

        assertThatThrownBy(() -> new PermissionRegistry(providers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
        assertThat(new PermissionRegistry(List.of(provider("a".repeat(32)))).all()).hasSize(1);
    }

    @Test
    void keepsBuiltInOrder() {
        var registry = new PermissionRegistry(List.of(new BuiltInPermissions(), provider("manage_widgets")));

        assertThat(registry.all()).extracting(PermissionInfo::permission).containsExactly(
                Permission.MANAGE_NAMESPACES,
                Permission.MANAGE_EXTENSIONS,
                Permission.MANAGE_PUBLISHERS,
                Permission.MANAGE_SCANS,
                Permission.MANAGE_CONSISTENCY,
                Permission.MANAGE_RATE_LIMITS,
                Permission.MANAGE_CACHES,
                Permission.MANAGE_SEARCH_INDEX,
                Permission.MANAGE_SETTINGS,
                Permission.VIEW_REPORTS,
                Permission.of("manage_widgets"));
        assertThat(registry.isRegistered(Permission.of("manage_widgets"))).isTrue();
        assertThat(registry.isRegistered(Permission.of("nope"))).isFalse();
    }
}
