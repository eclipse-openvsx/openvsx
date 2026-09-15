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

/**
 * Prints rows as an aligned table, indented by two spaces. Every column but the last is padded,
 * so a trailing empty cell doesn't leave ragged whitespace behind.
 */
export function printRows(rows: string[][]): void {
    const widths: number[] = [];
    for (const row of rows) {
        row.forEach((cell, i) => widths[i] = Math.max(widths[i] ?? 0, cell.length));
    }
    for (const row of rows) {
        const line = row
            .map((cell, i) => (i === row.length - 1 ? cell : cell.padEnd(widths[i])))
            .join('  ')
            .trimEnd();
        console.log(`  ${line}`);
    }
}

/** Formats a count with thousands separators, and the noun pluralised to match. */
export function formatCount(count: number | undefined, noun: string): string {
    const value = count ?? 0;
    return `${formatNumber(value)} ${value === 1 ? noun : noun + 's'}`;
}

export function formatNumber(value: number | undefined): string {
    return (value ?? 0).toLocaleString('en-US');
}

/**
 * Shortens `text` to at most `max` characters, marking that it was cut. A `max` of zero or less
 * leaves no room for the marker either, so it yields nothing rather than an ellipsis wider than the
 * budget it was given.
 */
export function truncate(text: string, max: number): string {
    if (max <= 0) {
        return '';
    }
    const collapsed = text.replace(/\s+/g, ' ').trim();
    return collapsed.length <= max ? collapsed : `${collapsed.substring(0, max - 1)}…`;
}
