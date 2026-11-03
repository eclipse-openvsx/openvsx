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

import { FunctionComponent } from 'react';
import { Button, Dialog, dialogClasses, DialogContentText, DialogTitle, Link } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import LockClockRoundedIcon from '@mui/icons-material/LockClockRounded';
import LoginRoundedIcon from '@mui/icons-material/LoginRounded';
import { LoginComponent } from '../default/login';

// Frosted glass like the navbar.
const FrostedDialog = styled(Dialog)(({ theme }) => ({
    [`& .${dialogClasses.paper}`]: {
        backgroundColor: alpha(theme.palette.background.paper, 0.88),
        backdropFilter: 'blur(12px) saturate(1.3)',
        WebkitBackdropFilter: 'blur(12px) saturate(1.3)'
    }
}));

const Body = styled('div')({
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: '2.5rem 2rem 2rem',
    textAlign: 'center'
});

const IconBadge = styled('div')(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '3.5rem',
    height: '3.5rem',
    borderRadius: theme.shape.borderRadiusCard,
    backgroundColor: theme.palette.accentSoft,
    color: theme.palette.secondary.light,
    '& svg': { fontSize: '1.75rem' }
}));

const Title = styled(DialogTitle)({
    marginTop: '1.25rem',
    padding: 0,
    fontSize: '1.375rem',
    fontWeight: 700,
    letterSpacing: '-0.01em',
    lineHeight: 1.3
});

const Message = styled(DialogContentText)({
    margin: '0.5rem 0 1.75rem',
    fontSize: '0.9375rem',
    lineHeight: 1.55
});

const Actions = styled('div')({
    display: 'flex',
    alignItems: 'center',
    gap: '1.25rem'
});

/** Asks a user whose session ended to log in again, returning them to the page they were on. */
export const SessionExpiredDialog: FunctionComponent<SessionExpiredDialogProps> = props => (
    <FrostedDialog open={props.open} onClose={props.onClose} maxWidth='xs' fullWidth>
        <Body>
            <IconBadge>
                <LockClockRoundedIcon />
            </IconBadge>
            <Title>Your session has expired</Title>
            <Message>Log in again to continue where you left off.</Message>
            <Actions>
                {props.loginProviders ? (
                    <LoginComponent
                        loginProviders={props.loginProviders}
                        renderButton={(href, onClick) => (
                            <Button
                                variant='contained'
                                color='secondary'
                                size='large'
                                startIcon={<LoginRoundedIcon />}
                                href={href}
                                onClick={onClick}>
                                Log in
                            </Button>
                        )}
                    />
                ) : null}
                <Link
                    component='button'
                    variant='body2'
                    color='text.secondary'
                    underline='hover'
                    onClick={props.onClose}>
                    Not now
                </Link>
            </Actions>
        </Body>
    </FrostedDialog>
);

export interface SessionExpiredDialogProps {
    open: boolean;
    loginProviders?: Record<string, string>;
    onClose: () => void;
}
