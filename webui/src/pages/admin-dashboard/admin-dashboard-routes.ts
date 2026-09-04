/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { createRoute } from '../../utils';

export namespace AdminDashboardRoutes {
    export const ROOT = 'admin-dashboard';
    export const MAIN = createRoute([ROOT]);
    export const STATISTICS = createRoute([ROOT, 'statistics']);
    export const NAMESPACE_ADMIN = createRoute([ROOT, 'namespaces']);
    export const EXTENSION_ADMIN = createRoute([ROOT, 'extensions']);
    export const PUBLISHER_ADMIN = createRoute([ROOT, 'publisher']);
    export const SCANS_ADMIN = createRoute([ROOT, 'scans']);
    export const TIERS = createRoute([ROOT, 'tiers']);
    export const CUSTOMERS = createRoute([ROOT, 'customers']);
    export const USAGE_STATS = createRoute([ROOT, 'usage']);
    export const SETTINGS = createRoute([ROOT, 'settings']);
    export const LOGS = createRoute([ROOT, 'logs']);
    export const CONSISTENCY = createRoute([ROOT, 'consistency']);
    export const SEARCH_INDEX = createRoute([ROOT, 'search-index']);
}
