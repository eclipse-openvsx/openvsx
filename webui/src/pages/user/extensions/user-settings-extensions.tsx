/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */

import { FunctionComponent } from 'react';
import { Link as RouteLink } from 'react-router';
import { Box, Button } from '@mui/material';
import FileUploadOutlinedIcon from '@mui/icons-material/FileUploadOutlined';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { ManageExtensionCard } from '../../../components/extension/manage-extension-card';
import { EmptyPlaceholder } from '../settings/settings-primitives';
import { ExtensionGrid } from '../../../components/page-primitives';
import { SettingsHeader } from '../settings/settings-header';
import { UserSettingsRoutes } from '../user-settings-routes';
import { PublishRoutes } from '../../publish/publish-routes';
import { useUserExtensions } from '../../../hooks/use-user-extensions';

export const UserSettingsExtensions: FunctionComponent = () => {
    // The publish queue reads the same entry as it follows a package, so a card lands here as soon
    // as the registry has the package, without this page fetching again.
    const { data: extensions, isLoading } = useUserExtensions();

    return (
        <Box>
            <SettingsHeader
                title='Extensions'
                actions={
                    <Button
                        variant='outlined'
                        component={RouteLink}
                        to={PublishRoutes.ROOT}
                        startIcon={<FileUploadOutlinedIcon sx={{ fontSize: '0.9375rem' }} />}>
                        Publish extension
                    </Button>
                }
            />
            <DelayedLoadIndicator loading={isLoading} />
            {extensions && extensions.length > 0 ? (
                <ExtensionGrid>
                    {extensions.map(extension => (
                        <ManageExtensionCard
                            key={`${extension.namespace}.${extension.name}-${extension.version}`}
                            extension={extension}
                            routePrefix={UserSettingsRoutes.EXTENSIONS}
                        />
                    ))}
                </ExtensionGrid>
            ) : !isLoading ? (
                <EmptyPlaceholder>You haven&apos;t published any extensions yet.</EmptyPlaceholder>
            ) : null}
        </Box>
    );
};
