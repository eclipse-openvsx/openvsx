/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Button, Typography } from '@mui/material';

import { SearchListContainer } from './search-list-container';
import { StyledInput } from './namespace-input';
import { useAdminExtension, useDeleteExtension, usePurgeExtension } from './use-extension-admin';
import { ExtensionDetailView } from '../../components/extension/extension-detail-view';
import { AdminDashboardRoutes } from './admin-dashboard-routes';
import { createRoute } from '../../utils';
import { isNotFound } from '../../server-request';

export const ExtensionAdmin: FunctionComponent = () => {
    const navigate = useNavigate();

    const { namespace: nsParam, extension: extParam } = useParams<{ namespace?: string; extension?: string }>();

    const [namespaceValue, setNamespaceValue] = useState(nsParam ?? '');
    const [extensionValue, setExtensionValue] = useState(extParam ?? '');

    useEffect(() => {
        setNamespaceValue(nsParam ?? '');
        setExtensionValue(extParam ?? '');
    }, [nsParam, extParam]);

    const [validationError, setValidationError] = useState('');
    const [extensionFieldError, setExtensionFieldError] = useState(false);
    const [namespaceFieldError, setNamespaceFieldError] = useState(false);

    const target = nsParam && extParam ? { namespace: nsParam, extension: extParam } : null;

    const { data, isFetching: loading, error: queryError, refetch } = useAdminExtension(target);
    const { mutateAsync: deleteExtension } = useDeleteExtension();
    const { mutateAsync: purgeExtension } = usePurgeExtension();

    const extension = queryError ? undefined : data;
    const fetchError = isNotFound(queryError) ? `Extension not found: ${target?.namespace}.${target?.extension}` : '';
    const error = validationError || fetchError;

    const findExtension = () => {
        if (!namespaceValue) {
            setNamespaceFieldError(true);
            setValidationError('Name of Namespace is mandatory');
            return;
        }
        setNamespaceFieldError(false);
        if (!extensionValue) {
            setExtensionFieldError(true);
            setValidationError('Name of Extension is mandatory');
            return;
        }
        setExtensionFieldError(false);
        setValidationError('');
        if (nsParam === namespaceValue && extParam === extensionValue) {
            refetch();
        } else {
            navigate(createRoute([AdminDashboardRoutes.EXTENSION_ADMIN, namespaceValue, extensionValue]));
        }
    };

    return (
        <SearchListContainer
            searchContainer={[
                <StyledInput
                    placeholder='Namespace'
                    error={namespaceFieldError}
                    key='nsi'
                    onChange={setNamespaceValue}
                    value={namespaceValue}
                    hideIconButton={true}
                    autoFocus={true}
                />,
                <StyledInput
                    error={extensionFieldError}
                    key='ei'
                    onChange={setExtensionValue}
                    value={extensionValue}
                    onSubmit={findExtension}
                    placeholder='Extension'
                    hideIconButton={true}
                    autoFocus={false}
                />,
                <Button key='btn' variant='contained' onClick={findExtension}>
                    Search Extension
                </Button>,
                error ? <Typography color='error'>{error}</Typography> : ''
            ]}
            listContainer={
                extension ? (
                    <ExtensionDetailView
                        extension={extension}
                        onRemoveVersion={targets =>
                            deleteExtension({
                                namespace: extension.namespace,
                                extension: extension.name,
                                targetPlatformVersions: targets
                            })
                        }
                        onPurgeVersion={targets =>
                            purgeExtension({
                                namespace: extension.namespace,
                                extension: extension.name,
                                targetPlatformVersions: targets
                            })
                        }
                        onVersionDeleted={refetch}
                    />
                ) : (
                    ''
                )
            }
            loading={loading}
        />
    );
};
