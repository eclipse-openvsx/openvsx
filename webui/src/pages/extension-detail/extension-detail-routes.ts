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

import { createRoute, getTargetPlatforms } from '../../utils';

export namespace ExtensionDetailRoutes {
    export namespace Parameters {
        export const NAMESPACE = ':namespace';
        export const NAME = ':name';
        export const TARGET = `:target(${getTargetPlatforms().join('|')})`;
        export const VERSION = ':version?';
    }

    export const ROOT = 'extension';
    export const MAIN = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, Parameters.VERSION]);
    export const MAIN_TARGET = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, Parameters.TARGET, Parameters.VERSION]);
    export const LATEST = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME]);
    export const LATEST_TARGET = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, Parameters.TARGET]);
    export const PRE_RELEASE = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, 'pre-release']);
    export const PRE_RELEASE_TARGET = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, Parameters.TARGET, 'pre-release']);
    export const REVIEWS = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, 'reviews']);
    export const REVIEWS_TARGET = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, Parameters.TARGET, 'reviews']);
    export const CHANGES = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, 'changes']);
    export const CHANGES_TARGET = createRoute([ROOT, Parameters.NAMESPACE, Parameters.NAME, Parameters.TARGET, 'changes']);
}
