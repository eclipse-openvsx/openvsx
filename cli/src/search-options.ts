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

import { RegistryOptions } from './registry-options';

export interface SearchOptions extends RegistryOptions {
    /**
     * Text to search for. Omit to browse, which is what makes `--category` useful on its own.
     */
    text?: string;
    /**
     * Restrict results to a category, e.g. `Programming Languages`.
     */
    category?: string;
    /**
     * Restrict results to extensions published for a target platform.
     */
    target?: string;
    /**
     * Sort key: `relevance`, `timestamp`, `rating` or `downloadCount`.
     */
    sortBy?: string;
    /**
     * `asc` or `desc`.
     */
    sortOrder?: string;
    /**
     * Number of results to return.
     */
    size?: number;
    /**
     * Index of the first result, for paging through a large result set.
     */
    offset?: number;
    /**
     * Print the raw results as JSON instead of a table.
     */
    json?: boolean;
}
