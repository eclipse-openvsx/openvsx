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

import type { AdminStatistics } from '../../../extension-registry-types';

export const PUBLISH_METHODS = ['Trusted publishing', 'Access token', 'Web UI / unknown'];

// Before 1.2.0 the web UI published through a one-time token (OTT).
const WEB_UI_METHODS = ['NONE', 'OTT'];

/** Groups the token types into the three ways a version can be published, in `PUBLISH_METHODS` order. */
export function countByPublishMethod(statistics: AdminStatistics): number[] {
    const counts = [0, 0, 0];
    for (const e of statistics.extensionVersionsByPublishMethod ?? []) {
        const index = e.publishMethod === 'TPT' ? 0 : WEB_UI_METHODS.includes(e.publishMethod) ? 2 : 1;
        counts[index] += e.extensionVersions;
    }
    return counts;
}
