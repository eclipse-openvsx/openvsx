/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useState, ReactNode } from 'react';
import { Box, Typography, Button, Dialog, DialogContent, DialogContentText, Link } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import { UserData } from '../../../extension-registry-types';
import { SanitizedMarkdown } from '../../../components/sanitized-markdown';
import { Timestamp } from '../../../components/timestamp';
import { ButtonWithProgress } from '../../../components/button-with-progress';
import { createAbsoluteURL } from '../../../utils';
import { MainContext } from '../../../context';
import { cardSurface } from '../../../components/page-primitives';
import { IconTile } from '../settings/settings-primitives';
import CircularProgress from '@mui/material/CircularProgress';
import { useLocation } from 'react-router';
import { usePublisherAgreementText, useSignPublisherAgreement } from './use-publisher-agreement';

export const UserPublisherAgreement: FunctionComponent<UserPublisherAgreementProps> = props => {
    const { service, pageSettings, handleError } = useContext(MainContext);
    const location = useLocation();
    const [dialogOpen, setDialogOpen] = useState(false);
    const agreementUrl = pageSettings.urls.publisherAgreement;
    const { data: agreementText } = usePublisherAgreementText(agreementUrl ?? '', {
        enabled: dialogOpen && !!agreementUrl
    });
    const { mutate: signAgreement, isPending: signing } = useSignPublisherAgreement();

    const closePublisherAgreement = () => {
        setDialogOpen(false);
    };

    const signPublisherAgreement = () => {
        signAgreement(undefined, { onSuccess: () => setDialogOpen(false) });
    };

    const openPublisherAgreement = () => {
        if (!agreementUrl) {
            handleError(
                { error: 'Publisher agreement text is not available.' },
                { title: 'Couldn’t load the publisher agreement' }
            );
        } else {
            setDialogOpen(true);
        }
    };

    const onClose = () => {
        setDialogOpen(false);
    };

    const user = props.user;
    if (!user.publisherAgreement) {
        return null;
    }

    const publisherAgreementName = pageSettings?.publisherAgreement?.name ?? '';
    const publisherAgreementSigned = user.publisherAgreement.status == 'signed';
    const loginProvider = user.publisherAgreement.loginProvider;
    const loginProviderName = user.publisherAgreement.loginProviderName || loginProvider;

    const outdated = user.publisherAgreement.status === 'outdated';
    const required = outdated ? (
        <>
            The {publisherAgreementName} Publisher Agreement changed since you signed it. You need to sign the new
            version before you can publish any extension to this registry.
        </>
    ) : (
        <>
            You need to sign the {publisherAgreementName} Publisher Agreement before you can publish any extension to
            this registry.
        </>
    );

    let title: string;
    let description: ReactNode;
    let action: ReactNode;
    if (publisherAgreementSigned) {
        title = 'Publisher Agreement signed';
        description = user.publisherAgreement.timestamp ? (
            <>
                You signed the {publisherAgreementName} Publisher Agreement{' '}
                <Timestamp value={user.publisherAgreement.timestamp} />.
            </>
        ) : (
            <>You signed the {publisherAgreementName} Publisher Agreement.</>
        );
        action = (
            <Button onClick={openPublisherAgreement} variant='outlined'>
                View
            </Button>
        );
    } else if (user.publisherAgreement.status === 'revoked') {
        title = 'Publisher Agreement revoked';
        description = (
            <>
                Your {publisherAgreementName} Publisher Agreement was revoked, so you cannot publish. Contact an
                administrator to sign it again.
            </>
        );
        action = null;
    } else if (!loginProvider || user.additionalLogins?.find(login => login.provider === loginProvider)) {
        title = outdated ? 'Publisher Agreement updated' : 'Publisher Agreement required';
        description = required;
        action = (
            <Button onClick={openPublisherAgreement} variant='outlined' color='secondary'>
                Review &amp; sign
            </Button>
        );
    } else {
        title = outdated ? 'Publisher Agreement updated' : 'Publisher Agreement required';
        description = (
            <>
                {required} To start the signing process, please log in with {loginProviderName}.
            </>
        );
        action = (
            <Link
                href={createAbsoluteURL(
                    [service.serverUrl, 'oauth2', 'authorization', loginProvider],
                    // the server returns to this page once the login is linked
                    [{ key: 'redirect', value: location.pathname + location.search + location.hash }]
                )}>
                <Button variant='outlined' color='secondary'>
                    Log in with {loginProviderName}
                </Button>
            </Link>
        );
    }

    return (
        <>
            <Box
                sx={theme => ({
                    ...cardSurface(theme),
                    display: 'flex',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '0.875rem',
                    p: '1rem 1.125rem',
                    mt: '1.125rem'
                })}>
                <IconTile sx={{ width: '2.125rem', height: '2.125rem' }}>
                    {publisherAgreementSigned ? (
                        <CheckIcon sx={{ fontSize: '1.0625rem' }} />
                    ) : (
                        <DescriptionOutlinedIcon sx={{ fontSize: '1.0625rem' }} />
                    )}
                </IconTile>
                <Box sx={{ flex: 1, minWidth: '12.5rem' }}>
                    <Typography sx={{ fontSize: '0.875rem', fontWeight: 600 }}>{title}</Typography>
                    <Typography sx={{ fontSize: '0.78125rem', color: 'text.disabled', mt: '0.125rem' }}>
                        {description}
                    </Typography>
                </Box>
                {action}
            </Box>
            <Dialog
                open={dialogOpen}
                onClose={onClose}
                maxWidth='xl'
                sx={{ '& .MuiDialog-paperScrollPaper': { height: '75%', width: '100%' } }}>
                <DialogContent>
                    {agreementText ? (
                        <DialogContentText component='div'>
                            <SanitizedMarkdown content={agreementText} sanitize={false} linkify={false} />
                            <Box display='flex' justifyContent='flex-end'>
                                <Button onClick={closePublisherAgreement}>Close</Button>
                                {!publisherAgreementSigned && (
                                    <ButtonWithProgress working={signing} onClick={signPublisherAgreement}>
                                        Agree
                                    </ButtonWithProgress>
                                )}
                            </Box>
                        </DialogContentText>
                    ) : (
                        <Box height={1} display='flex' justifyContent='center' alignItems='center'>
                            <CircularProgress color='secondary' />
                        </Box>
                    )}
                </DialogContent>
            </Dialog>
        </>
    );
};

export interface UserPublisherAgreementProps {
    user: UserData;
}
