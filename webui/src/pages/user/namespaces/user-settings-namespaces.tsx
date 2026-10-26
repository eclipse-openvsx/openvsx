/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useMemo, useState, ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { Box, Button, ButtonBase, Link, Typography, useMediaQuery, useTheme } from '@mui/material';
import { styled } from '@mui/material/styles';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { Namespace, UserData } from '../../../extension-registry-types';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { MainContext } from '../../../context';
import { MONO_FONT } from '../../../default/theme';
import { cardSurface, focusOutline } from '../../../components/page-primitives';
import { createRoute } from '../../../utils';
import { NamespaceDetailView } from '../../../components/namespace/namespace-detail-view';
import {
    NamespaceDetailConfig,
    NamespaceDetailConfigContext
} from '../../../components/namespace/namespace-detail-config';
import { CreateNamespaceDialog } from './create-namespace-dialog';
import { UserSettingsRoutes } from '../user-settings-routes';
import { BackButton } from '../settings/settings-primitives';
import { useHandleNamespaceCreated, useUserNamespaces } from './use-user-namespaces';

// Mobile-only list entry; on desktop namespaces are picked from the sidebar.
const NamespaceListItem = styled(ButtonBase)(({ theme }) => ({
    ...cardSurface(theme),
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    gap: '0.8125rem',
    textAlign: 'left',
    padding: '0.875rem 1rem',
    marginBottom: '0.625rem',
    fontFamily: 'inherit',
    ...focusOutline(theme),
    '@media (hover: hover)': {
        '&:hover': { borderColor: theme.palette.secondary.main }
    }
}));

export const UserSettingsNamespaces: FunctionComponent<UserSettingsNamespacesProps> = ({ selectedName }) => {
    const { pageSettings, service, user } = useContext(MainContext);
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    const navigate = useNavigate();
    const namespacesQuery = useUserNamespaces();
    const handleNamespaceCreated = useHandleNamespaceCreated();
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);

    const namespaces = namespacesQuery.data ?? [];
    const loading = namespacesQuery.isLoading || detailLoading;
    const namespaceAccessUrl = pageSettings.urls.namespaceAccessInfo;

    // Pin the viewer's own membership and hide them from the add-member search.
    const detailConfig = useMemo<NamespaceDetailConfig>(
        () => ({
            fixSelf: true,
            filterUsers: (foundUser: UserData) =>
                foundUser.provider !== user?.provider || foundUser.loginName !== user?.loginName
        }),
        [user]
    );

    const openNamespace = (namespace: Namespace) => {
        navigate(createRoute([UserSettingsRoutes.NAMESPACES, namespace.name]));
    };

    const renderDetail = (namespace: Namespace): ReactNode => (
        <NamespaceDetailConfigContext.Provider value={detailConfig}>
            <NamespaceDetailView
                namespace={namespace}
                setLoadingState={setDetailLoading}
                extensionRoutePrefix={UserSettingsRoutes.EXTENSIONS}
                showClaimAction
                // The public endpoint hides what a namespace member still needs to manage: an
                // inactive extension, or one whose only versions are soft-deleted.
                extensionQuery={extension => ({
                    queryKey: ['user', 'extension', namespace.name, extension.name],
                    queryFn: ({ signal }) => service.getExtension(signal, namespace.name, extension.name)
                })}
            />
        </NamespaceDetailConfigContext.Provider>
    );

    const renderList = (): ReactNode => (
        <Box>
            {namespaces.map(namespace => {
                const extensionCount = Object.keys(namespace.extensions).length;
                return (
                    <NamespaceListItem key={namespace.name} onClick={() => openNamespace(namespace)}>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.4375rem' }}>
                                <Typography
                                    component='span'
                                    noWrap
                                    sx={{ fontFamily: MONO_FONT, fontSize: '0.9375rem', fontWeight: 700, minWidth: 0 }}>
                                    {namespace.name}
                                </Typography>
                                {!namespace.verified ? (
                                    <WarningAmberIcon
                                        titleAccess='Not verified'
                                        sx={{ fontSize: '0.875rem', color: 'warningAccent', flexShrink: 0 }}
                                    />
                                ) : null}
                            </Box>
                            <Typography sx={{ fontSize: '0.78125rem', color: 'text.disabled', mt: '0.1875rem' }}>
                                {extensionCount === 1 ? '1 extension' : `${extensionCount} extensions`}
                            </Typography>
                        </Box>
                        <ChevronRightIcon sx={{ fontSize: '1.125rem', color: 'text.disabled', flexShrink: 0 }} />
                    </NamespaceListItem>
                );
            })}
            <Button variant='outlined' startIcon={<AddIcon />} onClick={() => setCreateDialogOpen(true)}>
                Create namespace
            </Button>
        </Box>
    );

    const renderContent = (): ReactNode => {
        if (namespaces.length === 0) {
            if (loading) {
                return null;
            }
            return (
                <Box>
                    <Typography variant='body1' sx={{ mb: '1rem' }}>
                        No namespaces available. Read{' '}
                        <Link color='secondary' href={namespaceAccessUrl} target='_blank'>
                            here
                        </Link>{' '}
                        about claiming namespaces.
                    </Typography>
                    <Button variant='outlined' startIcon={<AddIcon />} onClick={() => setCreateDialogOpen(true)}>
                        Create namespace
                    </Button>
                </Box>
            );
        }

        if (isMobile && selectedName == null) {
            return renderList();
        }

        const chosenNamespace = namespaces.find(namespace => namespace.name === selectedName) ?? namespaces[0];
        return (
            <Box>
                {isMobile ? (
                    <BackButton
                        disableRipple
                        onClick={() => navigate(UserSettingsRoutes.NAMESPACES)}
                        startIcon={<ArrowBackIcon sx={{ fontSize: '0.9375rem' }} />}
                        sx={{ mb: '1.125rem' }}>
                        All namespaces
                    </BackButton>
                ) : null}
                {renderDetail(chosenNamespace)}
            </Box>
        );
    };

    return (
        <Box>
            <DelayedLoadIndicator loading={loading} />
            {renderContent()}
            <CreateNamespaceDialog
                open={createDialogOpen}
                onClose={() => setCreateDialogOpen(false)}
                namespaceCreated={handleNamespaceCreated}
            />
        </Box>
    );
};

export interface UserSettingsNamespacesProps {
    /** Name of the namespace selected via the route, if any. */
    selectedName?: string;
}
