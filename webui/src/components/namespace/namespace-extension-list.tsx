/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { useLocation } from 'react-router';
import { Box } from '@mui/material';
import { Namespace } from '../../extension-registry-types';
import { DelayedLoadIndicator } from '../delayed-load-indicator';
import { ManageExtensionCard } from '../extension/manage-extension-card';
import { ExtensionGrid } from '../page-primitives';
import { EmptyPlaceholder, SettingsSectionTitle } from '../../pages/user/settings/settings-primitives';
import { NamespaceExtensionQuery, useNamespaceExtensionList } from './use-namespace-extension-list';

export const NamespaceExtensionList: FunctionComponent<NamespaceExtensionListProps> = props => {
    // The card links back to wherever the namespace is being viewed, user settings or admin alike.
    const { pathname } = useLocation();
    const { extensions, isPending: loading } = useNamespaceExtensionList(props.namespace, {
        extensionQuery: props.extensionQuery,
        omitUnavailable: props.omitUnavailable
    });

    return (
        <Box>
            <SettingsSectionTitle component='h3'>Extensions</SettingsSectionTitle>
            <DelayedLoadIndicator loading={loading} />
            {loading ? null : extensions.length > 0 ? (
                <ExtensionGrid>
                    {extensions.map(extension => (
                        <ManageExtensionCard
                            key={`${extension.namespace}.${extension.name}-${extension.version}`}
                            extension={extension}
                            routePrefix={props.routePrefix}
                            linkState={{
                                backTo: pathname,
                                backLabel: `Back to ${props.namespace.name}`
                            }}
                        />
                    ))}
                </ExtensionGrid>
            ) : (
                <EmptyPlaceholder>No extensions published under this namespace yet.</EmptyPlaceholder>
            )}
        </Box>
    );
};

export interface NamespaceExtensionListProps {
    namespace: Namespace;
    // Base route each extension card links to. Supplied by the caller.
    routePrefix: string;
    // Query used to retrieve each extension's detail. Defaults to the public registry API.
    extensionQuery?: NamespaceExtensionQuery;
    // Leave out an extension the query answers 404 for, instead of reporting it. For a caller
    // listing an admin namespace through the public API: that payload names inactive and
    // soft-deleted extensions too, which the public API has nothing to return for. Every other
    // failure is still reported - those mean the list is wrong, not that the extension is hidden.
    omitUnavailable?: boolean;
}
