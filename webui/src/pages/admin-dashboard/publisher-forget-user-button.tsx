/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { PublisherInfo } from '../../extension-registry-types';
import { UpdateContext } from './publisher-admin';
import { useForgetUser } from './use-publisher-admin';

const dangerButtonSx = {
    textTransform: 'none',
    '&:hover': { bgcolor: 'error.main', color: 'common.white' }
} as const;

export const PublisherForgetUserButton: FunctionComponent<PublisherForgetUserButtonProps> = props => {
    const updateContext = useContext(UpdateContext);
    const { mutate: forgetUser, isPending: working } = useForgetUser();

    const [dialogOpen, setDialogOpen] = useState(false);

    const { user } = props.publisherInfo;

    const doForget = () => {
        forgetUser(
            { provider: user.provider as string, login: user.loginName },
            {
                onSuccess: () => {
                    setDialogOpen(false);
                    updateContext.handleUserDeleted();
                }
            }
        );
    };

    return (
        <>
            <Button variant='outlined' color='error' sx={dangerButtonSx} onClick={() => setDialogOpen(true)}>
                Forget user
            </Button>
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>Forget user</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Erase {user.loginName} in response to a data-protection erasure request? Their access tokens and
                        published extension versions are removed, namespace memberships dropped, and the account itself
                        is deleted outright if nothing else in the database still refers to it — otherwise it is
                        anonymized in place. This cannot be undone.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant='contained' color='primary' onClick={() => setDialogOpen(false)}>
                        Cancel
                    </Button>
                    <ButtonWithProgress autoFocus sx={{ ml: 1 }} color='error' working={working} onClick={doForget}>
                        Forget
                    </ButtonWithProgress>
                </DialogActions>
            </Dialog>
        </>
    );
};

export interface PublisherForgetUserButtonProps {
    publisherInfo: PublisherInfo;
}
