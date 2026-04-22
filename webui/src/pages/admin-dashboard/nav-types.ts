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
