/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { Box, Container, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import { ExtensionCard } from '../../components/extension-card';
import { ExtensionGrid } from '../../components/page-primitives';
import { ListLoadError } from '../../components/list-load-error';
import { HomeCuratedSection } from '../../page-settings';
import { SearchEntry } from '../../extension-registry-types';
import { useSearch } from '../../hooks/use-search';
import { CURATED_SIZE, DEFAULT_CURATED_SECTIONS, useCuratedRows } from './use-home-data';

export interface CuratedSectionsProps {
    /** Rows to fetch and render; defaults to "Most downloaded" and "Recently updated". */
    sections?: HomeCuratedSection[];
    /** "See all" click handler; defaults to opening the search page unfiltered. */
    onSeeAll?: () => void;
}

const RowFrame = styled('div')({ position: 'relative' });

// Stand-ins a failed row shows, faded, behind its error: they keep the loaded row's height at every width.
const VANISHED_EXTENSIONS: SearchEntry[] = [
    'Language Support',
    'Color Theme',
    'Code Formatter',
    'Linter',
    'Debugger',
    'Snippets',
    'Git Tools'
].map((displayName, idx) => ({
    url: '',
    files: {},
    name: `vanished-${idx}`,
    namespace: 'open-vsx',
    version: '1.0.0',
    allVersions: [],
    deprecated: false,
    displayName,
    description: 'Extends the editor',
    downloadCount: 12000 * (idx + 1),
    averageRating: 4,
    reviewCount: 8 * (idx + 1)
}));

const RowGrid = styled(ExtensionGrid, { shouldForwardProp: prop => prop !== 'vanished' })<{ vanished: boolean }>(
    ({ vanished }) =>
        vanished ? { filter: 'blur(0.1875rem)', opacity: 0.35, pointerEvents: 'none', userSelect: 'none' } : {}
);

const RowLoadError = styled(ListLoadError)({
    position: 'absolute',
    inset: 0,
    justifyContent: 'center',
    alignContent: 'center',
    padding: '0 1rem',
    textAlign: 'center'
});

/** Renders the curated extension rows (e.g. "Most downloaded"), skipping empty ones. */
export const CuratedSections: FunctionComponent<CuratedSectionsProps> = props => {
    const rows = useCuratedRows(props.sections ?? DEFAULT_CURATED_SECTIONS);
    const { search } = useSearch();
    return (
        <>
            {rows.map(row => {
                // A failed refetch keeps the extensions it already had, like the search results.
                const failed = !!row.error && !row.loading && row.extensions.length === 0;
                if (!row.loading && row.extensions.length === 0 && !failed) {
                    return null;
                }
                return (
                    <Container maxWidth='xl' component='section' key={row.title}>
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                mb: '0.875rem'
                            }}>
                            <Box>
                                <Typography
                                    sx={{
                                        fontSize: { xs: '1rem', sm: '1.4375rem' },
                                        fontWeight: 700,
                                        letterSpacing: '-0.02em'
                                    }}>
                                    {row.title}
                                </Typography>
                                <Typography
                                    component='span'
                                    sx={{
                                        fontSize: '0.8125rem',
                                        color: 'text.disabled',
                                        display: { xs: 'none', sm: 'block' }
                                    }}>
                                    {row.subtitle}
                                </Typography>
                            </Box>
                            <Box
                                component='button'
                                onClick={props.onSeeAll ?? (() => search({ query: '', sortBy: row.sortBy }))}
                                sx={{
                                    background: 'none',
                                    border: 'none',
                                    color: 'secondary.light',
                                    fontSize: '0.875rem',
                                    fontWeight: 600,
                                    cursor: 'pointer'
                                }}>
                                See all →
                            </Box>
                        </Box>
                        <RowFrame>
                            <RowGrid vanished={failed} aria-hidden={failed || undefined}>
                                {/* Fixed index-keyed slots so cards stay mounted across the swap and don't re-fade. */}
                                {Array.from(
                                    { length: row.loading || failed ? CURATED_SIZE : row.extensions.length },
                                    (_, idx) => (
                                        <ExtensionCard
                                            key={idx}
                                            extension={
                                                failed
                                                    ? VANISHED_EXTENSIONS[idx % VANISHED_EXTENSIONS.length]
                                                    : row.extensions[idx]
                                            }
                                            tabIndex={failed ? -1 : undefined}
                                            fadeDelayMs={(idx % CURATED_SIZE) * 200}
                                        />
                                    )
                                )}
                            </RowGrid>
                            {failed && (
                                <RowLoadError
                                    message='We couldn’t load extensions at this time.'
                                    onRetry={row.refetch}
                                />
                            )}
                        </RowFrame>
                    </Container>
                );
            })}
        </>
    );
};
