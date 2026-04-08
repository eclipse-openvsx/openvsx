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
import { Theme, Grid, Typography, Avatar } from '@mui/material';
import { styled } from '@mui/material/styles';
import { toLocalTime } from '../../utils';
import { UserData } from '../../extension-registry-types';
import { UserPublisherAgreement } from './user-publisher-agreement';
import { MainContext } from "../../context";

const ProfileGrid = styled(Grid)(({ theme }: {theme: Theme}) => ({
    [theme.breakpoints.up('lg')]: {
        justifyContent: 'space-between'
    },
    ['@media(max-width: 1040px)']: {
        flexDirection: 'row-reverse',
        justifyContent: 'flex-end',
        '& > div:first-of-type': {
            marginLeft: '2rem'
        }
    },
    [theme.breakpoints.down('sm')]: {
        textAlign: 'center',
        flexDirection: 'column-reverse',
        '& > div:first-of-type': {
            marginLeft: '0',
            marginTop: '2rem'
        }
    },
    marginBottom: theme.spacing(2)
}));

export const UserSettingsProfile: FunctionComponent<UserSettingsProfileProps> = ({ user, isAdmin }) => {

    const { pageSettings } = useContext(MainContext);

    let publisherAgreementPanel: ReactNode = null;
    if (user.publisherAgreement) {
        if (isAdmin) {
            let statusText = 'has not signed';
            if (user.publisherAgreement.status === 'signed') {
                statusText = 'has signed';
            } else if (user.publisherAgreement.status === 'outdated') {
                statusText = 'has signed an outdated version of';
            }

            const publisherAgreementName = pageSettings?.publisherAgreement?.name ?? '';

            publisherAgreementPanel = <Typography variant='body1' title={toLocalTime(user.publisherAgreement.timestamp)}>
                {user.loginName} {statusText} the {publisherAgreementName} Publisher Agreement.
            </Typography>;
        } else {
            publisherAgreementPanel = <Grid container>
                <Grid item xs={12}>
                    <UserPublisherAgreement user={user} />
                </Grid>
            </Grid>;
        }
    }

    return <>
        <ProfileGrid container>
            <Grid item>
                <Typography variant='h5' gutterBottom>Profile</Typography>
                <Typography variant='body1'>Login name: {user.loginName}</Typography>
                <Typography variant='body1'>Full name: {user.fullName}</Typography>
            </Grid>
            <Grid item>
                <Avatar
                    variant='rounded'
                    src={user.avatarUrl}
                    sx={{
                        width: '150px',
                        height: '150px',
                        my: 0,
                        mx: { xs: 'auto', sm: 'auto', md: 0, lg: 0, xl: 0 }
                    }}
                />
            </Grid>
        </ProfileGrid>
        {publisherAgreementPanel}
    </>;
};

export interface UserSettingsProfileProps {
    user: UserData;
    isAdmin?: boolean;
}
