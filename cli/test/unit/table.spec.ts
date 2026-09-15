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
import { truncate } from '../../src/table';

/**
 * `table.ts` exists to be shared between commands, so its helpers are asserted at their boundaries
 * here rather than only through whichever command happens to call them with a comfortable width.
 */
describe('truncate', () => {

    it('leaves text that fits alone', () => {
        expect(truncate('short', 10)).toBe('short');
    });

    it('collapses whitespace and trims', () => {
        expect(truncate('  two   words\n', 20)).toBe('two words');
    });

    it('keeps the result within the given width, marker included', () => {
        expect(truncate('abcdefghij', 5)).toBe('abcd…');
        expect(truncate('abcdefghij', 5)).toHaveLength(5);
    });

    it('spends its whole budget on the marker when only one character fits', () => {
        expect(truncate('abcdefghij', 1)).toBe('…');
    });

    // A zero budget leaves no room for the marker either. Returning the ellipsis anyway would be one
    // character wider than asked for, which is how a truncating helper ends up wrapping a table.
    it('yields nothing for a width of zero or less', () => {
        expect(truncate('abcdefghij', 0)).toBe('');
        expect(truncate('abcdefghij', -1)).toBe('');
    });

    it('yields nothing for text that is only whitespace', () => {
        expect(truncate('   ', 10)).toBe('');
    });
});
