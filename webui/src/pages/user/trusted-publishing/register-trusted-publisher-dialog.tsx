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

import { FormEvent, FunctionComponent, ReactNode, useContext, useEffect, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    LinearProgress,
    Link,
    MenuItem,
    TextField,
    Typography
} from '@mui/material';
import { handleError } from '../../../utils';
import { MainContext } from '../../../context';
import { TrustedPublisherProvider, UrlString } from '../../../extension-registry-types';
import { TrustedPublisherProviderIcon } from './trusted-publisher-provider-icon';
import { ProviderKind, findRegistrationPathPair, orderRegistrationKeys, providerKind } from './registration-fields';
import { useRegisterTrustedPublisher } from './use-trusted-publishers';

// hidden when no documentation URL is configured in the page settings
export const TrustedPublishingDocsLink: FunctionComponent = () => {
    const { pageSettings } = useContext(MainContext);
    const url = pageSettings.urls.trustedPublishing;
    if (!url) {
        return null;
    }
    return (
        <Link color='secondary' href={url} target='_blank' rel='noopener'>
            Learn more about trusted publishing
        </Link>
    );
};

// Optional helper text per provider kind and registration key; unlisted fields get none.
const FIELD_HELP: Partial<Record<ProviderKind, Record<string, ReactNode>>> = {
    github: {
        workflow: 'Filename only (e.g. release.yml). Must exist in .github/workflows/ in your repository.',
        repository_id: 'Only for private repositories. Run: gh api repos/OWNER/REPO --jq .id',
        owner_id: 'Only for private repositories. Run: gh api repos/OWNER/REPO --jq .owner.id',
        environment: (
            <>
                The name of the{' '}
                <Link
                    href='https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-deployments/managing-environments-for-deployment'
                    target='_blank'
                    rel='noopener'
                    color='secondary'>
                    GitHub Actions environment
                </Link>{' '}
                used for publishing. This is encouraged for projects with maintainers who should not have publishing
                access.
            </>
        )
    },
    gitlab: {
        workflow: 'CI config file name (e.g. .gitlab-ci.yml).',
        project_id: 'Only for private projects. Shown under Settings > General of the project.',
        namespace_id: 'Only for private projects. Shown under Settings > General of the group.',
        environment: (
            <>
                The name of the{' '}
                <Link
                    href='https://docs.gitlab.com/ee/ci/environments/'
                    target='_blank'
                    rel='noopener'
                    color='secondary'>
                    GitLab CI/CD environment
                </Link>{' '}
                used for publishing. This is encouraged for projects with maintainers who should not have publishing
                access.
            </>
        )
    }
};

const fieldHelp = (providerId: string, key: string): ReactNode => FIELD_HELP[providerKind(providerId)]?.[key];

export interface SelectableNamespace {
    name: string;
    // extensions a trusted publisher can still be registered for, as reported by the server
    registrableExtensions: string[];
    // extensions that already have one; used only to explain an empty selection
    registeredExtensions: string[];
    // base endpoint for this namespace's trusted-publishing requests
    trustedPublishingUrl: UrlString;
}

export interface RegisterTrustedPublisherDialogProps {
    open: boolean;
    onClose: () => void;
    // UI-only callback; the publisher list itself refreshes via query invalidation
    onRegistered?: () => void;
    // namespaces the registration can target, with their selectable extensions
    namespaces: SelectableNamespace[];
    // fixed target namespace (one of `namespaces`); omit to let the user pick one
    namespace?: string;
    // binds the registration to this extension and hides the selector
    lockedExtension?: string;
    initialProvider?: string;
    providers: TrustedPublisherProvider[];
}

export const RegisterTrustedPublisherDialog: FunctionComponent<RegisterTrustedPublisherDialogProps> = props => {
    const { open, initialProvider, lockedExtension } = props;
    const {
        mutateAsync: registerTrustedPublisher,
        isPending: loading,
        error: registerError,
        reset: resetRegistration
    } = useRegisterTrustedPublisher();
    // server-side validation errors (e.g. repository not found) show inside the form
    const errorMessage = registerError ? handleError(registerError) : undefined;

    const [namespaceName, setNamespaceName] = useState('');
    const [provider, setProvider] = useState('');
    const [extension, setExtension] = useState('');
    const [registration, setRegistration] = useState<Record<string, string>>({});

    useEffect(() => {
        if (open) {
            setNamespaceName(props.namespace ?? '');
            setProvider(initialProvider ?? '');
            setExtension(lockedExtension ?? '');
            setRegistration({});
            resetRegistration();
        }
    }, [open, initialProvider, lockedExtension, props.namespace]);

    const namespace = props.namespace ?? namespaceName;
    const selectedNamespace = props.namespaces.find(ns => ns.name === namespace);
    const extensionNames = selectedNamespace?.registrableExtensions ?? [];

    const handleClose = () => {
        if (!loading) {
            props.onClose();
        }
    };

    const inputs = props.providers.find(p => p.id === provider)?.registrationInputs ?? [];
    const inputsByKey = new Map(inputs.map(input => [input.key, input]));
    const orderedKeys = orderRegistrationKeys(inputs.map(input => input.key));
    const requiredKeys = orderedKeys.filter(key => !inputsByKey.get(key)?.optional);
    const pathPair = findRegistrationPathPair(orderedKeys);
    const [pathOwnerKey, pathRepoKey] = pathPair ?? [];
    const standaloneKeys = orderedKeys.filter(key => !pathPair?.includes(key));

    const registrationField = (key: string, flex?: number) => {
        const input = inputsByKey.get(key);
        return (
            <TextField
                key={key}
                required={!input?.optional}
                fullWidth={flex === undefined}
                label={input?.description}
                helperText={fieldHelp(provider, key)}
                value={registration[key] ?? ''}
                onChange={e => setRegistration(prev => ({ ...prev, [key]: e.target.value }))}
                sx={flex !== undefined ? { flex } : undefined}
            />
        );
    };

    const canRegister =
        !loading &&
        namespace !== '' &&
        provider !== '' &&
        extension !== '' &&
        requiredKeys.every(key => (registration[key] ?? '').trim() !== '');

    const handleRegister = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const cleaned: Record<string, string> = {};
        for (const key of orderedKeys) {
            const value = (registration[key] ?? '').trim();
            if (value !== '') {
                cleaned[key] = value;
            }
        }
        const trustedPublishingUrl = selectedNamespace?.trustedPublishingUrl;
        if (!trustedPublishingUrl) {
            return;
        }
        try {
            await registerTrustedPublisher({
                trustedPublishingUrl,
                request: { provider, namespace, extension, registration: cleaned }
            });
            props.onRegistered?.();
            props.onClose();
        } catch {
            // shown inline via the mutation's error state
        }
    };

    // Why nothing can be registered: either everything is taken (one publisher per extension) or
    // there is nothing to bind to. Extensions must exist and be active, which the server enforces.
    const nothingRegistrableText = (): string => {
        if ((selectedNamespace?.registeredExtensions.length ?? 0) === 0) {
            return 'This namespace has no active extensions yet. Publish an extension before registering a trusted publisher.';
        }
        return lockedExtension
            ? 'This extension already has a trusted publisher. Delete the existing registration to replace it.'
            : 'Every active extension in this namespace already has a trusted publisher. Delete an existing registration to replace it.';
    };

    // Provider and registration fields for the chosen namespace; only rendered once one is picked.
    const renderFormBody = (): ReactNode => {
        if (namespace === '') {
            return null;
        }
        if (extensionNames.length === 0) {
            return <DialogContentText color='error'>{nothingRegistrableText()}</DialogContentText>;
        }
        return (
            <>
                <TextField
                    select
                    required
                    fullWidth
                    label='Publisher'
                    value={provider}
                    onChange={e => {
                        setProvider(e.target.value);
                        setRegistration({});
                    }}>
                    {props.providers.map(p => (
                        <MenuItem key={p.id} value={p.id}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                <TrustedPublisherProviderIcon providerId={p.id} fontSize='small' />
                                {p.name}
                            </Box>
                        </MenuItem>
                    ))}
                </TextField>
                {!lockedExtension && (
                    <TextField
                        select
                        required
                        fullWidth
                        label='Extension'
                        value={extension}
                        onChange={e => setExtension(e.target.value)}>
                        {extensionNames.map(name => (
                            <MenuItem key={name} value={name}>
                                {name}
                            </MenuItem>
                        ))}
                    </TextField>
                )}
                {pathOwnerKey && pathRepoKey && (
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                        {registrationField(pathOwnerKey, 1)}
                        <Typography sx={{ mt: 2 }}>/</Typography>
                        {registrationField(pathRepoKey, 2)}
                    </Box>
                )}
                {standaloneKeys.map(key => registrationField(key))}
            </>
        );
    };

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            fullWidth
            maxWidth='sm'
            PaperProps={{ component: 'form', onSubmit: handleRegister }}>
            <DialogTitle>Register trusted publisher</DialogTitle>
            {loading && <LinearProgress color='secondary' />}
            <DialogContent>
                <DialogContentText sx={{ mb: 3 }}>
                    Establish trust between this extension and your repository using OpenID Connect (OIDC), so a CI
                    workflow can publish without a personal access token. Publishing is allowed from any branch or tag.{' '}
                    <TrustedPublishingDocsLink />
                </DialogContentText>
                {errorMessage && (
                    <Alert severity='error' sx={{ mb: 3 }}>
                        {errorMessage}
                    </Alert>
                )}
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    {!props.namespace && (
                        <TextField
                            select
                            required
                            fullWidth
                            label='Namespace'
                            value={namespaceName}
                            onChange={e => {
                                setNamespaceName(e.target.value);
                                setExtension('');
                            }}>
                            {props.namespaces.map(ns => (
                                <MenuItem key={ns.name} value={ns.name}>
                                    {ns.name}
                                </MenuItem>
                            ))}
                        </TextField>
                    )}
                    {renderFormBody()}
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type='submit' variant='contained' color='secondary' disabled={!canRegister}>
                    Register
                </Button>
            </DialogActions>
        </Dialog>
    );
};
