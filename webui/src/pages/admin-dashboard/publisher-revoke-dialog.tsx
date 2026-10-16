/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useState, useContext } from 'react';
import {
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogContentText,
    DialogActions,
    Typography,
    Link
} from '@mui/material';
import { createAbsoluteURL } from '../../utils';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { PublisherInfo } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { UpdateContext } from './publisher-admin';
import { useRevokePublisherContributions } from './use-publisher-admin';

export const PublisherRevokeContributionsButton: FunctionComponent<PublisherRevokeContributionsButtonProps> = props => {
    const { user, service, handleError } = useContext(MainContext);
    const updateContext = useContext(UpdateContext);
    const { mutateAsync: revokeContributions, isPending: working } = useRevokePublisherContributions();

    const [dialogOpen, setDialogOpen] = useState(false);

    // A missing publisherAgreement means the backend could not reliably determine the status
    // (see PublisherAgreementService#adminEnrichUserJson) - treat that the same as 'none':
    // only a confirmed 'signed' or 'outdated' agreement needs the additional login to revoke.
    const agreementStatus = props.publisherInfo.user.publisherAgreement?.status;
    const hasAgreement = agreementStatus === 'signed' || agreementStatus === 'outdated';

    const loginProvider = user?.publisherAgreement?.loginProvider;
    if (hasAgreement && loginProvider && !user?.additionalLogins?.find(login => login.provider === loginProvider)) {
        // The admin must hold the agreement's additional login to revoke it
        return (
            <Link href={createAbsoluteURL([service.serverUrl, 'oauth2', 'authorization', loginProvider])}>
                <Button variant='contained' color='secondary'>
                    Log in with {user?.publisherAgreement?.loginProviderName || loginProvider}
                </Button>
            </Link>
        );
    }

    const doRevoke = async () => {
        try {
            const user = props.publisherInfo.user;
            await revokeContributions({ provider: user.provider as string, login: user.loginName });
            updateContext.handleUpdate();
            setDialogOpen(false);
        } catch (err) {
            handleError(err);
        }
    };

    const tokenCount = props.publisherInfo.activeAccessTokenNum;
    const extensionCount = props.publisherInfo.extensions.filter(e => e.active).length;

    return (
        <>
            <Button variant='outlined' color='error' onClick={() => setDialogOpen(true)}>
                Revoke publisher contributions
            </Button>
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>Revoke publisher contributions</DialogTitle>
                <DialogContent>
                    <DialogContentText component='div'>
                        <Typography component='div'>
                            {!tokenCount && !extensionCount && !hasAgreement ? (
                                <>
                                    Publisher {props.publisherInfo.user.loginName} currently has no contributions to
                                    revoke. Send the request anyway?
                                </>
                            ) : (
                                <>
                                    The following actions will be executed:
                                    <ul>
                                        {tokenCount > 0 ? (
                                            <li>
                                                Deactivate {tokenCount} access token{tokenCount > 1 ? 's' : ''} of{' '}
                                                {props.publisherInfo.user.loginName}
                                            </li>
                                        ) : null}
                                        {extensionCount > 0 ? (
                                            <li>
                                                Deactivate {extensionCount} published extension version
                                                {extensionCount > 1 ? 's' : ''}
                                            </li>
                                        ) : null}
                                        {hasAgreement ? (
                                            <li>
                                                Revoke the Publisher Agreement of {props.publisherInfo.user.loginName}
                                            </li>
                                        ) : null}
                                    </ul>
                                </>
                            )}
                        </Typography>
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant='contained' color='primary' onClick={() => setDialogOpen(false)}>
                        Cancel
                    </Button>
                    <ButtonWithProgress autoFocus sx={{ ml: 1 }} color='error' working={working} onClick={doRevoke}>
                        Revoke contributions
                    </ButtonWithProgress>
                </DialogActions>
            </Dialog>
        </>
    );
};

export interface PublisherRevokeContributionsButtonProps {
    publisherInfo: PublisherInfo;
}
