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
import { Helmet } from 'react-helmet-async';
import { Grid, Container, Box, Typography, Link } from '@mui/material';
import { useParams } from 'react-router-dom';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { UserSettingTabs } from './user-setting-tabs';
import { UserSettingsTokens } from './user-settings-tokens';
import { UserSettingsProfile } from './user-settings-profile';
import { UserSettingsNamespaces } from './user-settings-namespaces';
import { UserSettingsExtensions } from './user-settings-extensions';
import { MainContext } from '../../context';
import { UserData } from '../../extension-registry-types';
import { LoginComponent } from '../../default/login';
import { UserSettingsDeleteExtension } from './user-settings-delete-extension';

export const UserSettings: FunctionComponent<UserSettingsProps> = props => {

    const { pageSettings, user, loginProviders } = useContext(MainContext);
    const { tab, namespace, extension } = useParams();

    const renderTab = (user: UserData, tab?: string, namespace?: string, extension?: string): ReactNode => {
        if (tab == null && namespace != null && extension != null) {
            return <UserSettingsDeleteExtension namespace={namespace} extension={extension}/>;
        }

        switch (tab) {
            case 'profile':
                return <UserSettingsProfile user={user} />;
            case 'tokens':
                return <UserSettingsTokens />;
            case 'namespaces':
                return <UserSettingsNamespaces />;
            case 'extensions':
                return <UserSettingsExtensions />;
            default:
                return null;
        }
    };

    const renderContent = (): ReactNode => {
        if (props.userLoading) {
            return <DelayedLoadIndicator loading={true} />;
        }

        if (!user) {
            return loginProviders ? <Container>
                <Box mt={6}>
                    <Typography variant='h4'>Not Logged In</Typography>
                    <Box mt={2}>
                        <Typography variant='body1'>
                            Please <LoginComponent loginProviders={loginProviders} renderButton={(href, onClick) => {
return (<Link color='secondary' href={href} onClick={onClick}>log in</Link>);
}}/> to
                            access your account settings.
                        </Typography>
                    </Box>
                </Box>
            </Container> : null;
        }

        return <Container>
            <Box mt={6}>
                <Grid container sx={{ flexDirection: { xs: 'column', sm: 'column', md: 'column', lg: 'row', xl: 'row' } }}>
                    <Grid item sx={{ mb: { xs: '3rem', sm: '3rem', md: '3rem', lg: '3rem', xl: 0 } }}>
                        <UserSettingTabs />
                    </Grid>
                    <Grid
                        item
                        sx={{
                            pt: { xs: 0, sm: 0, md: 0, lg: '.5rem', xl: '.5rem' },
                            pl: { xs: 0, sm: 0, md: 0, lg: '3rem', xl: '3rem' },
                            flex: { xs: 'none', sm: 'none', md: 'none', lg: '1', xl: '1' },
                            width: { xs: '100%', sm: '100%', md: '100%', lg: 'auto', xl: 'auto' }
                        }}
                    >
                        <Box>
                            {renderTab(user, tab, namespace, extension)}
                        </Box>
                    </Grid>
                </Grid>
            </Box>
        </Container>;
    };

    return <>
        <Helmet>
            <title>Settings – { pageSettings.pageTitle }</title>
        </Helmet>
        { renderContent() }
    </>;
};

export interface UserSettingsProps {
    userLoading: boolean;
}