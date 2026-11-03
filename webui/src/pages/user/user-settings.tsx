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
import { Box, Typography, Link } from '@mui/material';
import { useParams } from 'react-router';
import { PageContainer } from '../../components/page-container';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { UserSettingsSidebar } from './settings/user-settings-sidebar';
import { UserSettingsMobileNav } from './settings/user-settings-mobile-nav';
import { useActiveSettingsTab, useContributedTabs } from './settings/settings-tabs';
import { UserSettingsTokens } from './tokens/user-settings-tokens';
import { UserSettingsTrustedPublishers } from './trusted-publishing/user-settings-trusted-publishers';
import { useTrustedPublishingStatus } from './trusted-publishing/use-trusted-publishers';
import { UserSettingsProfile } from './profile/user-settings-profile';
import { UserSettingsNamespaces } from './namespaces/user-settings-namespaces';
import { UserSettingsExtensions } from './extensions/user-settings-extensions';
import { UserSettingsCustomers } from './customers/user-settings-customers';
import { MainContext } from '../../context';
import { UserData } from '../../extension-registry-types';
import { LoginComponent } from '../../default/login';
import { ExtensionSettings } from './extensions/extension-settings';

export const UserSettings: FunctionComponent<UserSettingsProps> = props => {
    const { pageSettings, user, loginProviders } = useContext(MainContext);
    const { tab, namespace, extension } = useParams();
    const activeTab = useActiveSettingsTab();
    const contributedTabs = useContributedTabs();
    const { data: trustedPublishingStatus } = useTrustedPublishingStatus();
    const trustedPublishingEnabled = trustedPublishingStatus?.enabled ?? false;

    const renderTab = (user: UserData): ReactNode => {
        if (tab == null && namespace != null && extension != null) {
            return <ExtensionSettings namespace={namespace} extension={extension} />;
        }

        switch (activeTab) {
            case 'profile':
                return <UserSettingsProfile user={user} />;
            case 'tokens':
                return <UserSettingsTokens />;
            case 'trusted-publishers':
                return trustedPublishingEnabled ? <UserSettingsTrustedPublishers /> : null;
            case 'namespaces':
                return <UserSettingsNamespaces selectedName={namespace} />;
            case 'extensions':
                return <UserSettingsExtensions />;
            case 'customers':
                return <UserSettingsCustomers />;
            default:
                return contributedTabs.find(t => t.value === activeTab)?.element ?? null;
        }
    };

    const renderContent = (): ReactNode => {
        if (props.userLoading) {
            return <DelayedLoadIndicator loading={true} />;
        }

        if (!user) {
            return loginProviders ? (
                <PageContainer>
                    <Typography variant='h4'>Not Logged In</Typography>
                    <Box mt={2}>
                        <Typography variant='body1'>
                            Please{' '}
                            <LoginComponent
                                loginProviders={loginProviders}
                                renderButton={(href, onClick) => {
                                    return (
                                        <Link color='secondary' href={href} onClick={onClick}>
                                            log in
                                        </Link>
                                    );
                                }}
                            />{' '}
                            to access your account settings.
                        </Typography>
                    </Box>
                </PageContainer>
            ) : null;
        }

        return (
            <PageContainer
                sx={{
                    // No mobile padding: the sticky tab pills sit directly below the navbar.
                    pt: { xs: 0, md: '2.625rem' },
                    display: 'grid',
                    gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: '14.75rem minmax(0, 1fr)' },
                    gap: { xs: '1.5rem', md: '2.5rem' },
                    alignItems: 'start'
                }}>
                <UserSettingsSidebar />
                <Box sx={{ minWidth: 0 }}>
                    {/* Inside the content column so the sticky row can travel its full height. */}
                    <UserSettingsMobileNav />
                    {renderTab(user)}
                </Box>
            </PageContainer>
        );
    };

    return (
        <>
            <Helmet>
                <title>Settings – {pageSettings.pageTitle}</title>
            </Helmet>
            {renderContent()}
        </>
    );
};

export interface UserSettingsProps {
    userLoading: boolean;
}
