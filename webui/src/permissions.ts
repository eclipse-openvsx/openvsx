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

import { AdminPermission, UserData } from './extension-registry-types';

/**
 * Whether the user may perform an admin action requiring `permission`. The `admin` role implies every
 * permission, including ones added later, matching `UserData#hasPermission` on the server.
 */
export const hasPermission = (user: UserData | undefined, permission: AdminPermission): boolean =>
    user?.role === 'admin' || (user?.permissions?.includes(permission) ?? false);

/**
 * Whether the user has any admin access at all - the question every entry point to the admin
 * dashboard asks, since a user granted a single permission still belongs there.
 */
export const hasAnyAdminAccess = (user: UserData | undefined): boolean =>
    user?.role === 'admin' || (user?.permissions?.length ?? 0) > 0;

/** Every admin permission with its display label, in the order admin pages list them. */
export const PERMISSION_OPTIONS: { value: AdminPermission; label: string }[] = [
    { value: 'manage_namespaces', label: 'Manage namespaces' },
    { value: 'manage_extensions', label: 'Manage extensions' },
    { value: 'manage_publishers', label: 'Manage publishers' },
    { value: 'manage_scans', label: 'Manage scans' },
    { value: 'manage_consistency', label: 'Manage data consistency' },
    { value: 'manage_rate_limits', label: 'Manage rate limiting' },
    { value: 'manage_caches', label: 'Manage caches' },
    { value: 'manage_search_index', label: 'Manage search index' },
    { value: 'manage_settings', label: 'Manage settings' },
    { value: 'view_reports', label: 'View reports' }
];
