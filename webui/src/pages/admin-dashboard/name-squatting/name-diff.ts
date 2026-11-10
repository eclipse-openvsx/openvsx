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

export interface DiffSegment {
    text: string;
    /** Not part of the longest run of characters both names share. */
    changed: boolean;
}

/**
 * Splits two names into the characters they share, in order, and those that differ, ignoring case
 * as the similarity check does. Each name keeps its own spelling in the segments.
 */
export function diffNames(a: string, b: string): [DiffSegment[], DiffSegment[]] {
    const lowerA = a.toLowerCase();
    const lowerB = b.toLowerCase();
    // lcs[i][j]: length of the longest common subsequence of lowerA from i and lowerB from j
    const lcs = Array.from({ length: a.length + 1 }, () => new Uint16Array(b.length + 1));
    for (let i = a.length - 1; i >= 0; i--) {
        for (let j = b.length - 1; j >= 0; j--) {
            lcs[i][j] = lowerA[i] === lowerB[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
        }
    }
    const keptA = new Array<boolean>(a.length).fill(false);
    const keptB = new Array<boolean>(b.length).fill(false);
    let i = 0;
    let j = 0;
    while (i < a.length && j < b.length) {
        if (lowerA[i] === lowerB[j]) {
            keptA[i++] = true;
            keptB[j++] = true;
        } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
            i++;
        } else {
            j++;
        }
    }
    return [toSegments(a, keptA), toSegments(b, keptB)];
}

function toSegments(text: string, kept: boolean[]): DiffSegment[] {
    const segments: DiffSegment[] = [];
    for (let index = 0; index < text.length; index++) {
        const changed = !kept[index];
        const last = segments[segments.length - 1];
        if (last?.changed === changed) {
            last.text += text[index];
        } else {
            segments.push({ text: text[index], changed });
        }
    }
    return segments;
}
