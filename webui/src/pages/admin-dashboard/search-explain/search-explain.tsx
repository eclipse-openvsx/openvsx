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

import { FC, FormEvent, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    Collapse,
    IconButton,
    LinearProgress,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Tooltip,
    Typography
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import type { SearchExplainEntry } from '../../../extension-registry-types';
import { ScoreBreakdown } from './score-breakdown';
import { formatCompactNumber, handleError } from '../../../utils';
import { useSearchIndex } from '../search-index/use-search-index';
import { useSearchExplain } from './use-search-explain';

// A plain Table rather than the DataGrid the rest of the admin dashboard uses: these rows are in
// search result order, which is the thing being examined, and a grid that invites the reader to sort
// by score or downloads would let them destroy it with one click.
const RESULT_SIZE = 25;

const num = (value: number | undefined, digits = 3): string =>
    value === undefined || value === null ? '—' : value.toFixed(digits);

/**
 * How much of the relevance each term accounts for, drawn as one bar.
 * <p>
 * The numbers alone make you do arithmetic across a row to see which term decided the ordering; the bar
 * says it at a glance, which is the question this page exists to answer.
 */
const RelevanceBar: FC<{ entry: SearchExplainEntry }> = ({ entry }) => {
    const parts = [
        { key: 'rating', value: entry.rating ?? 0, color: 'primary.main' },
        { key: 'downloads', value: entry.downloads ?? 0, color: 'success.main' },
        { key: 'recency', value: entry.recency ?? 0, color: 'warning.main' }
    ];
    const total = parts.reduce((sum, part) => sum + part.value, 0);
    if (total === 0) {
        return <Box sx={{ color: 'text.disabled', fontSize: '0.75rem' }}>—</Box>;
    }

    return (
        <Tooltip title={parts.map(part => `${part.key} ${part.value.toFixed(3)}`).join('  ·  ')} placement='top' arrow>
            <Box sx={{ display: 'flex', height: 10, borderRadius: 1, overflow: 'hidden', minWidth: 120 }}>
                {parts.map(part => (
                    <Box key={part.key} sx={{ width: `${(part.value / total) * 100}%`, bgcolor: part.color }} />
                ))}
            </Box>
        </Tooltip>
    );
};

/**
 * Why a search returned what it returned.
 * <p>
 * A result's score is two numbers multiplied: how well the query matched the extension's text, and a
 * relevance computed from its rating, downloads and age when it was indexed. Only the product reaches the
 * result list, so a ranking that looks wrong gives no clue which half is responsible - and the two want
 * entirely different fixes. One is the query's field weights, the other the relevance formula. This shows
 * both, for the search the registry actually runs rather than a reconstruction of it.
 */
/** One result: the summary row, and the breakdown it expands into. */
const SearchExplainRow: FC<{ entry: SearchExplainEntry; expanded: boolean; onToggle: () => void }> = ({
    entry,
    expanded,
    onToggle
}) => (
    <>
        <TableRow hover sx={{ cursor: 'pointer', '& > *': { borderBottom: 'unset' } }} onClick={onToggle}>
            <TableCell sx={{ width: 32 }}>
                <IconButton size='small' aria-label='Show the score breakdown'>
                    {expanded ? (
                        <KeyboardArrowUpIcon fontSize='inherit' />
                    ) : (
                        <KeyboardArrowDownIcon fontSize='inherit' />
                    )}
                </IconButton>
            </TableCell>
            <TableCell>{entry.position + 1}</TableCell>
            <TableCell>
                <Box sx={{ fontWeight: 500 }}>
                    {entry.namespace}.{entry.name}
                </Box>
                <Box sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>
                    {formatCompactNumber(entry.downloadCount)} downloads
                    {entry.timestamp ? ` · ${entry.timestamp.slice(0, 10)}` : ''}
                </Box>
            </TableCell>
            <TableCell align='right'>{num(entry.score)}</TableCell>
            <TableCell align='right'>{num(entry.textScore)}</TableCell>
            <TableCell align='right'>
                {num(entry.storedRelevance)}
                {/* A stored relevance the formula no longer agrees with means the index predates a
                change to it - worth seeing before drawing any conclusion from the ordering. */}
                {entry.currentRelevance !== undefined &&
                    Math.abs(entry.currentRelevance - entry.storedRelevance) > 0.01 && (
                        <Tooltip
                            title={`Recomputes to ${entry.currentRelevance.toFixed(
                                3
                            )} — the index is older than the formula`}
                            arrow>
                            <Box component='span' sx={{ ml: 0.5, color: 'warning.main', cursor: 'help' }}>
                                ≠
                            </Box>
                        </Tooltip>
                    )}
            </TableCell>
            <TableCell>
                <RelevanceBar entry={entry} />
            </TableCell>
            <TableCell>
                {entry.unverified && <Chip size='small' label='unverified' sx={{ mr: 0.5 }} />}
                {entry.deprecated && <Chip size='small' label='deprecated' />}
            </TableCell>
        </TableRow>
        <TableRow>
            {/* Spans every column of the row above, the leading expander and trailing chips included. */}
            <TableCell colSpan={8} sx={{ p: 0, borderBottom: '1px solid', borderColor: 'divider' }}>
                <Collapse in={expanded} timeout='auto' unmountOnExit>
                    <ScoreBreakdown entry={entry} />
                </Collapse>
            </TableCell>
        </TableRow>
    </>
);

export const SearchExplainAdmin: FC = () => {
    const [term, setTerm] = useState('');
    const [submitted, setSubmitted] = useState('');
    const [expanded, setExpanded] = useState<string | undefined>(undefined);
    // Only elasticsearch scores anything, so on any other engine there is nothing here to explain and
    // the endpoint refuses. Shared query key with the Search Index page, so this costs no extra request.
    const { data: index } = useSearchIndex();
    const unsupported = index !== undefined && index.implementation !== 'elasticsearch';
    const { data, isFetching, isFetchingNextPage, hasNextPage, fetchNextPage, error } = useSearchExplain(
        submitted,
        RESULT_SIZE
    );
    // Every page fetched so far, in order: the point of paging here is to reach a deep result without
    // losing sight of what is above it.
    const pages = data?.pages ?? [];
    const entries = pages.flatMap(page => page.entries);
    const summary = pages[0];

    const submit = (event: FormEvent) => {
        event.preventDefault();
        setSubmitted(term.trim());
    };

    return (
        <Box sx={{ p: 3 }}>
            <Box sx={{ mb: 3 }}>
                <Typography variant='h4' component='h1'>
                    Search Explain
                </Typography>
                <Typography variant='body2' color='text.secondary'>
                    Run a search and see what each result&apos;s score is made of. The score is the text match
                    multiplied by the stored relevance, so a result in the wrong place is either a query-weighting
                    problem or a relevance-formula one — this says which.
                </Typography>
            </Box>

            {unsupported && (
                <Alert severity='warning' sx={{ mb: 3 }}>
                    Searches on this registry are answered by <strong>{index.implementation}</strong>, which orders rows
                    rather than scoring them. There is no score to take apart, so there is nothing for this page to
                    report.
                </Alert>
            )}

            <Box component='form' onSubmit={submit} sx={{ display: 'flex', gap: 2, mb: 3, maxWidth: 640 }}>
                <TextField
                    fullWidth
                    size='small'
                    label='Search term'
                    placeholder='markdown'
                    value={term}
                    onChange={event => setTerm(event.target.value)}
                    disabled={unsupported}
                />
                <Button
                    type='submit'
                    variant='contained'
                    disabled={unsupported || term.trim().length === 0 || isFetching}>
                    Explain
                </Button>
            </Box>

            {error && <Alert severity='error'>{handleError(error)}</Alert>}
            {isFetching && !isFetchingNextPage && <LinearProgress sx={{ mb: 2 }} />}

            {summary && (
                <>
                    <Alert severity='info' sx={{ mb: 2 }}>
                        {summary.totalHits.toLocaleString()} matches for <strong>{summary.query}</strong>, showing{' '}
                        {entries.length}. Every downloads term is measured against the registry&apos;s largest count,{' '}
                        <strong>{formatCompactNumber(summary.references.maxDownloadCount)}</strong>, and every rating is
                        smoothed towards the registry average of{' '}
                        <strong>{summary.references.averageReviewRating.toFixed(2)}</strong> — which is why a term can
                        contribute nothing for reasons that have nothing to do with the extension.
                    </Alert>

                    <TableContainer component={Paper} elevation={0} variant='outlined'>
                        <Table size='small'>
                            <TableHead>
                                <TableRow>
                                    <TableCell />
                                    <TableCell>#</TableCell>
                                    <TableCell>Extension</TableCell>
                                    <TableCell align='right'>Score</TableCell>
                                    <TableCell align='right'>Text</TableCell>
                                    <TableCell align='right'>Relevance</TableCell>
                                    <TableCell>Relevance made of</TableCell>
                                    <TableCell />
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {entries.map(entry => {
                                    const id = `${entry.namespace}.${entry.name}`;
                                    return (
                                        <SearchExplainRow
                                            key={id}
                                            entry={entry}
                                            expanded={expanded === id}
                                            onToggle={() => setExpanded(expanded === id ? undefined : id)}
                                        />
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>

                    {hasNextPage && (
                        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
                            <Button variant='outlined' onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
                                {isFetchingNextPage
                                    ? 'Loading…'
                                    : `Show ${Math.min(RESULT_SIZE, summary.totalHits - entries.length)} more`}
                            </Button>
                        </Box>
                    )}
                </>
            )}
        </Box>
    );
};
