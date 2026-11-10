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
    Button,
    Chip,
    IconButton,
    Stack,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
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
import { Timestamp } from '../../../components/timestamp';
import { formatCompactNumber } from '../../../utils';
import { FIELD_LABELS, NAMESPACE_HINT_THRESHOLD, pluralize } from './name-squatting-model';
import { ColumnWidths, ExtensionIdentity, SimilarityChip, ValuePair } from './name-squatting-parts';
import { GroupCard, GroupHeader, GroupTable, HeaderEnd } from './name-squatting-styles';
import { extensionId } from './use-name-squatting';

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

const COLUMN_WIDTHS = ['22%', 'auto', '6.5rem', '11rem', '11.5rem'];

const LookalikeRow: FC<{
    lookalike: NameSquattingLookalike;
    onAllow: () => void;
    onDelete: () => void;
}> = ({ lookalike, onAllow, onDelete }) => {
    const { extension } = lookalike;
    return (
        <TableRow>
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
                <Stack direction='row' spacing={1} justifyContent='flex-end'>
                    <Button size='small' variant='outlined' onClick={onAllow}>
                        Allow
                    </Button>
                    <Button
                        size='small'
                        variant='outlined'
                        color='error'
                        onClick={onDelete}
                        aria-label={`Delete ${extensionId(extension)}`}>
                        Delete…
                    </Button>
                </Stack>
            </TableCell>
        </TableRow>
    );
};

export interface CollisionGroupProps {
    group: NameSquattingGroup;
    onAllow: (anchor: NameSquattingExtension, lookalikes: NameSquattingExtension[]) => void;
    /** `laterCount` is how many extensions of the group were published after the one to delete. */
    onDelete: (extension: NameSquattingExtension, laterCount: number) => void;
}

/**
 * The extension published first and those published later that resemble it, a row each. Published
 * first is not proof of being the original, so either side can be deleted.
 */
export const CollisionGroup: FC<CollisionGroupProps> = ({ group, onAllow, onDelete }) => {
    const [open, setOpen] = useState(true);
    const { anchor, lookalikes } = group;
    return (
        <GroupCard variant='outlined'>
            <GroupHeader>
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
                    {lookalikes.length > 1 && (
                        <Button
                            size='small'
                            onClick={() =>
                                onAllow(
                                    anchor,
                                    lookalikes.map(lookalike => lookalike.extension)
                                )
                            }>
                            Allow all…
                        </Button>
                    )}
                    <Button
                        size='small'
                        color='error'
                        onClick={() => onDelete(anchor, lookalikes.length)}
                        aria-label={`Delete ${extensionId(anchor)}`}>
                        Delete…
                    </Button>
                </HeaderEnd>
            </GroupHeader>
            {open && (
                <TableContainer>
                    <GroupTable size='small'>
                        <ColumnWidths widths={COLUMN_WIDTHS} />
                        <TableHead>
                            <TableRow>
                                <TableCell>Published later</TableCell>
                                <TableCell>Matched on (A = published first, B = published later)</TableCell>
                                <TableCell align='right'>Downloads</TableCell>
                                <TableCell>Published</TableCell>
                                <TableCell align='right'>Resolve</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {lookalikes.map(lookalike => (
                                <LookalikeRow
                                    key={extensionId(lookalike.extension)}
                                    lookalike={lookalike}
                                    onAllow={() => onAllow(anchor, [lookalike.extension])}
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
