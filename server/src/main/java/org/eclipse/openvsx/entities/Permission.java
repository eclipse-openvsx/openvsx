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
 */
public enum Permission {
    MANAGE_NAMESPACES, MANAGE_EXTENSIONS, MANAGE_PUBLISHERS, MANAGE_SCANS, MANAGE_CONSISTENCY, MANAGE_RATE_LIMITS, MANAGE_CACHES, MANAGE_SEARCH_INDEX, MANAGE_SETTINGS, VIEW_REPORTS;

    public static Permission valueOfIgnoreCase(String value) {
        if (value == null) {
            return null;
        }
        return Permission.valueOf(value.trim().toUpperCase());
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
