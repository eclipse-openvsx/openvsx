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
    Collapse,
    Divider,
    IconButton,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Tooltip,
    Typography
} from '@mui/material';
import { styled } from '@mui/material/styles';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import type { NameSquattingFlag, NameSquattingState } from '../../../extension-registry-types';

const RowPaper = styled(Box)(({ theme }) => ({
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
    padding: theme.spacing(2),
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1)
}));

const ExpandButton = styled(IconButton, { shouldForwardProp: prop => prop !== 'expanded' })<{ expanded: boolean }>(
    ({ theme, expanded }) => ({
        transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
        transition: theme.transitions.create('transform', { duration: theme.transitions.duration.shortest })
    })
);

const HeaderRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'flex-start',
    gap: theme.spacing(2),
    flexWrap: 'wrap'
}));

const InlineGroup = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    flexWrap: 'wrap'
}));

const stateLabels: Record<NameSquattingState, { label: string; color: 'success' | 'default' | 'warning' }> = {
    PUBLISHED: { label: 'Published', color: 'success' },
    DEACTIVATED: { label: 'Deactivated', color: 'default' },
    REJECTED: { label: 'Publication blocked', color: 'warning' }
};

const formatDate = (value: string) => new Date(value).toLocaleString();

export interface NameSquattingRowProps {
    flag: NameSquattingFlag;
    onClear: (flag: NameSquattingFlag) => void;
    onDelete: (flag: NameSquattingFlag) => void;
}

export const NameSquattingRow: FC<NameSquattingRowProps> = ({ flag, onClear, onDelete }) => {
    const [expanded, setExpanded] = useState(false);

    const extensionId = `${flag.namespace}.${flag.extensionName}`;
    const state = stateLabels[flag.state];
    // Publication was blocked, so there is no extension to clear findings on or to deactivate.
    const rejected = flag.state === 'REJECTED';

    return (
        <RowPaper>
            <HeaderRow>
                <Box sx={{ flex: 1, minWidth: '15rem' }}>
                    <Typography variant='subtitle1'>{flag.displayName}</Typography>
                    <Typography variant='body2' color='text.secondary'>
                        {extensionId} &middot; published by {flag.publisher}
                    </Typography>
                </Box>
                <InlineGroup>
                    <Chip size='small' color={state.color} label={state.label} />
                    <Chip
                        size='small'
                        variant='outlined'
                        label={`${flag.findingCount} ${flag.findingCount === 1 ? 'finding' : 'findings'}`}
                    />
                    <Typography variant='caption' color='text.secondary'>
                        Last flagged {formatDate(flag.dateLastDetected)}
                    </Typography>
                    <ExpandButton
                        expanded={expanded}
                        onClick={() => setExpanded(prev => !prev)}
                        aria-label={expanded ? `Hide findings for ${extensionId}` : `Show findings for ${extensionId}`}
                        aria-expanded={expanded}
                        size='small'>
                        <ExpandMoreIcon />
                    </ExpandButton>
                </InlineGroup>
            </HeaderRow>

            <InlineGroup>
                {rejected ? (
                    <Typography variant='body2' color='text.secondary'>
                        Publication was blocked by the check, so there is no extension to moderate.
                    </Typography>
                ) : (
                    <>
                        <Button size='small' variant='outlined' onClick={() => onClear(flag)}>
                            Mark as false positive
                        </Button>
                        <Tooltip
                            title={
                                flag.activeVersionCount === 0
                                    ? 'The extension has no active versions left to deactivate'
                                    : ''
                            }>
                            <span>
                                <Button
                                    size='small'
                                    variant='outlined'
                                    color='error'
                                    disabled={flag.activeVersionCount === 0}
                                    onClick={() => onDelete(flag)}>
                                    Soft delete extension
                                </Button>
                            </span>
                        </Tooltip>
                        <Typography variant='caption' color='text.secondary'>
                            {flag.activeVersionCount} active {flag.activeVersionCount === 1 ? 'version' : 'versions'}
                        </Typography>
                    </>
                )}
            </InlineGroup>

            <Collapse in={expanded} unmountOnExit>
                <Divider sx={{ mb: 1 }} />
                <Table size='small'>
                    <TableHead>
                        <TableRow>
                            <TableCell>Version</TableCell>
                            <TableCell>Scan</TableCell>
                            <TableCell>Rule</TableCell>
                            <TableCell>Reason</TableCell>
                            <TableCell>Detected</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {flag.findings.map(finding => (
                            <TableRow key={finding.id}>
                                <TableCell>
                                    {finding.version}
                                    {finding.targetPlatform && finding.targetPlatform !== 'universal'
                                        ? ` (${finding.targetPlatform})`
                                        : ''}
                                </TableCell>
                                <TableCell>
                                    {finding.scanStatus}
                                    {finding.enforcedFlag ? ' (enforced)' : ''}
                                </TableCell>
                                <TableCell>{finding.ruleName}</TableCell>
                                <TableCell>{finding.reason}</TableCell>
                                <TableCell>{formatDate(finding.dateDetected)}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </Collapse>
        </RowPaper>
    );
};
