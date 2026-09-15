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

import { ReactNode } from 'react';

export interface RouteEntry {
    path: string;
    name: string;
    icon: ReactNode;
    description?: string;
}

export interface NavGroup {
    name: string;
    icon: ReactNode;
    children: RouteEntry[];
}

export type NavEntry = RouteEntry | NavGroup;

export const isNavGroup = (entry: NavEntry): entry is NavGroup => 'children' in entry;

/** Side panel and overview grouping for contributed admin pages. */
export interface AdminPageCategory {
    name: string;
    icon: ReactNode;
}

/**
 * An admin dashboard page contributed by a consumer through
 * `PageSettings.elements.adminPages`. It shows up in the side panel, as a card on the
 * dashboard overview, and as a route under the admin dashboard.
 */
export interface AdminPage {
    /**
     * Path below the admin dashboard root, without a leading slash (e.g. `'analytics/agents'`).
     * The page also receives everything below it, so it may render nested routes of its own.
     * A page whose first segment is one the built-in pages already use is ignored — contributed
     * pages can only be added, never override a built-in one.
     */
    path: string;
    name: string;
    icon: ReactNode;
    /** Shown under the page name on the dashboard overview. */
    description?: string;
    /**
     * Groups the page in the side panel and on the overview. Pages sharing a category name are
     * merged into one group, and a name matching a built-in group appends to that group.
     * Without a category the page sits at the top level.
     */
    category?: AdminPageCategory;
    element: ReactNode;
}
