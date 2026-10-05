/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { Box } from '@mui/material';
import { Namespace, isError, Extension, ErrorResult } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { DelayedLoadIndicator } from '../delayed-load-indicator';
import { ManageExtensionCard } from '../extension/manage-extension-card';
import { ExtensionGrid } from '../page-primitives';
import { EmptyPlaceholder, SettingsSectionTitle } from '../../pages/user/settings/settings-primitives';

/**
 * Retrieves the full detail for a single extension of a namespace. The caller decides which endpoint
 * to use, e.g. the public registry API (active extensions only) or the admin API (also returns
 * inactive/soft-deleted extensions). Receives both the extension `name` and its metadata `url` so the
 * implementation can use whichever it needs.
 */
export type FetchNamespaceExtension = (
    abortController: AbortController,
    extension: { name: string; url: string }
) => Promise<Readonly<Extension | ErrorResult>>;

export const NamespaceExtensionList: FunctionComponent<NamespaceExtensionListProps> = props => {
    const [extensions, setExtensions] = useState<Extension[]>();
    const [loading, setLoading] = useState<boolean>(true);
    const context = useContext(MainContext);
    // The card links back to wherever the namespace is being viewed, user settings or admin alike.
    const { pathname } = useLocation();

    const fetchExtension: FetchNamespaceExtension =
        props.fetchExtension ??
        ((abortController, extension) => context.service.getExtensionDetail(abortController, extension.url));

    // A single effect keyed on the namespace name owns the fetch lifecycle: it resets state, loads the
    // extensions, and creates a fresh AbortController each run so that switching namespaces (or unmounting)
    // aborts the previous namespace's in-flight requests instead of letting a stale response overwrite the
    // current one. (Aborts are ignored by the error handler, so this never surfaces a spurious error.)
    useEffect(() => {
        const abortController = new AbortController();
        setExtensions(undefined);
        setLoading(true);
        updateExtensions(abortController);
        return () => abortController.abort();
    }, [props.namespace.name]);

    const updateExtensions = async (abortController: AbortController): Promise<void> => {
        const entries = Object.keys(props.namespace.extensions).map((name: string) => ({
            name,
            url: props.namespace.extensions[name]
        }));

        const getExtension = async (entry: { name: string; url: string }) => {
            try {
                const result = await fetchExtension(abortController, entry);
                if (isError(result)) {
                    throw result;
                }
                return result;
            } catch (error) {
                if (!props.omitUnavailable) {
                    context.handleError(error);
                }
                return undefined;
            }
        };

        const extensionUnfiltered = await Promise.all(entries.map(getExtension));
        const extensions = extensionUnfiltered.filter(e => e != null) as Extension[];

        setExtensions(extensions);
        setLoading(false);
    };

    return (
        <Box>
            <SettingsSectionTitle component='h3'>Extensions</SettingsSectionTitle>
            <DelayedLoadIndicator loading={loading} />
            {extensions && extensions.length > 0 ? (
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
            ) : !loading ? (
                <EmptyPlaceholder>No extensions published under this namespace yet.</EmptyPlaceholder>
            ) : null}
        </Box>
    );
};

export interface NamespaceExtensionListProps {
    namespace: Namespace;
    // Base route each extension card links to. Supplied by the caller.
    routePrefix: string;
    // Endpoint used to retrieve each extension's detail. Defaults to the public registry API.
    fetchExtension?: FetchNamespaceExtension;
    // Leave out an extension the fetch cannot resolve instead of reporting it. For a caller listing
    // an admin namespace through the public API: that payload names inactive and soft-deleted
    // extensions too, which the public API has nothing to return for.
    omitUnavailable?: boolean;
}
