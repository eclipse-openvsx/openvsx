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

/**
 * A single admin capability that can be granted to or revoked from a user independently of their
 * {@link UserData.Role}. A user with {@link UserData.Role#ADMIN} implicitly has every permission,
 * including ones added after that user was granted the role; see {@link UserData#hasPermission}.
 * <p>
 * An open type: deployments add their own through {@code PermissionProvider}. Only registered
 * permissions can be granted, and the stored name is at most 32 characters.
 */
public record Permission(String name) {

    public static final Permission MANAGE_NAMESPACES = of("manage_namespaces");
    public static final Permission MANAGE_EXTENSIONS = of("manage_extensions");
    public static final Permission MANAGE_PUBLISHERS = of("manage_publishers");
    public static final Permission MANAGE_SCANS = of("manage_scans");
    public static final Permission MANAGE_CONSISTENCY = of("manage_consistency");
    public static final Permission MANAGE_CACHES = of("manage_caches");
    public static final Permission MANAGE_SEARCH_INDEX = of("manage_search_index");
    public static final Permission MANAGE_SETTINGS = of("manage_settings");
    public static final Permission VIEW_REPORTS = of("view_reports");

    /** Trims and lower-cases {@code value}; {@code null} stays {@code null}. */
    public static Permission of(String value) {
        return value == null ? null : new Permission(value.trim().toLowerCase());
    }

    @Override
    public String toString() {
        return name;
    }
}
