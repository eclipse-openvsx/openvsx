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

import java.util.List;

import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Permission;

@Component
public class BuiltInPermissions implements PermissionProvider {

    @Override
    public List<PermissionInfo> permissions() {
        return List.of(
                new PermissionInfo(Permission.MANAGE_NAMESPACES, "Manage namespaces"),
                new PermissionInfo(Permission.MANAGE_EXTENSIONS, "Manage extensions"),
                new PermissionInfo(Permission.MANAGE_PUBLISHERS, "Manage publishers"),
                new PermissionInfo(Permission.MANAGE_SCANS, "Manage scans"),
                new PermissionInfo(Permission.MANAGE_CONSISTENCY, "Manage data consistency"),
                new PermissionInfo(Permission.MANAGE_RATE_LIMITS, "Manage rate limiting"),
                new PermissionInfo(Permission.MANAGE_CACHES, "Manage caches"),
                new PermissionInfo(Permission.MANAGE_SEARCH_INDEX, "Manage search index"),
                new PermissionInfo(Permission.MANAGE_SETTINGS, "Manage settings"),
                new PermissionInfo(Permission.VIEW_REPORTS, "View reports"));
    }
}
