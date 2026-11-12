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
    Box,
    Button,
    CircularProgress,
    FormControlLabel,
    Paper,
    Skeleton,
    Switch,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from '@mui/material';
import type { NameSquattingExtension, NameSquattingField } from '../../../extension-registry-types';
import { AllowDialog } from './allow-dialog';
import { CollisionGroup } from './collision-group';
import { DeleteExtensionDialog } from './delete-extension-dialog';
import { FIELD_LABELS, filterCollisionGroups, pluralize } from './name-squatting-model';
import { LoadError } from './name-squatting-parts';
import { Toolbar } from './name-squatting-styles';
import { extensionId, useNameSquattingCollisions } from './use-name-squatting';

const ALL_FIELDS: NameSquattingField[] = ['NAME', 'DISPLAY_NAME'];

export const CollisionsTab: FC = () => {
    const [fields, setFields] = useState<NameSquattingField[]>(ALL_FIELDS);
    const [exactOnly, setExactOnly] = useState(false);
    const [query, setQuery] = useState('');
    const [allowTarget, setAllowTarget] = useState<{
        anchor: NameSquattingExtension;
        lookalikes: NameSquattingExtension[];
    }>();
    const [deleteTarget, setDeleteTarget] = useState<{
        extension: NameSquattingExtension;
        laterCount: number;
    }>();

    const {
        data: groups,
        error,
        isPending,
        isFetching,
        isFetchNextPageError,
        hasNextPage,
        isFetchingNextPage,
        fetchNextPage,
        refetch
    } = useNameSquattingCollisions();

    const visible = useMemo(
        () => filterCollisionGroups(groups ?? [], { fields: new Set(fields), exactOnly, query }),
        [groups, fields, exactOnly, query]
    );
    const lookalikeCount = visible.reduce((count, group) => count + group.lookalikes.length, 0);
    const exactCount = visible.reduce(
        (count, group) =>
            count + group.lookalikes.filter(lookalike => lookalike.matches.some(match => match.exact)).length,
        0
    );

    const renderGroups = () => {
        if (isPending) {
            return error ? null : (
                <Box aria-busy='true' aria-label='Checking extensions'>
                    {[0, 1, 2].map(index => (
                        <Skeleton key={index} variant='rounded' height='6rem' sx={{ mb: 2 }} />
                    ))}
                </Box>
            );
        }
        if (visible.length === 0) {
            const filtered = (groups?.length ?? 0) > 0;
            return (
                <Paper variant='outlined' sx={{ p: 3 }}>
                    <Typography variant='h6' component='h3'>
                        {filtered ? 'No similar extensions match these filters' : 'No similar names found'}
                    </Typography>
                    <Typography color='text.secondary'>
                        Pairs you have allowed are not shown here; you can review them on the Allowlist tab.
                    </Typography>
                </Paper>
            );
        }
        return visible.map(group => (
            <CollisionGroup
                key={extensionId(group.anchor)}
                group={group}
                onAllow={(anchor, lookalikes) => setAllowTarget({ anchor, lookalikes })}
                onDelete={(extension, laterCount) => setDeleteTarget({ extension, laterCount })}
            />
        ));
    };

    return (
        <>
            <Toolbar>
                <Typography variant='body2' color='text.secondary'>
                    Match on
                </Typography>
                <ToggleButtonGroup
                    size='small'
                    value={fields}
                    aria-label='Match on'
                    // at least one field stays selected, or nothing could ever be shown
                    onChange={(_event, next: NameSquattingField[]) => next.length > 0 && setFields(next)}>
                    {ALL_FIELDS.map(field => (
                        <ToggleButton key={field} value={field}>
                            {FIELD_LABELS[field]}
                        </ToggleButton>
                    ))}
                </ToggleButtonGroup>
                <FormControlLabel
                    control={<Switch checked={exactOnly} onChange={event => setExactOnly(event.target.checked)} />}
                    label='Exact matches only'
                />
                <TextField
                    size='small'
                    type='search'
                    placeholder='Filter by extension or namespace'
                    inputProps={{ 'aria-label': 'Filter groups' }}
                    value={query}
                    onChange={event => setQuery(event.target.value)}
                    sx={{ ml: 'auto', minWidth: '16rem' }}
                />
                <Button
                    variant='outlined'
                    onClick={() => refetch()}
                    disabled={isFetching}
                    startIcon={isFetching && !isFetchingNextPage ? <CircularProgress size={16} /> : undefined}>
                    Check again
                </Button>
            </Toolbar>
            {error && !isFetchNextPageError && <LoadError error={error} onRetry={() => refetch()} />}
            {!isPending && visible.length > 0 && (
                <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                    {pluralize(visible.length, 'group')} · {pluralize(lookalikeCount, 'later extension')} · {exactCount}{' '}
                    with an exact match
                    {hasNextPage && ' so far'}
                </Typography>
            )}
            {renderGroups()}
            {error && isFetchNextPageError && <LoadError error={error} onRetry={() => fetchNextPage()} />}
            {hasNextPage && !isFetchNextPageError && (
                <Button
                    fullWidth
                    variant='outlined'
                    onClick={() => fetchNextPage()}
                    disabled={isFetchingNextPage}
                    startIcon={isFetchingNextPage ? <CircularProgress size={16} /> : undefined}>
                    Load more groups
                </Button>
            )}
            {allowTarget && (
                <AllowDialog
                    anchor={allowTarget.anchor}
                    lookalikes={allowTarget.lookalikes}
                    onClose={() => setAllowTarget(undefined)}
                />
            )}
            {deleteTarget && (
                <DeleteExtensionDialog
                    extension={deleteTarget.extension}
                    laterCount={deleteTarget.laterCount}
                    onClose={() => setDeleteTarget(undefined)}
                />
            )}
        </>
    );
};
