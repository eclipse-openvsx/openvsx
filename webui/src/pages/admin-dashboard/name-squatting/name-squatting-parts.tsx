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
import { Alert, Box, Button, Chip, Link, Typography } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
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

const Mono = styled('span')(({ theme }) => ({
    fontFamily: 'monospace',
    fontSize: '0.8125rem',
    color: theme.palette.text.secondary
}));

const Links = styled(Box)(({ theme }) => ({
    display: 'flex',
    gap: theme.spacing(1.5),
    fontSize: '0.8125rem'
}));

/** Display name, identifier and links to the public page and the admin Extensions page. */
export const ExtensionIdentity: FC<{ extension: NameSquattingExtension }> = ({ extension }) => (
    <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Typography component='span' fontWeight={600}>
            {extension.displayName ?? extension.name}
        </Typography>
        <Mono>{extensionId(extension)}</Mono>
        <Links>
            <Link
                component={RouterLink}
                to={createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name])}
                target='_blank'
                rel='noopener'>
                Public page
            </Link>
            <Link
                component={RouterLink}
                to={createRoute([AdminDashboardRoutes.EXTENSION_ADMIN, extension.namespace, extension.name])}>
                Admin
            </Link>
        </Links>
    </Box>
);

/** Links to a namespace's public page and to the admin Namespaces page. */
export const NamespaceLinks: FC<{ name: string }> = ({ name }) => (
    <Links>
        <Link
            component={RouterLink}
            to={createRoute([NamespaceDetailRoutes.ROOT, name])}
            target='_blank'
            rel='noopener'>
            Public page
        </Link>
        <Link component={RouterLink} to={createRoute([AdminDashboardRoutes.NAMESPACE_ADMIN, name])}>
            Admin
        </Link>
    </Links>
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
