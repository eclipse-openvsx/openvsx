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
import { Alert, Box, Button, Chip, Link } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { Link as RouterLink } from 'react-router';
import type { NameSquattingExtension } from '../../../extension-registry-types';
import { NamespaceDetailRoutes } from '../../namespace-detail/namespace-detail-routes';
import type { ErrorResponse } from '../../../server-request';
import { createRoute, handleError } from '../../../utils';
import { ExtensionDetailRoutes } from '../../extension-detail/extension-detail-routes';
import { AdminDashboardRoutes } from '../admin-dashboard-routes';
import { diffNames, DiffSegment } from './name-diff';
import { formatSimilarity, similarityColor } from './name-squatting-model';
import { extensionId } from './use-name-squatting';

const NewTab = () => (
    <OpenInNewIcon sx={{ fontSize: '0.875rem', ml: 0.5, verticalAlign: 'middle', color: 'text.secondary' }} />
);

const monoLink = {
    fontFamily: 'monospace',
    fontSize: '0.8125rem',
    color: 'text.secondary'
} as const;

/**
 * Display name and identifier. Throughout the page, the arrow marks the public page (a new tab) and the
 * monospace identifier opens the admin page.
 */
export const ExtensionIdentity: FC<{ extension: NameSquattingExtension }> = ({ extension }) => (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', minWidth: 0 }}>
        <Link
            component={RouterLink}
            to={createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name])}
            target='_blank'
            rel='noopener'
            title='Open the public page in a new tab'
            underline='hover'
            color='inherit'
            fontWeight={600}>
            {extension.displayName ?? extension.name}
            <NewTab />
        </Link>
        <Link
            component={RouterLink}
            to={createRoute([AdminDashboardRoutes.EXTENSION_ADMIN, extension.namespace, extension.name])}
            title='Open on the admin Extensions page'
            underline='hover'
            sx={monoLink}>
            {extensionId(extension)}
        </Link>
    </Box>
);

/** A namespace's name, opening the admin page, and the arrow to its public page, as for extensions. */
export const NamespaceIdentity: FC<{ name: string }> = ({ name }) => (
    <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <Link
            component={RouterLink}
            to={createRoute([AdminDashboardRoutes.NAMESPACE_ADMIN, name])}
            title='Open on the admin Namespaces page'
            underline='hover'
            sx={{ ...monoLink, fontSize: 'inherit', fontWeight: 600, color: 'text.primary' }}>
            {name}
        </Link>
        <Link
            component={RouterLink}
            to={createRoute([NamespaceDetailRoutes.ROOT, name])}
            target='_blank'
            rel='noopener'
            title='Open the public page in a new tab'
            aria-label={`Open the public page of ${name} in a new tab`}
            sx={{ display: 'inline-flex' }}>
            <NewTab />
        </Link>
    </Box>
);

/** Column widths for a `GroupTable`, in column order. */
export const ColumnWidths: FC<{ widths: string[] }> = ({ widths }) => (
    <colgroup>
        {widths.map((width, index) => (
            <col key={index} style={{ width }} />
        ))}
    </colgroup>
);

export const SimilarityChip: FC<{ similarity: number; exact: boolean }> = ({ similarity, exact }) => (
    <Chip size='small' label={formatSimilarity(similarity, exact)} color={similarityColor(similarity, exact)} />
);

const Changed = styled('mark')(({ theme }) => ({
    backgroundColor: alpha(theme.palette.warning.main, 0.3),
    color: 'inherit',
    borderRadius: '0.125rem'
}));

const Segments: FC<{ segments: DiffSegment[] }> = ({ segments }) => (
    <>
        {segments.map((segment, index) =>
            segment.changed ? <Changed key={index}>{segment.text}</Changed> : <span key={index}>{segment.text}</span>
        )}
    </>
);

const PairGrid = styled(Box)(({ theme }) => ({
    display: 'grid',
    gridTemplateColumns: 'auto 1fr',
    columnGap: theme.spacing(1),
    fontSize: '0.875rem',
    wordBreak: 'break-word'
}));

const PairKey = styled('span')(({ theme }) => ({
    color: theme.palette.text.secondary,
    fontWeight: 600
}));

/**
 * The two values one above the other, the characters they do not share highlighted. A is the
 * extension published first (or the other extension of an allowed pair), B the one published later.
 */
export const ValuePair: FC<{ first: string; second: string }> = ({ first, second }) => {
    const [segmentsA, segmentsB] = diffNames(first, second);
    return (
        <PairGrid>
            <PairKey>A</PairKey>
            <span>
                <Segments segments={segmentsA} />
            </span>
            <PairKey>B</PairKey>
            <span>
                <Segments segments={segmentsB} />
            </span>
        </PairGrid>
    );
};

// Not capped: the dialog scrolls its content itself and keeps its buttons in view.
const PairGroups = styled('ul')(({ theme }) => ({
    listStyle: 'none',
    margin: 0,
    padding: theme.spacing(1.5, 2),
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
    fontFamily: 'monospace',
    fontSize: '0.875rem',
    '& > li + li': { marginTop: theme.spacing(1.5) }
}));

const Seconds = styled('ul')(({ theme }) => ({
    listStyle: 'none',
    margin: theme.spacing(0.5, 0, 0, 0.5),
    padding: theme.spacing(0, 0, 0, 1.5),
    borderLeft: `2px solid ${theme.palette.divider}`
}));

/** Pairs grouped under their first value, in the order those first appear. */
const groupPairs = (pairs: { first: string; second: string }[]) => {
    const groups = new Map<string, string[]>();
    pairs.forEach(({ first, second }) => groups.set(first, [...(groups.get(first) ?? []), second]));
    return [...groups].map(([first, seconds]) => ({ first, seconds }));
};

/** Selected pairs, each look-alike under the name it resembles, which is named once however many do. */
export const GroupedPairs: FC<{ pairs: { first: string; second: string }[]; label: string }> = ({ pairs, label }) => (
    <PairGroups aria-label={label}>
        {groupPairs(pairs).map(group => (
            <li key={group.first}>
                <Box component='span' sx={{ fontWeight: 600 }}>
                    {group.first}
                </Box>
                <Seconds>
                    {group.seconds.map(second => (
                        <li key={second}>{second}</li>
                    ))}
                </Seconds>
            </li>
        ))}
    </PairGroups>
);

/** The server answers 503 when a lookup ran past its statement timeout and was stopped. */
const isTimeout = (error: unknown): boolean => (error as Partial<ErrorResponse> | null)?.status === 503;

/** A failed lookup, with a retry; a 503 is the statement timeout, which a retry may well get past. */
export const LoadError: FC<{ error: unknown; onRetry: () => void }> = ({ error, onRetry }) => (
    <Alert
        severity='error'
        sx={{ mb: 2 }}
        action={
            <Button color='inherit' size='small' onClick={onRetry}>
                Try again
            </Button>
        }>
        {isTimeout(error) ? (
            <>
                <strong>The check took too long and was stopped.</strong> Nothing was changed. Try again; if this keeps
                happening, raise <code>ovsx.scanning.name-similarity.statement-timeout</code> in the server
                configuration.
            </>
        ) : (
            handleError(error)
        )}
    </Alert>
);
