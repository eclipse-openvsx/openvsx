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

import { FC, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Paper,
    Skeleton,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography
} from '@mui/material';
import type { NameSquattingNamespaceAllow } from '../../../extension-registry-types';
import { Timestamp } from '../../../components/timestamp';
import { handleError } from '../../../utils';
import { SearchField } from './name-squatting-controls';
import { pluralize } from './name-squatting-model';
import { ColumnWidths, NamespaceIdentity, SimilarityChip, ValuePair } from './name-squatting-parts';
import { GroupCard, GroupTable, Toolbar } from './name-squatting-styles';
import { useNameSquattingNamespaceAllowlist, useRemoveNameSquattingAllows } from './use-name-squatting';

const COLUMN_WIDTHS = ['auto', '12rem', '24%', '7rem'];

const RemoveNamespaceAllowDialog: FC<{ allow: NameSquattingNamespaceAllow; onClose: () => void }> = ({
    allow,
    onClose
}) => {
    const { mutateAsync: removeAllows, isPending: removing, error } = useRemoveNameSquattingAllows();

    const handleConfirm = async () => {
        try {
            await removeAllows([allow.id]);
            onClose();
        } catch {
            // shown from the mutation's error below
        }
    };

    return (
        <Dialog open onClose={removing ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{`Remove the allow for namespaces ${allow.namespace} and ${allow.otherNamespace}?`}</DialogTitle>
            <DialogContent>
                {error && (
                    <Alert severity='error' sx={{ mb: 2 }}>
                        {handleError(error)}
                    </Alert>
                )}
                <Typography>
                    If the two names still resemble each other, the pair will appear on the Namespaces tab again.
                </Typography>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={removing}>
                    Cancel
                </Button>
                <Button
                    variant='contained'
                    color='error'
                    onClick={handleConfirm}
                    disabled={removing}
                    startIcon={removing ? <CircularProgress size={20} /> : undefined}>
                    Remove allow
                </Button>
            </DialogActions>
        </Dialog>
    );
};

const AllowRow: FC<{ allow: NameSquattingNamespaceAllow; onRemove: () => void }> = ({ allow, onRemove }) => (
    <TableRow>
        <TableCell>
            <Box sx={{ mb: 0.5 }}>
                <SimilarityChip similarity={allow.similarity} exact={false} />
            </Box>
            <ValuePair first={allow.otherNamespace} second={allow.namespace} />
            <NamespaceIdentity name={allow.namespace} />
        </TableCell>
        <TableCell>
            <Box>{allow.decidedBy ?? 'A former user'}</Box>
            <Typography variant='caption' color='text.secondary'>
                <Timestamp value={allow.decidedAt} />
            </Typography>
        </TableCell>
        <TableCell>
            {allow.reason ?? (
                <Typography variant='body2' color='text.secondary'>
                    No reason given
                </Typography>
            )}
        </TableCell>
        <TableCell align='right'>
            <Button size='small' color='error' onClick={onRemove}>
                Remove
            </Button>
        </TableCell>
    </TableRow>
);

/** The allowed pairs of namespaces, one row each, under the names they had when allowed. */
export const NamespaceAllowlist: FC = () => {
    const [query, setQuery] = useState('');
    const [removeTarget, setRemoveTarget] = useState<NameSquattingNamespaceAllow>();

    const {
        data: allows,
        error,
        isPending,
        isFetchNextPageError,
        hasNextPage,
        isFetchingNextPage,
        fetchNextPage,
        refetch
    } = useNameSquattingNamespaceAllowlist();

    const visible = useMemo(() => {
        const needle = query.trim().toLowerCase();
        return (allows ?? []).filter(
            allow =>
                needle.length === 0 ||
                allow.namespace.toLowerCase().includes(needle) ||
                allow.otherNamespace.toLowerCase().includes(needle)
        );
    }, [allows, query]);

    const renderAllows = () => {
        if (isPending) {
            return error ? null : (
                <Box aria-busy='true' aria-label='Loading the namespace allowlist'>
                    <Skeleton variant='rounded' height='4rem' sx={{ mb: 2 }} />
                </Box>
            );
        }
        if (visible.length === 0) {
            return (
                <Paper variant='outlined' sx={{ p: 3 }}>
                    <Typography variant='h6' component='h3'>
                        {(allows?.length ?? 0) > 0
                            ? 'No allowed namespaces match this filter'
                            : 'No allowed namespaces yet'}
                    </Typography>
                    <Typography color='text.secondary'>
                        When you allow a pair on the Namespaces tab, the pair is listed here so you can remove it later.
                    </Typography>
                </Paper>
            );
        }
        return (
            <GroupCard variant='outlined'>
                <TableContainer>
                    <GroupTable size='small'>
                        <ColumnWidths widths={COLUMN_WIDTHS} />
                        <TableHead>
                            <TableRow>
                                <TableCell>Names when allowed (A = target, B = look-alike)</TableCell>
                                <TableCell>Allowed by</TableCell>
                                <TableCell>Reason</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {visible.map(allow => (
                                <AllowRow key={allow.id} allow={allow} onRemove={() => setRemoveTarget(allow)} />
                            ))}
                        </TableBody>
                    </GroupTable>
                </TableContainer>
            </GroupCard>
        );
    };

    return (
        <>
            <Toolbar>
                <SearchField
                    value={query}
                    onChange={setQuery}
                    placeholder='Search namespace'
                    label='Search allowed namespaces'
                />
            </Toolbar>
            {error && (
                <Alert
                    severity='error'
                    sx={{ mb: 2 }}
                    action={
                        <Button
                            color='inherit'
                            size='small'
                            onClick={() => (isFetchNextPageError ? fetchNextPage() : refetch())}>
                            Try again
                        </Button>
                    }>
                    {handleError(error)}
                </Alert>
            )}
            {!isPending && visible.length > 0 && (
                <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                    {pluralize(visible.length, 'allowed pair')}
                    {hasNextPage && ' so far'}
                </Typography>
            )}
            {renderAllows()}
            {hasNextPage && !isFetchNextPageError && (
                <Button
                    fullWidth
                    variant='outlined'
                    onClick={() => fetchNextPage()}
                    disabled={isFetchingNextPage}
                    startIcon={isFetchingNextPage ? <CircularProgress size={16} /> : undefined}>
                    Load more
                </Button>
            )}
            {removeTarget && (
                <RemoveNamespaceAllowDialog allow={removeTarget} onClose={() => setRemoveTarget(undefined)} />
            )}
        </>
    );
};
