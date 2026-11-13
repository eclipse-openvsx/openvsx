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

import { describe, expect, it } from 'vitest';
import { diffNames } from '../../../../../src/pages/admin-dashboard/name-squatting/name-diff';

const changed = (segments: { text: string; changed: boolean }[]) =>
    segments.filter(segment => segment.changed).map(segment => segment.text);

describe('diffNames', () => {
    it('marks a swapped character on both sides', () => {
        const [a, b] = diffNames('python', 'pyth0n');

        expect(changed(a)).toEqual(['o']);
        expect(changed(b)).toEqual(['0']);
    });

    it('marks only the added characters of the longer name', () => {
        const [a, b] = diffNames('ESLint', 'ESLint Pro');

        expect(changed(a)).toEqual([]);
        expect(changed(b)).toEqual([' Pro']);
    });

    // The similarity check ignores case, so a case-only difference is not one worth highlighting.
    it('ignores case but keeps each name spelled as given', () => {
        const [a, b] = diffNames('Go', 'go');

        expect(a).toEqual([{ text: 'Go', changed: false }]);
        expect(b).toEqual([{ text: 'go', changed: false }]);
    });

    it('handles an empty name', () => {
        expect(diffNames('', 'abc')).toEqual([[], [{ text: 'abc', changed: true }]]);
    });
});
