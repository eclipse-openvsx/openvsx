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

import { Registry, SearchEntry } from './registry';
import { SearchOptions } from './search-options';
import { formatNumber, printRows, truncate } from './table';
import { addEnvOptions } from './util';

/** Sort keys the registry accepts, for an up-front check with a better message than a 400. */
export const SORT_KEYS = ['relevance', 'timestamp', 'rating', 'downloadCount'];

export const SORT_ORDERS = ['asc', 'desc'];

export const DEFAULT_SEARCH_SIZE = 20;

/** Keeps a row from wrapping on a normal terminal, where the other columns take about half of it. */
const DESCRIPTION_WIDTH = 60;

/**
 * Searches the registry for extensions.
 */
export async function search(options: SearchOptions): Promise<void> {
    addEnvOptions(options);
    if (options.sortBy && !SORT_KEYS.includes(options.sortBy)) {
        throw new Error(`Sort key must be one of ${SORT_KEYS.join(', ')}.`);
    }
    if (options.sortOrder && !SORT_ORDERS.includes(options.sortOrder)) {
        throw new Error(`Sort order must be one of ${SORT_ORDERS.join(', ')}.`);
    }

    const size = options.size ?? DEFAULT_SEARCH_SIZE;
    const offset = options.offset ?? 0;
    const registry = new Registry(options);
    const result = await registry.search({
        query: options.text,
        category: options.category,
        targetPlatform: options.target,
        sortBy: options.sortBy,
        sortOrder: options.sortOrder,
        size,
        offset
    });
    if (result.error) {
        throw new Error(result.error);
    }

    if (options.json) {
        console.log(JSON.stringify(result, null, 4));
        return;
    }

    printResults(result.extensions ?? [], result.totalSize, offset);
}

function printResults(entries: SearchEntry[], totalSize: number, offset: number): void {
    if (entries.length === 0) {
        console.log('No extensions found.');
        return;
    }

    const rows = entries.map(entry => [
        `${entry.namespace}.${entry.name}`,
        entry.version,
        formatNumber(entry.downloadCount),
        entry.averageRating !== undefined ? entry.averageRating.toFixed(1) : '-',
        describe(entry)
    ]);
    printRows([['Extension', 'Version', 'Downloads', 'Rating', 'Description'], ...rows]);

    const last = offset + entries.length;
    console.log();
    console.log(`Showing ${offset + 1}-${last} of ${formatNumber(totalSize)}.`);
    if (last < totalSize) {
        console.log(`Pass --offset ${last} for the next page.`);
    }
}

/**
 * The description, prefixed with anything a reader should weigh before installing. Deprecation is
 * the one flag worth spending row width on here; `show` reports the rest.
 */
function describe(entry: SearchEntry): string {
    const description = truncate(entry.description ?? '', DESCRIPTION_WIDTH);
    return entry.deprecated ? `(deprecated) ${description}`.trimEnd() : description;
}
