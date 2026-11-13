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
import { Box, Button, CircularProgress, Paper, Skeleton, Typography } from '@mui/material';
import type { NameSquattingExtension, NameSquattingField } from '../../../extension-registry-types';
import { AllowDialog, CollisionPair } from './allow-dialog';
import { CollisionGroup } from './collision-group';
import { DeleteExtensionDialog } from './delete-extension-dialog';
import { FilterCheckbox, RefreshControl, SearchField, SelectionBar } from './name-squatting-controls';
import { FIELD_LABELS, filterCollisionGroups, pluralize } from './name-squatting-model';
import { LoadError } from './name-squatting-parts';
import { Toolbar } from './name-squatting-styles';
import { collisionPairKey, extensionId, useNameSquattingCollisions } from './use-name-squatting';

const ALL_FIELDS: NameSquattingField[] = ['NAME', 'DISPLAY_NAME'];

export const CollisionsTab: FC = () => {
    const [fields, setFields] = useState<NameSquattingField[]>(ALL_FIELDS);
    const [exactOnly, setExactOnly] = useState(false);
    const [query, setQuery] = useState('');
    const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
    const [allowTarget, setAllowTarget] = useState<CollisionPair[]>();
    const [deleteTarget, setDeleteTarget] = useState<{
        extension: NameSquattingExtension;
        laterCount: number;
    }>();

    const {
        data: groups,
        dataUpdatedAt,
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
    // Only what the filters leave visible can be acted on, whatever was selected before they changed.
    const visiblePairs = useMemo(
        () =>
            visible.flatMap(group =>
                group.lookalikes.map(lookalike => ({
                    key: collisionPairKey(group.anchor, lookalike.extension),
                    anchor: group.anchor,
                    lookalike: lookalike.extension
                }))
            ),
        [visible]
    );
    const selectedPairs = visiblePairs.filter(pair => selected.has(pair.key));
    const lookalikeCount = visiblePairs.length;
    const exactCount = visible.reduce(
        (count, group) =>
            count + group.lookalikes.filter(lookalike => lookalike.matches.some(match => match.exact)).length,
        0
    );

    const toggleField = (field: NameSquattingField, checked: boolean) =>
        setFields(ALL_FIELDS.filter(candidate => (candidate === field ? checked : fields.includes(candidate))));

    const select = (keys: string[], checked: boolean) =>
        setSelected(previous => {
            const next = new Set(previous);
            keys.forEach(key => (checked ? next.add(key) : next.delete(key)));
            return next;
        });

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
                selected={selected}
                onSelect={select}
                onAllow={setAllowTarget}
                onDelete={(extension, laterCount) => setDeleteTarget({ extension, laterCount })}
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
                    label='Search collisions'
                />
                <Typography variant='body2' color='text.secondary'>
                    Match on
                </Typography>
                {ALL_FIELDS.map(field => (
                    <FilterCheckbox
                        key={field}
                        label={FIELD_LABELS[field]}
                        checked={fields.includes(field)}
                        // the last field stays checked, or nothing could ever be shown
                        disabled={fields.length === 1 && fields.includes(field)}
                        onChange={checked => toggleField(field, checked)}
                    />
                ))}
                <FilterCheckbox label='Exact matches only' checked={exactOnly} onChange={setExactOnly} />
                <RefreshControl
                    onClick={() => refetch()}
                    refreshing={isFetching && !isFetchingNextPage}
                    updatedAt={dataUpdatedAt}
                />
            </Toolbar>
            {error && !isFetchNextPageError && <LoadError error={error} onRetry={() => refetch()} />}
            {!isPending && visible.length > 0 && (
                <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                    {pluralize(visible.length, 'group')} · {pluralize(lookalikeCount, 'later extension')} · {exactCount}{' '}
                    with an exact match
                    {hasNextPage && ' so far'}
                </Typography>
            )}
            {!isPending && visible.length > 0 && (
                <SelectionBar
                    selected={selectedPairs.length}
                    total={visiblePairs.length}
                    onSelectAll={checked => setSelected(new Set(checked ? visiblePairs.map(pair => pair.key) : []))}
                    onAction={() => setAllowTarget(selectedPairs)}
                />
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
                    pairs={allowTarget}
                    onAllowed={() => setSelected(new Set())}
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
