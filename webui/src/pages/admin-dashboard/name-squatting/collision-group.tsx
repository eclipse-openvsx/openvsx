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

import { FC, useState } from 'react';
import {
    Box,
    Chip,
    IconButton,
    Stack,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography
} from '@mui/material';
import { styled } from '@mui/material/styles';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import type {
    NameSquattingExtension,
    NameSquattingGroup,
    NameSquattingLookalike
} from '../../../extension-registry-types';
import { DeleteIconButton } from '../../../components/delete-icon-button';
import { Timestamp } from '../../../components/timestamp';
import { formatCompactNumber } from '../../../utils';
import { FIELD_LABELS, NAMESPACE_HINT_THRESHOLD, pluralize } from './name-squatting-model';
import type { CollisionPair } from './allow-dialog';
import { AllowIconButton, SelectCell, SelectCheckbox } from './name-squatting-controls';
import { ColumnWidths, ExtensionIdentity, SimilarityChip, ValuePair } from './name-squatting-parts';
import { GroupCard, GroupHeader, GroupTable, HeaderEnd } from './name-squatting-styles';
import { collisionPairKey, extensionId } from './use-name-squatting';

const Facts = styled(Box)(({ theme }) => ({
    display: 'flex',
    flexWrap: 'wrap',
    gap: theme.spacing(0.5, 2),
    color: theme.palette.text.secondary,
    fontSize: '0.875rem'
}));

const MatchRow = styled(Box)(({ theme }) => ({
    display: 'grid',
    gridTemplateColumns: '7.5rem 1fr',
    gap: theme.spacing(1),
    alignItems: 'start',
    '& + &': { marginTop: theme.spacing(1) }
}));

const Hint = styled(Typography)(({ theme }) => ({
    display: 'block',
    color: theme.palette.text.secondary,
    fontSize: '0.75rem'
})) as typeof Typography;

const COLUMN_WIDTHS = ['3.25rem', '22%', 'auto', '6.5rem', '11rem', '6.5rem'];

/** Opens the delete dialog; the dialog's typed confirmation guards the delete itself. */
const DeleteButton: FC<{ extension: NameSquattingExtension; onClick: () => void; tooltip?: string }> = ({
    extension,
    onClick,
    tooltip = 'Delete extension'
}) => (
    <Tooltip title={tooltip}>
        <DeleteIconButton onClick={onClick} aria-label={`Delete ${extensionId(extension)}`} />
    </Tooltip>
);

const LookalikeRow: FC<{
    lookalike: NameSquattingLookalike;
    selected: boolean;
    onSelect: (selected: boolean) => void;
    onAllow: () => void;
    onDelete: () => void;
}> = ({ lookalike, selected, onSelect, onAllow, onDelete }) => {
    const { extension } = lookalike;
    return (
        <TableRow selected={selected}>
            <SelectCell checked={selected} onChange={onSelect} label={`Select ${extensionId(extension)}`} />
            <TableCell>
                <ExtensionIdentity extension={extension} />
            </TableCell>
            <TableCell>
                {lookalike.matches.map(match => (
                    <MatchRow key={match.field}>
                        <Stack spacing={0.5} alignItems='flex-start'>
                            <SimilarityChip similarity={match.similarity} exact={match.exact} />
                            <Hint component='span'>{FIELD_LABELS[match.field]}</Hint>
                        </Stack>
                        <ValuePair first={match.value} second={match.lookalikeValue} />
                    </MatchRow>
                ))}
                {lookalike.namespaceSimilarity >= NAMESPACE_HINT_THRESHOLD && (
                    <Hint component='span' sx={{ mt: 1 }}>
                        Namespaces are {Math.round(lookalike.namespaceSimilarity * 100)}% similar too
                    </Hint>
                )}
            </TableCell>
            <TableCell align='right'>{formatCompactNumber(extension.downloadCount)}</TableCell>
            <TableCell>
                {extension.publishedDate && <Timestamp value={extension.publishedDate} />}
                <Hint component='span'>{pluralize(extension.activeVersionCount, 'version')}</Hint>
                <Hint component='span'>
                    {lookalike.flaggedAt ? (
                        <>
                            flagged <Timestamp value={lookalike.flaggedAt} />
                        </>
                    ) : (
                        'shares the display name, not flagged'
                    )}
                </Hint>
            </TableCell>
            <TableCell align='right'>
                <Stack direction='row' spacing={1} justifyContent='flex-end' alignItems='center'>
                    <AllowIconButton onClick={onAllow} aria-label={`Allow ${extensionId(extension)}`} />
                    <DeleteButton extension={extension} onClick={onDelete} />
                </Stack>
            </TableCell>
        </TableRow>
    );
};

export interface CollisionGroupProps {
    group: NameSquattingGroup;
    /** Keys of the selected pairs, see `collisionPairKey`. */
    selected: ReadonlySet<string>;
    onSelect: (keys: string[], selected: boolean) => void;
    onAllow: (pairs: CollisionPair[]) => void;
    /** `laterCount` is how many extensions of the group were published after the one to delete. */
    onDelete: (extension: NameSquattingExtension, laterCount: number) => void;
}

/**
 * The extension published first and those published later that resemble it, a row each. Published
 * first is not proof of being the original, so either side can be deleted.
 */
export const CollisionGroup: FC<CollisionGroupProps> = ({ group, selected, onSelect, onAllow, onDelete }) => {
    const [open, setOpen] = useState(true);
    const { anchor, lookalikes } = group;
    const keys = lookalikes.map(lookalike => collisionPairKey(anchor, lookalike.extension));
    const selectedCount = keys.filter(key => selected.has(key)).length;
    return (
        <GroupCard variant='outlined'>
            <GroupHeader>
                <SelectCheckbox
                    checked={selectedCount === keys.length}
                    indeterminate={selectedCount > 0 && selectedCount < keys.length}
                    onChange={event => onSelect(keys, event.target.checked)}
                    inputProps={{ 'aria-label': `Select every extension published after ${extensionId(anchor)}` }}
                    // the header's 16px padding less 8px: the same inset as the row checkboxes below
                    sx={{ ml: -1 }}
                />
                <IconButton
                    size='small'
                    onClick={() => setOpen(!open)}
                    aria-expanded={open}
                    aria-label={`${open ? 'Collapse' : 'Expand'} ${extensionId(anchor)}`}>
                    {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                </IconButton>
                <Chip size='small' label='Published first' variant='outlined' />
                <ExtensionIdentity extension={anchor} />
                <Facts>
                    <span>{formatCompactNumber(anchor.downloadCount)} downloads</span>
                    {anchor.publishedDate && (
                        <span>
                            since <Timestamp value={anchor.publishedDate} />
                        </span>
                    )}
                </Facts>
                <HeaderEnd>
                    <Chip size='small' label={`${lookalikes.length} published later`} />
                    <AllowIconButton
                        tooltip='Allow every pair in this group'
                        aria-label={`Allow every extension published after ${extensionId(anchor)}`}
                        onClick={() =>
                            onAllow(lookalikes.map(lookalike => ({ anchor, lookalike: lookalike.extension })))
                        }
                    />
                    <DeleteButton
                        extension={anchor}
                        tooltip='Delete this extension only'
                        onClick={() => onDelete(anchor, lookalikes.length)}
                    />
                </HeaderEnd>
            </GroupHeader>
            {open && (
                <TableContainer>
                    <GroupTable size='small'>
                        <ColumnWidths widths={COLUMN_WIDTHS} />
                        <TableHead>
                            <TableRow>
                                <TableCell />
                                <TableCell>Published later</TableCell>
                                <TableCell>Matched on (A = published first, B = published later)</TableCell>
                                <TableCell align='right'>Downloads</TableCell>
                                <TableCell>Published</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {lookalikes.map((lookalike, index) => (
                                <LookalikeRow
                                    key={keys[index]}
                                    lookalike={lookalike}
                                    selected={selected.has(keys[index])}
                                    onSelect={checked => onSelect([keys[index]], checked)}
                                    onAllow={() => onAllow([{ anchor, lookalike: lookalike.extension }])}
                                    onDelete={() => onDelete(lookalike.extension, 0)}
                                />
                            ))}
                        </TableBody>
                    </GroupTable>
                </TableContainer>
            )}
        </GroupCard>
    );
};
