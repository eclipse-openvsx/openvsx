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

import { FC } from 'react';
import { Box, Chip } from '@mui/material';
import type { SearchExplainEntry, SearchScoreDetail } from '../../../extension-registry-types';

const MONO = 'ui-monospace, SFMono-Regular, Menlo, monospace';

const fixed = (value: number | undefined, digits = 3): string =>
    value === undefined || value === null ? '—' : value.toFixed(digits);

/**
 * The engine's account of a score, as the tree it is.
 * <p>
 * Rendered rather than summarised because the shape is the information: which clause matched, what it
 * contributed, and what that in turn was made of. Any summary would be this page deciding in advance
 * which part of the answer someone is looking for.
 */
const ScoreDetailTree: FC<{ detail: SearchScoreDetail; depth?: number }> = ({ detail, depth = 0 }) => (
    <Box sx={{ pl: depth === 0 ? 0 : 2, borderLeft: depth === 0 ? 0 : '1px solid', borderColor: 'divider' }}>
        <Box sx={{ display: 'flex', gap: 1, fontFamily: MONO, fontSize: '0.75rem', py: '1px' }}>
            <Box sx={{ minWidth: '4.5rem', textAlign: 'right', color: 'text.primary', flexShrink: 0 }}>
                {detail.value.toFixed(4)}
            </Box>
            <Box sx={{ color: 'text.secondary' }}>
                {detail.description}
                {detail.truncated && (
                    <Box component='span' sx={{ color: 'warning.main', ml: 0.5 }}>
                        … (deeper steps omitted)
                    </Box>
                )}
            </Box>
        </Box>
        {detail.details.map((child, index) => (
            <ScoreDetailTree key={`${child.description}-${index}`} detail={child} depth={depth + 1} />
        ))}
    </Box>
);

/**
 * Everything a single result's score is made of, in the order it is made of it.
 * <p>
 * The score is a product of two halves that want entirely different fixes, so the arithmetic is written
 * out rather than left to be inferred from three columns: the relevance terms sum, the penalties multiply,
 * and the result multiplies the text score. Each number appears once, where it is used.
 */
export const ScoreBreakdown: FC<{ entry: SearchExplainEntry }> = ({ entry }) => {
    const stale =
        entry.currentRelevance !== undefined && Math.abs(entry.currentRelevance - entry.storedRelevance) > 0.01;

    return (
        <Box sx={{ py: 2, pl: 4, pr: 2, bgcolor: 'action.hover' }}>
            <Box sx={{ fontFamily: MONO, fontSize: '0.8125rem', mb: 1.5 }}>
                score {fixed(entry.score)} = text {fixed(entry.textScore)} × relevance {fixed(entry.storedRelevance)}
            </Box>

            <Box sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'text.secondary', mb: 0.5 }}>Relevance</Box>
            <Box sx={{ fontFamily: MONO, fontSize: '0.75rem', mb: 0.5 }}>
                rating {fixed(entry.rating)} + downloads {fixed(entry.downloads)} + recency {fixed(entry.recency)}
                {/* The factors are configurable and both can apply, so what they cost is reported rather
                    than described - "halved" was true of neither case reliably. */}
                {entry.unverified && (
                    <Chip
                        size='small'
                        label={`× ${fixed(entry.unverifiedFactor, 2)} unverified`}
                        sx={{ ml: 0.5, height: 18 }}
                    />
                )}
                {entry.deprecated && (
                    <Chip
                        size='small'
                        label={`× ${fixed(entry.deprecatedFactor, 2)} deprecated`}
                        sx={{ ml: 0.5, height: 18 }}
                    />
                )}
                {' = '}
                {fixed(entry.storedRelevance)}
            </Box>
            {stale && (
                <Box sx={{ fontSize: '0.75rem', color: 'warning.main', mb: 1 }}>
                    Recomputes to {fixed(entry.currentRelevance)} — the index predates the current formula, so this
                    result was ranked on a value the registry would no longer give it.
                </Box>
            )}

            <Box sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'text.secondary', mt: 1.5, mb: 0.5 }}>
                Text match, as the search engine accounts for it
            </Box>
            {entry.scoreDetail ? (
                <ScoreDetailTree detail={entry.scoreDetail} />
            ) : (
                <Box sx={{ fontSize: '0.75rem', color: 'text.disabled' }}>No account available.</Box>
            )}
        </Box>
    );
};
