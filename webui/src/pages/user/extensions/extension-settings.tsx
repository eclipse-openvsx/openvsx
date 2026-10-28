/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/

import { FunctionComponent, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { Box } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { BackButton } from '../settings/settings-primitives';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { ExtensionDetailView } from '../../../components/extension/extension-detail-view';
import { UserSettingsRoutes } from '../user-settings-routes';
import { isNotFound } from '../../../server-request';
import { useDeleteUserExtensionVersions, useUserExtension } from './use-user-extension';

/** Navigation target the manage page returns to; set by the card that linked here. */
export interface ExtensionSettingsBackState {
    backTo?: string;
    backLabel?: string;
}

export const ExtensionSettings: FunctionComponent<ExtensionSettingsProps> = props => {
    const navigate = useNavigate();
    const backState = (useLocation().state ?? {}) as ExtensionSettingsBackState;
    const backTo = backState.backTo ?? UserSettingsRoutes.EXTENSIONS;
    const backLabel = backState.backLabel ?? 'Back to your extensions';

    const {
        data: extension,
        isFetching: loading,
        error,
        refetch
    } = useUserExtension({ namespace: props.namespace, extension: props.extension });
    const { mutateAsync: deleteVersions } = useDeleteUserExtensionVersions();

    // The extension doesn't exist (or isn't yours) — don't stay on a dead page.
    useEffect(() => {
        if (isNotFound(error)) {
            navigate(UserSettingsRoutes.EXTENSIONS);
        }
    }, [error, navigate]);

    if (loading) {
        return <DelayedLoadIndicator loading={true} />;
    }

    if (!extension) {
        return null;
    }

    return (
        <Box>
            <BackButton
                disableRipple
                onClick={() => navigate(backTo)}
                startIcon={<ArrowBackIcon sx={{ fontSize: '0.9375rem' }} />}
                sx={{ mb: '1.375rem' }}>
                {backLabel}
            </BackButton>
            <ExtensionDetailView
                extension={extension}
                onRemoveVersion={targets =>
                    deleteVersions({
                        namespace: extension.namespace,
                        extension: extension.name,
                        targetPlatformVersions: targets
                    })
                }
                onVersionDeleted={refetch}
            />
        </Box>
    );
};

export interface ExtensionSettingsProps {
    namespace: string;
    extension: string;
}
