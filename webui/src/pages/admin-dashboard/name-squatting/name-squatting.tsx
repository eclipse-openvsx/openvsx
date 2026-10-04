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

import { FC, useEffect, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    CircularProgress,
    Paper,
    TablePagination,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from '@mui/material';
import { styled } from '@mui/material/styles';
import type { NameSquattingFlag, NameSquattingState } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { useDebouncedCallback } from '../../../hooks/use-debounced-callback';
import { NameSquattingRow } from './name-squatting-row';
import { NameSquattingAction, NameSquattingActionDialog } from './name-squatting-action-dialog';
import {
    NameSquattingFilters,
    useClearNameSquattingFlags,
    useDeleteNameSquattingExtensions,
    useNameSquattingCounts,
    useNameSquattingFlags
} from './use-name-squatting';

const PageLayout = styled(Box)(({ theme }) => ({
    padding: theme.spacing(3),
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2)
}));

const FilterBar = styled(Box)(({ theme }) => ({
    display: 'flex',
    gap: theme.spacing(2),
    flexWrap: 'wrap',
    alignItems: 'center'
}));

const FlagList = styled(Box)(({ theme }) => ({
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2)
}));

const CenteredProgress = styled(Box)(({ theme }) => ({
    display: 'flex',
    justifyContent: 'center',
    padding: theme.spacing(8, 0)
}));

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

const STATE_OPTIONS: { value: NameSquattingState; label: string }[] = [
    { value: 'PUBLISHED', label: 'Published' },
    { value: 'DEACTIVATED', label: 'Deactivated' },
    { value: 'REJECTED', label: 'Publication blocked' }
];

export const NameSquatting: FC = () => {
    const [publisherInput, setPublisherInput] = useState('');
    const [namespaceInput, setNamespaceInput] = useState('');
    const [nameInput, setNameInput] = useState('');
    const [search, setSearch] = useState<NameSquattingFilters>({});
    const [states, setStates] = useState<NameSquattingState[]>([]);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
    const [action, setAction] = useState<NameSquattingAction>('clear');
    const [selectedFlag, setSelectedFlag] = useState<NameSquattingFlag | undefined>();
    const [dialogOpen, setDialogOpen] = useState(false);
    const [errorDismissed, setErrorDismissed] = useState(false);

    const applySearch = useDebouncedCallback((next: NameSquattingFilters) => {
        setSearch(next);
        setPage(0);
    });

    useEffect(() => {
        applySearch({
            publisher: publisherInput.trim() || undefined,
            namespace: namespaceInput.trim() || undefined,
            name: nameInput.trim() || undefined
        });
    }, [applySearch, publisherInput, namespaceInput, nameInput]);

    const filters = useMemo<NameSquattingFilters>(
        () => ({ ...search, state: states.length > 0 ? states : undefined }),
        [search, states]
    );

    const { data, isFetching: loading, error: loadError } = useNameSquattingFlags(filters, page * pageSize, pageSize);
    const { data: counts } = useNameSquattingCounts(filters);
    const { mutateAsync: clearFlags } = useClearNameSquattingFlags();
    const { mutateAsync: deleteExtensions } = useDeleteNameSquattingExtensions();

    const flags: readonly NameSquattingFlag[] = data?.flags ?? [];
    const totalSize = data?.totalSize ?? 0;

    // A fresh load error should be shown again even if a previous one was dismissed.
    useEffect(() => {
        setErrorDismissed(false);
    }, [loadError]);

    const error = loadError && !errorDismissed ? handleError(loadError as Error) : null;

    const openDialog = (nextAction: NameSquattingAction, flag: NameSquattingFlag) => {
        setAction(nextAction);
        setSelectedFlag(flag);
        setDialogOpen(true);
    };

    const handleConfirm = async () => {
        if (!selectedFlag) {
            return;
        }
        const targets = [{ namespace: selectedFlag.namespace, extension: selectedFlag.extensionName }];
        if (action === 'clear') {
            await clearFlags(targets);
        } else {
            await deleteExtensions(targets);
        }
    };

    const handleDialogClose = () => {
        setDialogOpen(false);
        setSelectedFlag(undefined);
    };

    const countLabel = (state: NameSquattingState) => {
        if (!counts) {
            return '';
        }
        const value = { PUBLISHED: counts.published, DEACTIVATED: counts.deactivated, REJECTED: counts.rejected }[
            state
        ];
        return ` (${value})`;
    };

    return (
        <PageLayout>
            <Box>
                <Typography variant='h4' component='h1' gutterBottom>
                    Name Squatting
                </Typography>
                <Typography variant='body2' color='text.secondary'>
                    Extensions flagged by the name squatting publisher check. Clear the check on an extension whose
                    match is a false positive, or soft delete one that turns out to be squatting a name.
                    {counts ? ` ${counts.total} flagged in total.` : ''}
                </Typography>
            </Box>

            <FilterBar>
                <TextField
                    size='small'
                    label='Namespace'
                    value={namespaceInput}
                    onChange={event => setNamespaceInput(event.target.value)}
                />
                <TextField
                    size='small'
                    label='Extension'
                    value={nameInput}
                    onChange={event => setNameInput(event.target.value)}
                />
                <TextField
                    size='small'
                    label='Publisher'
                    value={publisherInput}
                    onChange={event => setPublisherInput(event.target.value)}
                />
                <ToggleButtonGroup
                    size='small'
                    value={states}
                    onChange={(_event, next: NameSquattingState[]) => {
                        setStates(next);
                        setPage(0);
                    }}
                    aria-label='Filter by extension state'>
                    {STATE_OPTIONS.map(option => (
                        <ToggleButton key={option.value} value={option.value}>
                            {option.label}
                            {countLabel(option.value)}
                        </ToggleButton>
                    ))}
                </ToggleButtonGroup>
            </FilterBar>

            {error && (
                <Alert severity='error' onClose={() => setErrorDismissed(true)}>
                    {error}
                </Alert>
            )}

            {loading && flags.length === 0 && (
                <CenteredProgress>
                    <CircularProgress color='secondary' />
                </CenteredProgress>
            )}

            {!loading && !error && flags.length === 0 && (
                <Paper elevation={0} sx={{ p: 3, textAlign: 'center' }}>
                    <Typography color='textSecondary'>No extensions are flagged for name squatting.</Typography>
                </Paper>
            )}

            {flags.length > 0 && (
                <>
                    <FlagList>
                        {flags.map(flag => (
                            <NameSquattingRow
                                key={`${flag.namespace}.${flag.extensionName}`}
                                flag={flag}
                                onClear={selected => openDialog('clear', selected)}
                                onDelete={selected => openDialog('delete', selected)}
                            />
                        ))}
                    </FlagList>
                    <TablePagination
                        component='div'
                        count={totalSize}
                        page={page}
                        onPageChange={(_event, nextPage) => setPage(nextPage)}
                        rowsPerPage={pageSize}
                        rowsPerPageOptions={PAGE_SIZE_OPTIONS}
                        onRowsPerPageChange={event => {
                            setPageSize(Number(event.target.value));
                            setPage(0);
                        }}
                    />
                </>
            )}

            <NameSquattingActionDialog
                open={dialogOpen}
                action={action}
                flag={selectedFlag}
                onClose={handleDialogClose}
                onConfirm={handleConfirm}
            />
        </PageLayout>
    );
};
