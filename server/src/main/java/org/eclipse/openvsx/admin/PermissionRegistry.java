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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Permission;

/** Every permission contributed by a {@link PermissionProvider}, in provider then declaration order. */
@Component
public class PermissionRegistry {

    // The width of user_data_permission.permission.
    private static final int MAX_NAME_LENGTH = 32;

    private final Map<Permission, PermissionInfo> byPermission = new LinkedHashMap<>();

    public PermissionRegistry(List<PermissionProvider> providers) {
        for (var provider : providers) {
            for (var info : provider.permissions()) {
                var name = info.permission().name();
                if (name.length() > MAX_NAME_LENGTH) {
                    throw new IllegalStateException(
                            "Permission name longer than " + MAX_NAME_LENGTH + " characters: " + name);
                }
                if (byPermission.putIfAbsent(info.permission(), info) != null) {
                    throw new IllegalStateException("Permission registered more than once: " + name);
                }
            }
        }
    }

    public List<PermissionInfo> all() {
        return List.copyOf(byPermission.values());
    }

    public boolean isRegistered(Permission permission) {
        return byPermission.containsKey(permission);
    }
}
