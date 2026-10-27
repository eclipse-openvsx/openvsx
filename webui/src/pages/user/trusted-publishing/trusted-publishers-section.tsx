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

import { FunctionComponent, useContext, useState } from 'react';
import { Box, Button, IconButton, Stack, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import { Eyebrow } from '../../../components/page-primitives';
import { SettingsSectionTitle } from '../settings/settings-primitives';
import { MainContext } from '../../../context';
import { Namespace, TrustedPublisher, TrustedPublisherProvider, UrlString } from '../../../extension-registry-types';
import { RegisterTrustedPublisherDialog, TrustedPublishingDocsLink } from './register-trusted-publisher-dialog';
import { PublisherList } from './publisher-list';
import { TrustedPublisherProviderIcon } from './trusted-publisher-provider-icon';
import {
    useDeleteTrustedPublisher,
    useTrustedPublishers,
    useTrustedPublishersProbe,
    useTrustedPublishingStatus
} from './use-trusted-publishers';

const AddPublisherPanel = styled(Box)(({ theme }) => ({
    position: 'relative',
    marginTop: theme.spacing(2),
    padding: theme.spacing(3),
    border: `2px dashed ${theme.palette.divider}`,
    // Card radius, not control: it alternates with the dashed EmptyPlaceholder in the same slot.
    borderRadius: theme.shape.borderRadiusCard,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: theme.spacing(1.5),
    textAlign: 'center'
}));

const CloseAddPanelButton = styled(IconButton)({
    position: 'absolute',
    top: '0.5rem',
    right: '0.5rem'
});

export interface TrustedPublishersSectionProps {
    namespace: string;
    // base endpoint for this namespace's trusted-publishing requests
    trustedPublishingUrl: UrlString;
    // filters the list and locks registrations to this extension
    extensionFilter?: string;
    providers: TrustedPublisherProvider[];
    // scope-specific first sentence of the section description; omit to render no description
    intro?: string;
    emptyText: string;
}

export const TrustedPublishersSection: FunctionComponent<TrustedPublishersSectionProps> = props => {
    const { data, isFetching } = useTrustedPublishers(props.trustedPublishingUrl);
    const { mutateAsync: deleteTrustedPublisher, isPending: deleting } = useDeleteTrustedPublisher();
    const [selectedProvider, setSelectedProvider] = useState('');
    const [dialogOpen, setDialogOpen] = useState(false);
    const [showAdd, setShowAdd] = useState(false);

    // the list endpoint is namespace-scoped, so extension pages filter client-side
    const allPublishers = data?.trustedPublishers ?? [];
    const publishers = props.extensionFilter
        ? allPublishers.filter(publisher => publisher.extension === props.extensionFilter)
        : allPublishers;
    // the server reports which extensions are still registrable (active and without a publisher);
    // an extension page can only ever register for its own extension
    const allRegistrable = data?.registrableExtensions ?? [];
    const registrableExtensions = props.extensionFilter
        ? allRegistrable.filter(name => name === props.extensionFilter)
        : allRegistrable;
    const loading = isFetching || deleting;

    const remove = async (publisher: TrustedPublisher) => {
        try {
            await deleteTrustedPublisher({ trustedPublishingUrl: props.trustedPublishingUrl, id: publisher.id });
        } catch {
            // Reported by the hook; awaited so the confirmation closes once the delete settles.
        }
    };

    const openDialogWith = (providerId: string) => {
        setSelectedProvider(providerId);
        setDialogOpen(true);
    };

    // an extension takes at most one trusted publisher, so with nothing registrable left the add
    // affordance is replaced by a note saying why
    const canRegister = registrableExtensions.length > 0;
    // the empty state shows the add panel; otherwise it collapses behind a button
    const hasPublishers = publishers.length > 0;
    const addPanelVisible = canRegister && (!hasPublishers || showAdd);
    const allTakenText = props.extensionFilter
        ? 'This extension already has a trusted publisher. Delete it to register a different one.'
        : 'Every active extension in this namespace already has a trusted publisher.';

    return (
        <>
            {props.intro ? (
                <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                    {props.intro} The workflow authenticates with a short-lived OIDC token at publish time, accepted
                    only when its repository, workflow and (if set) environment match a registration below.{' '}
                    <TrustedPublishingDocsLink />
                </Typography>
            ) : null}
            <PublisherList
                publishers={publishers}
                providers={props.providers}
                loading={loading}
                rowDetail={props.extensionFilter ? 'none' : 'extension'}
                emptyText={props.emptyText}
                onDelete={remove}
            />
            {addPanelVisible ? (
                <AddPublisherPanel>
                    {hasPublishers && (
                        <CloseAddPanelButton
                            size='small'
                            onClick={() => setShowAdd(false)}
                            aria-label='Cancel adding a trusted publisher'>
                            <CloseIcon fontSize='small' />
                        </CloseAddPanelButton>
                    )}
                    <Typography variant='subtitle2' color='text.secondary'>
                        Add a trusted publisher
                    </Typography>
                    <Stack direction='row' spacing={2} useFlexGap sx={{ flexWrap: 'wrap', justifyContent: 'center' }}>
                        {props.providers.map(provider => (
                            <Button
                                key={provider.id}
                                variant='contained'
                                color='inherit'
                                disableElevation
                                startIcon={<TrustedPublisherProviderIcon providerId={provider.id} />}
                                onClick={() => openDialogWith(provider.id)}>
                                {provider.name}
                            </Button>
                        ))}
                    </Stack>
                </AddPublisherPanel>
            ) : canRegister ? (
                <Button variant='outlined' startIcon={<AddIcon />} onClick={() => setShowAdd(true)} sx={{ mt: 2 }}>
                    Add a trusted publisher
                </Button>
            ) : hasPublishers ? (
                <Typography variant='body2' color='text.secondary' sx={{ mt: 2 }}>
                    {allTakenText}
                </Typography>
            ) : null}
            <RegisterTrustedPublisherDialog
                open={dialogOpen}
                initialProvider={selectedProvider}
                onClose={() => setDialogOpen(false)}
                onRegistered={() => setShowAdd(false)}
                namespaces={[
                    {
                        name: props.namespace,
                        registrableExtensions,
                        registeredExtensions: publishers.map(publisher => publisher.extension),
                        trustedPublishingUrl: props.trustedPublishingUrl
                    }
                ]}
                namespace={props.namespace}
                lockedExtension={props.extensionFilter}
                providers={props.providers}
            />
        </>
    );
};

export const UserNamespaceTrustedPublishers: FunctionComponent<{ namespace: Namespace }> = ({ namespace }) => {
    const { user } = useContext(MainContext);
    const { trustedPublishingUrl } = namespace;
    // the status query doubles as the feature probe: disabled, disallowed or failed → no providers → hidden
    const { data: trustedPublishingStatus } = useTrustedPublishingStatus();
    const providers = trustedPublishingStatus?.trustedPublisherProviders ?? [];
    if (!user || !trustedPublishingUrl || providers.length === 0) {
        return null;
    }
    return (
        <Box sx={{ mb: '2.375rem' }}>
            <SettingsSectionTitle component='h3'>Trusted Publishers</SettingsSectionTitle>
            <TrustedPublishersSection
                namespace={namespace.name}
                trustedPublishingUrl={trustedPublishingUrl}
                providers={providers}
                intro="Let a CI/CD workflow publish this namespace's extensions without a long-lived access token."
                emptyText='No trusted publishers registered yet.'
            />
        </Box>
    );
};

export const ExtensionTrustedPublishers: FunctionComponent<{ namespace: string; extension: string }> = ({
    namespace,
    extension
}) => {
    const { user, service } = useContext(MainContext);
    const trustedPublishingUrl = service.userTrustedPublishingUrl(namespace);
    // the status query doubles as the feature probe: disabled, disallowed or failed → no providers → hidden
    const { data: trustedPublishingStatus } = useTrustedPublishingStatus();
    const providers = trustedPublishingStatus?.trustedPublisherProviders ?? [];
    // The URL is built client-side (unlike Namespace.trustedPublishingUrl it is not owner-granted),
    // so the publishers list doubles as the ownership probe: it 403s unless the user owns the
    // namespace (e.g. an admin inspecting a foreign extension), which keeps the section hidden.
    const { isSuccess: canManage } = useTrustedPublishersProbe(providers.length > 0 ? trustedPublishingUrl : undefined);
    if (!user || providers.length === 0 || !canManage) {
        return null;
    }
    return (
        <Box sx={{ mb: '1.75rem' }}>
            <Eyebrow sx={{ mb: '0.25rem' }}>Trusted Publishers</Eyebrow>
            <TrustedPublishersSection
                namespace={namespace}
                trustedPublishingUrl={trustedPublishingUrl}
                extensionFilter={extension}
                providers={providers}
                intro='Let a CI/CD workflow publish this extension without a long-lived access token.'
                emptyText='No trusted publishers registered for this extension yet.'
            />
        </Box>
    );
};
