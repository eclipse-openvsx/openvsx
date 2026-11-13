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
    Chip,
    CircularProgress,
    IconButton,
    Paper,
    Skeleton,
    Stack,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { useSearchParams } from 'react-router';
import type {
    NameSquattingAllow,
    NameSquattingAllowlistGroup,
    NameSquattingExtension
} from '../../../extension-registry-types';
import { Timestamp } from '../../../components/timestamp';
import { handleError } from '../../../utils';
import { FilterCheckbox, SearchField } from './name-squatting-controls';
import { FIELD_LABELS, filterAllowlistGroups, pluralize } from './name-squatting-model';
import { ColumnWidths, ExtensionIdentity, SimilarityChip, ValuePair } from './name-squatting-parts';
import { GroupCard, GroupHeader, GroupTable, HeaderEnd, segmentedToggle, Toolbar } from './name-squatting-styles';
import { NamespaceAllowlist } from './namespace-allowlist';
import { RemoveAllowsDialog } from './remove-allows-dialog';
import { extensionId, useNameSquattingAllowlist } from './use-name-squatting';

const COLUMN_WIDTHS = ['22%', 'auto', '12rem', '24%', '7rem'];

const AllowRow: FC<{ allow: NameSquattingAllow; onRemove: () => void }> = ({ allow, onRemove }) => (
    <TableRow>
        <TableCell>
            <ExtensionIdentity extension={allow.other} />
        </TableCell>
        <TableCell>
            <Stack direction='row' spacing={1} useFlexGap flexWrap='wrap' alignItems='center' sx={{ mb: 0.5 }}>
                <SimilarityChip similarity={allow.similarity} exact={false} />
                <Typography variant='caption' color='text.secondary'>
                    {FIELD_LABELS[allow.field]}
                </Typography>
                {allow.renamed && <Chip size='small' color='warning' label='Renamed since allowed' />}
            </Stack>
            <ValuePair first={allow.otherValue} second={allow.value} />
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

const AllowlistGroup: FC<{
    group: NameSquattingAllowlistGroup;
    onRemove: (extension: NameSquattingExtension, allows: NameSquattingAllow[]) => void;
}> = ({ group, onRemove }) => {
    const [open, setOpen] = useState(false);
    const { extension, allows } = group;
    const renamed = allows.some(allow => allow.renamed);
    const lastDecidedAt = allows.reduce((last, allow) => (allow.decidedAt > last ? allow.decidedAt : last), '');
    return (
        <GroupCard variant='outlined'>
            <GroupHeader>
                <IconButton
                    size='small'
                    onClick={() => setOpen(!open)}
                    aria-expanded={open}
                    aria-label={`${open ? 'Hide' : 'Show'} the allowed pairs of ${extensionId(extension)}`}>
                    {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                </IconButton>
                <ExtensionIdentity extension={extension} />
                {renamed && <Chip size='small' color='warning' label='Renamed since allowed' />}
                <HeaderEnd>
                    <Chip size='small' label={`Allowed alongside ${pluralize(allows.length, 'extension')}`} />
                    <Typography variant='caption' color='text.secondary'>
                        last <Timestamp value={lastDecidedAt} />
                    </Typography>
                    <Button size='small' color='error' onClick={() => onRemove(extension, allows)}>
                        {allows.length === 1 ? 'Remove' : 'Remove all'}
                    </Button>
                </HeaderEnd>
            </GroupHeader>
            {open && (
                <TableContainer>
                    <GroupTable size='small'>
                        <ColumnWidths widths={COLUMN_WIDTHS} />
                        <TableHead>
                            <TableRow>
                                <TableCell>Allowed alongside</TableCell>
                                <TableCell>Match when allowed (A = other, B = this extension)</TableCell>
                                <TableCell>Allowed by</TableCell>
                                <TableCell>Reason</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {allows.map(allow => (
                                <AllowRow key={allow.id} allow={allow} onRemove={() => onRemove(extension, [allow])} />
                            ))}
                        </TableBody>
                    </GroupTable>
                </TableContainer>
            )}
        </GroupCard>
    );
};

const ExtensionAllowlist: FC = () => {
    const [query, setQuery] = useState('');
    const [renamedOnly, setRenamedOnly] = useState(false);
    const [removeTarget, setRemoveTarget] = useState<{
        extension: NameSquattingExtension;
        allows: NameSquattingAllow[];
    }>();

    const {
        data: groups,
        error,
        isPending,
        isFetchNextPageError,
        hasNextPage,
        isFetchingNextPage,
        fetchNextPage,
        refetch
    } = useNameSquattingAllowlist();

    const visible = useMemo(
        () => filterAllowlistGroups(groups ?? [], { query, renamedOnly }),
        [groups, query, renamedOnly]
    );
    const pairCount = visible.reduce((count, group) => count + group.allows.length, 0);

    const renderGroups = () => {
        if (isPending) {
            return error ? null : (
                <Box aria-busy='true' aria-label='Loading the allowlist'>
                    {[0, 1].map(index => (
                        <Skeleton key={index} variant='rounded' height='4rem' sx={{ mb: 2 }} />
                    ))}
                </Box>
            );
        }
        if (visible.length === 0) {
            const filtered = (groups?.length ?? 0) > 0;
            return (
                <Paper variant='outlined' sx={{ p: 3 }}>
                    <Typography variant='h6' component='h3'>
                        {filtered ? 'No allowed pairs match these filters' : 'No allowed pairs yet'}
                    </Typography>
                    <Typography color='text.secondary'>
                        When you allow a pair on the Collisions tab, the pair is listed here so you can remove it later.
                    </Typography>
                </Paper>
            );
        }
        return visible.map(group => (
            <AllowlistGroup
                key={extensionId(group.extension)}
                group={group}
                onRemove={(extension, allows) => setRemoveTarget({ extension, allows })}
            />
        ));
    };

    return (
        <>
            <Toolbar>
                <SearchField
                    value={query}
                    onChange={setQuery}
                    placeholder='Search namespace, name or display name'
                    label='Search allowlist'
                />
                <FilterCheckbox label='Names changed since allowed' checked={renamedOnly} onChange={setRenamedOnly} />
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
                    {pluralize(visible.length, 'extension')} · {pluralize(pairCount, 'allowed pair')}
                    {hasNextPage && ' so far'}
                </Typography>
            )}
            {renderGroups()}
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
                <RemoveAllowsDialog
                    extension={removeTarget.extension}
                    allows={removeTarget.allows}
                    onClose={() => setRemoveTarget(undefined)}
                />
            )}
        </>
    );
};

type AllowlistKind = 'extensions' | 'namespaces';

/** Allowed pairs of extensions or of namespaces; the kind is kept in the URL, extensions leaving it bare. */
export const AllowlistTab: FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const kind: AllowlistKind = searchParams.get('kind') === 'namespaces' ? 'namespaces' : 'extensions';

    return (
        <>
            <ToggleButtonGroup
                size='small'
                exclusive
                value={kind}
                aria-label='Allowed pairs of'
                onChange={(_event, next: AllowlistKind | null) =>
                    next &&
                    setSearchParams(
                        previous => {
                            const params = new URLSearchParams(previous);
                            if (next === 'namespaces') {
                                params.set('kind', next);
                            } else {
                                params.delete('kind');
                            }
                            return params;
                        },
                        { replace: true }
                    )
                }
                sx={theme => ({ ...segmentedToggle(theme), mb: 2 })}>
                <ToggleButton value='extensions'>Extensions</ToggleButton>
                <ToggleButton value='namespaces'>Namespaces</ToggleButton>
            </ToggleButtonGroup>
            {kind === 'extensions' ? <ExtensionAllowlist /> : <NamespaceAllowlist />}
        </>
    );
};
