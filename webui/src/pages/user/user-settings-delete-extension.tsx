/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */

import { FunctionComponent, useState, useContext, useEffect, useRef } from 'react';
import { Box } from '@mui/material';
import { MainContext } from '../../context';
import { isError, Extension, TargetPlatformVersion } from '../../extension-registry-types';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { ExtensionVersionContainer } from '../admin-dashboard/extension-version-container';
import { useNavigate } from 'react-router';
import { UserSettingsRoutes } from './user-settings-routes';

export const UserSettingsDeleteExtension: FunctionComponent<UserSettingsDeleteExtensionProps> = props => {
    const navigate = useNavigate();
    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

    useEffect(() => {
        findExtension();
    }, [props.namespace, props.extension]);

    const [loading, setLoading] = useState(false);

    const { service, handleError } = useContext(MainContext);
    const [extension, setExtension] = useState<Extension | undefined>(undefined);
    const findExtension = async () => {
        try {
            setLoading(true);
            const extensionDetail = await service.getExtension(abortController.current, props.namespace, props.extension);
            if (isError(extensionDetail)) {
                throw extensionDetail;
            }
            setExtension(extensionDetail);
            setLoading(false);
        } catch (err) {
            setLoading(false);
            setExtension(undefined);
            if (err && err.status === 404) {
                navigate(UserSettingsRoutes.EXTENSIONS);
            } else {
                handleError(err);
            }
        }
    };

    const onRemove = async (targetPlatformVersions?: TargetPlatformVersion[]) => {
        if (extension == null) {
            return;
        }

        await service.deleteExtensions(abortController.current, { namespace: extension.namespace, extension: extension.name, targetPlatformVersions: targetPlatformVersions?.map(({ version, targetPlatform }) => ({ version, targetPlatform })) });
        await findExtension();
    };

    return (
        <Box>
            <DelayedLoadIndicator loading={loading} />
            {
                extension ?
                    <ExtensionVersionContainer onRemove={onRemove} extension={extension} />
                    : ''
            }
        </Box>
    );
};

export interface UserSettingsDeleteExtensionProps {
    namespace: string;
    extension: string;
}