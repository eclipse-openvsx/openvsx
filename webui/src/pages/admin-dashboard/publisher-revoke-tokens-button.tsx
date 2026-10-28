/********************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useState } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { PublisherInfo } from '../../extension-registry-types';
import { UpdateContext } from './publisher-admin';
import { useRevokeAccessTokens } from './use-publisher-admin';

export const PublisherRevokeTokensButton: FunctionComponent<PublisherRevokeTokensButtonProps> = props => {
    const updateContext = useContext(UpdateContext);
    const { mutate: revokeTokens, isPending: working } = useRevokeAccessTokens();

    const [dialogOpen, setDialogOpen] = useState(false);

    const { user } = props.publisherInfo;
    const tokenCount = props.publisherInfo.activeAccessTokenNum;

    const doRevoke = () => {
        revokeTokens(
            { provider: user.provider as string, login: user.loginName },
            {
                onSuccess: () => {
                    updateContext.handleUpdate();
                    setDialogOpen(false);
                }
            }
        );
    };

    return (
        <>
            <Button variant='outlined' color='error' onClick={() => setDialogOpen(true)}>
                Revoke access tokens{' '}
                <Box component='span' sx={{ ml: 0.75, opacity: 0.6 }}>
                    {tokenCount}
                </Box>
            </Button>
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>Revoke access tokens</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Deactivate {tokenCount} active access token{tokenCount === 1 ? '' : 's'} of {user.loginName}?
                        This cannot be undone.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant='contained' color='primary' onClick={() => setDialogOpen(false)}>
                        Cancel
                    </Button>
                    <ButtonWithProgress autoFocus sx={{ ml: 1 }} color='error' working={working} onClick={doRevoke}>
                        Revoke tokens
                    </ButtonWithProgress>
                </DialogActions>
            </Dialog>
        </>
    );
};

export interface PublisherRevokeTokensButtonProps {
    publisherInfo: PublisherInfo;
}
