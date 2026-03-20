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

export namespace UserSettingsRoutes {
    export const ROOT = createRoute(['user-settings']);
    export const MAIN = createRoute([ROOT, ':tab']);
    export const PROFILE = createRoute([ROOT, 'profile']);
    export const TOKENS = createRoute([ROOT, 'tokens']);
    export const NAMESPACES = createRoute([ROOT, 'namespaces']);
    export const EXTENSIONS = createRoute([ROOT, 'extensions']);
    export const DELETE_EXTENSION = createRoute([ROOT, 'extensions', ':namespace', ':extension', 'delete']);
    export const CUSTOMERS = createRoute([ROOT, 'customers']);
}
