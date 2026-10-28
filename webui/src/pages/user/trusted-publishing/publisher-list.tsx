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
import {
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Divider,
    Paper,
    Stack,
    Typography
} from '@mui/material';
import { styled } from '@mui/material/styles';
import { DeleteIconButton } from '../../../components/delete-icon-button';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { Timestamp } from '../../../components/timestamp';
import { TrustedPublisher, TrustedPublisherProvider } from '../../../extension-registry-types';
import { TrustedPublisherProviderIcon } from './trusted-publisher-provider-icon';
import { findRegistrationPathPair, orderRegistrationKeys } from './registration-fields';

const PublisherRow = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(2),
    padding: theme.spacing(2)
}));

// Explicit provider IDs (private repositories), shown next to the path so a mistyped one is visible.
const ID_KEYS: Record<string, readonly [idKey: string, ownerIdKey: string]> = {
    repo: ['repository_id', 'owner_id'],
    project: ['project_id', 'namespace_id']
};

// e.g. "octocat/my-repo (id 42, owner id 7) · workflow: release.yml · environment: prod"
const registrationSummary = (registration: { [key: string]: string }): string => {
    const reg = registration ?? {};
    const keys = orderRegistrationKeys(Object.keys(reg)).filter(key => reg[key]?.trim());
    const pair = findRegistrationPathPair(keys);
    const idKeys = pair ? ID_KEYS[pair[1]] : undefined;
    const ids = idKeys?.filter(key => keys.includes(key)) ?? [];
    let path = pair?.map(key => reg[key]).join('/');
    if (path && ids.length) {
        path += ` (${ids.map(key => `${key === idKeys?.[0] ? 'id' : 'owner id'} ${reg[key]}`).join(', ')})`;
    }
    const fields = keys.filter(key => !pair?.includes(key) && !ids.includes(key)).map(key => `${key}: ${reg[key]}`);
    return [path, ...fields].filter(Boolean).join(' · ');
};

export interface PublisherListProps {
    publishers: TrustedPublisher[];
    providers: TrustedPublisherProvider[];
    loading: boolean;
    // shown when the list is empty; omit when the caller renders its own empty state
    emptyText?: string;
    /**
     * What identifies a row next to the provider name: the extension name
     * (default), namespace/extension when rows span several namespaces, or
     * nothing when every row is for the same extension.
     */
    rowDetail?: 'extension' | 'namespace/extension' | 'none';
    onDelete: (publisher: TrustedPublisher) => void | Promise<void>;
}

export const PublisherList: FunctionComponent<PublisherListProps> = props => {
    const rowDetail = props.rowDetail ?? 'extension';
    const [deleteTarget, setDeleteTarget] = useState<TrustedPublisher>();

    const providerName = (id: string): string => props.providers.find(p => p.id === id)?.name ?? id;

    const rowDetailLabel = (publisher: TrustedPublisher): string | undefined => {
        switch (rowDetail) {
            case 'namespace/extension':
                return `${publisher.namespace}/${publisher.extension}`;
            case 'extension':
                return publisher.extension;
            default:
                return undefined;
        }
    };

    const confirmDelete = async () => {
        if (!deleteTarget) {
            return;
        }
        await props.onDelete(deleteTarget);
        setDeleteTarget(undefined);
    };

    return (
        <>
            <DelayedLoadIndicator loading={props.loading} />
            {props.publishers.length ? (
                <Paper variant='outlined'>
                    <Stack divider={<Divider />}>
                        {props.publishers.map(publisher => {
                            const summary = registrationSummary(publisher.registration);
                            const detail = rowDetailLabel(publisher);
                            return (
                                <PublisherRow key={'tp-' + publisher.id}>
                                    <TrustedPublisherProviderIcon
                                        providerId={publisher.provider}
                                        fontSize='large'
                                        sx={{ color: 'text.secondary' }}
                                    />
                                    <Box sx={{ flex: 1, minWidth: 0 }}>
                                        <Typography sx={{ fontWeight: 'bold' }}>
                                            {providerName(publisher.provider)}
                                            {detail ? <> &middot; {detail}</> : null}
                                        </Typography>
                                        {summary ? (
                                            <Typography
                                                variant='body2'
                                                color='text.secondary'
                                                sx={{ wordBreak: 'break-word' }}>
                                                {summary}
                                            </Typography>
                                        ) : null}
                                        {publisher.createdTimestamp ? (
                                            <Typography variant='body2' color='text.secondary'>
                                                Created: <Timestamp value={publisher.createdTimestamp} />
                                            </Typography>
                                        ) : null}
                                    </Box>
                                    <DeleteIconButton
                                        onClick={() => setDeleteTarget(publisher)}
                                        disabled={props.loading}
                                        aria-label='Delete trusted publisher'
                                    />
                                </PublisherRow>
                            );
                        })}
                    </Stack>
                </Paper>
            ) : props.emptyText ? (
                <Typography variant='body1'>{props.emptyText}</Typography>
            ) : null}
            <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(undefined)}>
                <DialogTitle>Delete trusted publisher</DialogTitle>
                <DialogContent>
                    <DialogContentText component='div'>
                        Are you sure you want to delete this trusted publisher? Workflows relying on it will no longer
                        be able to publish.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDeleteTarget(undefined)}>Cancel</Button>
                    <Button variant='contained' color='error' autoFocus onClick={confirmDelete}>
                        Delete
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
