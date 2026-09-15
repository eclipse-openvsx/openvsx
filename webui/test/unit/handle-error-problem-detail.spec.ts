/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { describe, it, expect } from 'vitest';
import { handleError } from '../../src/utils';

// Spring answers a failed request-parameter constraint with problem+json, which carries none of
// ErrorResponse's fields. Without these branches every such response read as "An unexpected error
// occurred", hiding the one thing the admin needed to know.
describe('handleError with a problem+json body', () => {
    it('reports the offending parameters a validation failure lists', () => {
        expect(
            handleError({
                instance: '/admin/search-explain',
                status: 400,
                title: 'Validation failed',
                errors: ['size: parameter must not exceed 100']
            })
        ).toBe('Validation failed: size: parameter must not exceed 100');
    });

    it('joins several violations', () => {
        expect(
            handleError({
                status: 400,
                title: 'Validation failed',
                errors: ['query: parameter must not be blank', 'offset: parameter must not be negative']
            })
        ).toBe('Validation failed: query: parameter must not be blank, offset: parameter must not be negative');
    });

    it('falls back to detail for problems that carry prose instead', () => {
        expect(handleError({ status: 500, title: 'Internal Server Error', detail: 'Something broke' })).toBe(
            'Internal Server Error: Something broke'
        );
        expect(handleError({ status: 404, title: 'Not Found' })).toBe('Not Found');
    });

    // The ResultJson shape stays ahead of it, since that is what most of this API returns.
    it('still prefers a ResultJson error', () => {
        expect(handleError({ error: 'query must not be blank.', status: 400, title: 'ignored' })).toBe(
            'query must not be blank.'
        );
    });
});
