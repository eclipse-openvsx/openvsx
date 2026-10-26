/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext } from 'react';
import { Box, Typography } from '@mui/material';
import { toLocalTime } from '../../../utils';
import { UserData } from '../../../extension-registry-types';
import { UserPublisherAgreement } from './user-publisher-agreement';
import { MainContext } from '../../../context';
import { DetailRow, DetailsCard } from '../../../components/details-card';
import { UserAvatar } from '../../../components/user-avatar';
import { MediaSidebarLayout } from '../../../components/media-sidebar-layout';
import { SettingsHeader } from '../settings/settings-header';

export const UserSettingsProfile: FunctionComponent<UserSettingsProfileProps> = ({ user, isAdmin }) => {
    const { pageSettings } = useContext(MainContext);

    let publisherAgreementPanel: ReactNode = null;
    if (user.publisherAgreement) {
        if (isAdmin) {
            let statusText = 'has not signed';
            if (user.publisherAgreement.status === 'signed') {
                statusText = 'has signed';
            } else if (user.publisherAgreement.status === 'revoked') {
                statusText = 'has a revoked signature of';
            } else if (user.publisherAgreement.status === 'outdated') {
                statusText = 'has signed an outdated version of';
            }

            const publisherAgreementName = pageSettings?.publisherAgreement?.name ?? '';

            publisherAgreementPanel = (
                <Typography
                    variant='body1'
                    sx={{ mt: '1.125rem' }}
                    title={toLocalTime(user.publisherAgreement.timestamp)}>
                    {user.loginName} {statusText} the {publisherAgreementName} Publisher Agreement.
                </Typography>
            );
        } else {
            publisherAgreementPanel = <UserPublisherAgreement user={user} />;
        }
    }

    return (
        <Box>
            <SettingsHeader title='Profile' />
            <MediaSidebarLayout
                sidebar={
                    <UserAvatar
                        user={user}
                        sx={{
                            width: '100%',
                            height: 'auto',
                            aspectRatio: '1 / 1',
                            borderRadius: theme => `${theme.shape.borderRadiusCard}px`,
                            border: '1px solid',
                            borderColor: 'divider',
                            fontSize: '3.5rem'
                        }}
                    />
                }>
                <DetailsCard>
                    <DetailRow label='Login name' mono>
                        {user.loginName}
                    </DetailRow>
                    {user.fullName ? <DetailRow label='Full name'>{user.fullName}</DetailRow> : null}
                </DetailsCard>
                {publisherAgreementPanel}
            </MediaSidebarLayout>
        </Box>
    );
};

export interface UserSettingsProfileProps {
    user: UserData;
    isAdmin?: boolean;
}
