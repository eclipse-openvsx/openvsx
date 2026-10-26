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

import { FunctionComponent, useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { Namespace, TrustedPublisher } from '../../../extension-registry-types';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { useUserNamespaces } from '../namespaces/use-user-namespaces';
import { EmptyPlaceholder } from '../settings/settings-primitives';
import { SettingsHeader } from '../settings/settings-header';
import { PublisherList } from './publisher-list';
import { RegisterTrustedPublisherDialog, TrustedPublishingDocsLink } from './register-trusted-publisher-dialog';
import {
    useAllTrustedPublishers,
    useDeleteTrustedPublisher,
    useTrustedPublishingStatus
} from './use-trusted-publishers';

// Stable order across namespaces: namespace, then extension, then registration id.
const byNamespaceExtensionId = (a: TrustedPublisher, b: TrustedPublisher): number =>
    a.namespace.localeCompare(b.namespace) || a.extension.localeCompare(b.extension) || a.id - b.id;

// The backend sets trustedPublishingUrl only where the user may manage TP; the guard narrows the type too.
type TrustedPublishingNamespace = Namespace & { trustedPublishingUrl: string };
const canManageTrustedPublishers = (ns: Namespace): ns is TrustedPublishingNamespace =>
    Boolean(ns.trustedPublishingUrl);

/** Settings tab showing the trusted publishers of every namespace the user belongs to as one stack. */
export const UserSettingsTrustedPublishers: FunctionComponent = () => {
    const { data: namespaces = [], isLoading: namespacesLoading } = useUserNamespaces();

    // providers are registry-global; without any (feature off or user not allowed) nothing is manageable
    const { data: trustedPublishingStatus, isLoading: statusLoading } = useTrustedPublishingStatus();
    const providers = trustedPublishingStatus?.trustedPublisherProviders ?? [];
    const manageableNamespaces = providers.length > 0 ? namespaces.filter(canManageTrustedPublishers) : [];
    const {
        publishers: allPublishers,
        registrableExtensions,
        isLoading: publishersLoading
    } = useAllTrustedPublishers(manageableNamespaces.map(ns => ns.trustedPublishingUrl));
    const { mutateAsync: deleteTrustedPublisher, isPending: deleting } = useDeleteTrustedPublisher();
    const [dialogOpen, setDialogOpen] = useState(false);

    const loading = namespacesLoading || statusLoading || publishersLoading || deleting;
    const publishers = [...allPublishers].sort(byNamespaceExtensionId);
    // registrableExtensions is index-aligned with manageableNamespaces
    const selectableNamespaces = manageableNamespaces.map((ns, index) => ({
        name: ns.name,
        registrableExtensions: registrableExtensions[index] ?? [],
        registeredExtensions: allPublishers.filter(p => p.namespace === ns.name).map(p => p.extension),
        trustedPublishingUrl: ns.trustedPublishingUrl
    }));
    // nothing to register anywhere: every active extension is taken, or there are none
    const canRegister = selectableNamespaces.some(ns => ns.registrableExtensions.length > 0);

    const remove = async (publisher: TrustedPublisher) => {
        const trustedPublishingUrl = manageableNamespaces.find(
            ns => ns.name === publisher.namespace
        )?.trustedPublishingUrl;
        if (!trustedPublishingUrl) {
            return;
        }
        try {
            await deleteTrustedPublisher({ trustedPublishingUrl, id: publisher.id });
        } catch {
            // Reported by the hook; awaited so the confirmation closes once the delete settles.
        }
    };

    return (
        <Box>
            <SettingsHeader
                title='Trusted Publishers'
                description={
                    <>
                        Let CI/CD workflows publish your extensions without long-lived access tokens. The workflow
                        authenticates with a short-lived OIDC token at publish time, accepted only when its repository,
                        workflow and (if set) environment match a registration. <TrustedPublishingDocsLink />
                    </>
                }
                actions={
                    canRegister ? (
                        <Button variant='outlined' startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
                            Add a trusted publisher
                        </Button>
                    ) : undefined
                }
            />
            <DelayedLoadIndicator loading={loading} />
            {publishers.length > 0 ? (
                <>
                    <PublisherList
                        publishers={publishers}
                        providers={providers}
                        loading={deleting}
                        rowDetail='namespace/extension'
                        onDelete={remove}
                    />
                    {!canRegister ? (
                        <Typography variant='body2' color='text.secondary' sx={{ mt: 2 }}>
                            Every active extension in your namespaces already has a trusted publisher. An extension can
                            have only one.
                        </Typography>
                    ) : null}
                </>
            ) : !loading ? (
                <EmptyPlaceholder>
                    {manageableNamespaces.length === 0
                        ? 'Trusted publishers are registered per namespace — create or join a namespace first.'
                        : 'No trusted publishers registered yet.'}
                </EmptyPlaceholder>
            ) : null}
            <RegisterTrustedPublisherDialog
                open={dialogOpen}
                onClose={() => setDialogOpen(false)}
                namespaces={selectableNamespaces}
                providers={providers}
            />
        </Box>
    );
};
