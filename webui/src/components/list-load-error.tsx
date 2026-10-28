/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode } from 'react';
import { Button, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';

const Root = styled('div')(({ theme }) => ({
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: '0.5rem 0.75rem',
    color: theme.palette.warningAccent
}));

const Message = styled(Typography)({
    display: 'flex',
    alignItems: 'center',
    gap: '0.375rem'
}) as typeof Typography;

// More compact than MUI's `small`, to sit level with a line of body text.
const RetryButton = styled(Button)({
    minWidth: 0,
    padding: '0.0625rem 0.5rem',
    fontSize: '0.75rem',
    lineHeight: 1.5,
    '& .MuiButton-startIcon': {
        marginLeft: '-0.125rem',
        marginRight: '0.25rem',
        '& > *:nth-of-type(1)': { fontSize: '0.875rem' }
    }
});

/** An inline warning that a list's items failed to load, with a button to load them again. */
export const ListLoadError: FunctionComponent<ListLoadErrorProps> = ({ message, onRetry, className }) => (
    <Root className={className}>
        <Message variant='body2'>
            <WarningAmberRoundedIcon fontSize='small' />
            {message}
        </Message>
        <RetryButton variant='outlined' size='small' startIcon={<RefreshRoundedIcon />} onClick={onRetry}>
            Retry
        </RetryButton>
    </Root>
);

export interface ListLoadErrorProps {
    message: ReactNode;
    onRetry: () => void;
    /** Carries no spacing of its own: lay it out where it is used, e.g. with `styled(ListLoadError)`. */
    className?: string;
}
