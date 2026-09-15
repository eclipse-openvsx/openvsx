/********************************************************************************
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
 ********************************************************************************/

import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../support/test-providers';
import { ExtensionCard } from '../../../src/components/extension-card';
import { Extension } from '../../../src/extension-registry-types';
import { formatCompactNumber, formatRating } from '../../../src/utils';

const extension = (overrides: Partial<Extension> = {}): Extension =>
    ({
        namespaceUrl: 'https://example.test/api/foo',
        reviewsUrl: 'https://example.test/api/foo/bar/reviews',
        files: {},
        name: 'bar',
        namespace: 'foo',
        namespaceDisplayName: 'foo',
        displayName: 'Bar',
        version: '1.0.0',
        targetPlatform: 'universal',
        publishedBy: { loginName: 'someone', tokensUrl: '', createTokenUrl: '' },
        verified: true,
        allVersions: {},
        downloadCount: 0,
        reviewCount: 0,
        versionAlias: [],
        timestamp: '2026-01-01T00:00:00Z',
        downloads: {},
        deprecated: false,
        downloadable: true,
        ...overrides
    }) as unknown as Extension;

// testing-library normalizes whitespace in the DOM before matching, and some locales put a
// non-breaking space inside a compact number (fr-FR renders 1234 as '1,2 k'), so an expected string
// straight from the formatter would not compare equal. Normalize it the same way.
const normalized = (value: string) => value.replace(/\s+/g, ' ').trim();

// Scope: these cover the footer's rendering rules only - which elements appear for a given review
// count. They deliberately do not claim to cover the overflow fix itself, which is CSS: jsdom
// applies no layout, so an assertion about clipping or spilling would pass either way. Catching a
// regression there needs a browser (the Playwright suite runs against the deployed site on a
// schedule, so it cannot gate a change here).
describe('ExtensionCard footer', () => {
    // Expected strings come from the same formatters the component uses: compact and one-decimal
    // formatting are locale-dependent ('41M' is '41 Mio.' under de-DE), so hard-coding them fails
    // on a runner with a non-English locale.
    it('shows the rating as a single star and a number, alongside the download count', () => {
        renderWithProviders(
            <ExtensionCard extension={extension({ averageRating: 5, reviewCount: 16, downloadCount: 41183269 })} />
        );

        // One star, not the five ExtensionRatingStars draws - the intrinsic width that could not shrink.
        expect(screen.getAllByTestId('StarIcon')).toHaveLength(1);
        expect(screen.getByText(normalized(formatRating(5)))).toBeInTheDocument();
        expect(screen.getByText(normalized(`(${formatCompactNumber(16)})`))).toBeInTheDocument();
        expect(screen.getByText(normalized(formatCompactNumber(41183269)))).toBeInTheDocument();
    });

    // Nothing to say about a rating nobody has given: five grey stars claimed the width and stated a
    // score that does not exist.
    it('shows no rating at all when there are no reviews', () => {
        renderWithProviders(<ExtensionCard extension={extension({ downloadCount: 1234 })} />);

        expect(screen.queryByTestId('StarIcon')).not.toBeInTheDocument();
        expect(screen.getByText(normalized(formatCompactNumber(1234)))).toBeInTheDocument();
    });
});
