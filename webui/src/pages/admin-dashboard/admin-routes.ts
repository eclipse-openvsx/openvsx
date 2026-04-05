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

import { createRoute } from '../../utils';

export namespace AdminDashboardRoutes {
    export const ROOT = 'admin-dashboard';
    export const MAIN = createRoute([ROOT]);
    export const NAMESPACE_ADMIN = createRoute([ROOT, 'namespaces']);
    export const EXTENSION_ADMIN = createRoute([ROOT, 'extensions']);
    export const PUBLISHER_ADMIN = createRoute([ROOT, 'publisher']);
    export const SCANS_ADMIN = createRoute([ROOT, 'scans']);
    export const TIERS = createRoute([ROOT, 'tiers']);
    export const CUSTOMERS = createRoute([ROOT, 'customers']);
    export const USAGE_STATS = createRoute([ROOT, 'usage']);
    export const LOGS = createRoute([ROOT, 'logs']);
}
