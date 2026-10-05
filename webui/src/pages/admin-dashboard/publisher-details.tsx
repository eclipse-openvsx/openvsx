/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext } from 'react';
import { Alert, Avatar, Box, Chip, Divider, LinearProgress, Paper, Stack, Tooltip, Typography } from '@mui/material';
import { useIsMutating } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router';
import GitHubIcon from '@mui/icons-material/GitHub';
import PersonIcon from '@mui/icons-material/Person';
import FolderSharedIcon from '@mui/icons-material/FolderShared';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import ExtensionIcon from '@mui/icons-material/Extension';
import GavelIcon from '@mui/icons-material/Gavel';
import { UserRelationships } from '../../extension-registry-types';
import { ErrorResponse } from '../../server-request';
import { MainContext } from '../../context';
import { ExtensionCardList } from '../../components/extension/extension-card-list';
import { createRoute, handleError as formatError, toLocalTime } from '../../utils';
import { hasPermission } from '../../permissions';
import { ExtensionDetailRoutes } from '../extension-detail/extension-detail-routes';
import { NamespaceDetailRoutes } from '../namespace-detail/namespace-detail-routes';
import { AdminDashboardRoutes } from './admin-dashboard-routes';
import { PublisherForgetUserButton } from './publisher-forget-user-button';
import { PublisherRevokeContributionsButton } from './publisher-revoke-dialog';
import { PublisherRevokeTokensButton } from './publisher-revoke-tokens-button';
import { publisherMutationKey, usePublisherInfo } from './use-publisher-admin';

const AGREEMENT_META = {
    signed: { label: 'Signed', color: 'success' as const },
    outdated: { label: 'Outdated', color: 'warning' as const },
    none: { label: 'Not signed', color: 'default' as const }
};

const DetailSection: FunctionComponent<{ icon: ReactNode; title: string; count?: number; children: ReactNode }> = ({
    icon,
    title,
    count,
    children
}) => (
    <Box>
        <Stack direction='row' spacing={0.75} alignItems='center' sx={{ mb: 1 }}>
            {icon}
            <Typography variant='subtitle2'>{title}</Typography>
            {count !== undefined && (
                <Typography variant='caption' color='text.secondary'>
                    ({count})
                </Typography>
            )}
        </Stack>
        {children}
    </Box>
);

/**
 * The details card for the publisher selected in the search. Identity and role are
 * available immediately from the search result; the account info (agreement, tokens,
 * extensions) loads on demand. A top progress bar reflects any write in flight.
 */
export const PublisherDetails: FunctionComponent<{ entry: UserRelationships }> = ({ entry }) => {
    const { user } = entry;
    const { user: currentUser } = useContext(MainContext);
    const isCurrentUser = currentUser?.loginName === user.loginName && currentUser?.provider === user.provider;

    const busy = useIsMutating({ mutationKey: publisherMutationKey }) > 0;

    const { data: publisherInfo, error } = usePublisherInfo(user.loginName, user.provider ?? 'github', true);

    // A publisher's namespaces and extensions are legitimately on show here, but the admin pages for
    // them need their own permissions - so without those, link to the public pages rather than to a
    // page that would only answer "not authorized".
    const namespaceRoute = (name: string) =>
        hasPermission(currentUser, 'manage_namespaces')
            ? `${AdminDashboardRoutes.NAMESPACE_ADMIN}/${encodeURIComponent(name)}`
            : createRoute([NamespaceDetailRoutes.ROOT, name]);
    const extensionRoutePrefix = hasPermission(currentUser, 'manage_extensions')
        ? AdminDashboardRoutes.EXTENSION_ADMIN
        : createRoute([ExtensionDetailRoutes.ROOT]);

    const agreementStatus = publisherInfo?.user.publisherAgreement?.status ?? 'none';
    const agreement = AGREEMENT_META[agreementStatus];

    return (
        <Paper
            variant='outlined'
            sx={{
                position: 'relative',
                overflow: 'hidden',
                p: { xs: 2, md: 3 },
                flex: 1,
                display: 'flex',
                flexDirection: 'column'
            }}>
            {busy && <LinearProgress color='secondary' sx={{ position: 'absolute', top: 0, left: 0, right: 0 }} />}

            <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                sx={{ minWidth: 0, alignItems: { md: 'center' } }}>
                <Avatar variant='rounded' src={user.avatarUrl} sx={{ width: 56, height: 56 }} />
                <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Stack direction='row' spacing={1} alignItems='center' useFlexGap sx={{ flexWrap: 'wrap' }}>
                        <Typography variant='h6' noWrap>
                            {user.loginName}
                        </Typography>
                        <Chip
                            icon={
                                user.provider === 'github' ? (
                                    <GitHubIcon fontSize='small' />
                                ) : (
                                    <PersonIcon fontSize='small' />
                                )
                            }
                            label={user.provider ?? '—'}
                            size='small'
                            variant='outlined'
                        />
                        {isCurrentUser && (
                            <Chip
                                label='you'
                                size='small'
                                color='info'
                                variant='outlined'
                                sx={{ height: 18, '& .MuiChip-label': { px: 0.5, fontSize: '0.65rem' } }}
                            />
                        )}
                        {/* Read-only here - role and permissions are edited on the Access Control page. */}
                        {user.role && <Chip label={user.role} size='small' variant='outlined' />}
                    </Stack>
                    <Typography variant='body2' color='text.secondary' noWrap>
                        {user.fullName || '—'}
                    </Typography>
                </Box>
            </Stack>

            <Divider sx={{ my: 3 }} />

            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 3 }}>
                <DetailSection
                    icon={<FolderSharedIcon fontSize='small' color='action' />}
                    title='Namespaces'
                    count={entry.namespaces.length}>
                    {entry.namespaces.length > 0 ? (
                        <Stack direction='row' spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                            {entry.namespaces.map(ns => (
                                <Chip
                                    key={ns.name}
                                    label={ns.name}
                                    size='small'
                                    component={RouterLink}
                                    to={namespaceRoute(ns.name)}
                                    clickable
                                />
                            ))}
                        </Stack>
                    ) : (
                        <Typography variant='body2' color='text.secondary'>
                            None
                        </Typography>
                    )}
                </DetailSection>

                {error && <Alert severity='error'>{formatError(error as Error | Partial<ErrorResponse>)}</Alert>}

                {publisherInfo && (
                    <>
                        <Stack direction='row' spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                            {publisherInfo.user.publisherAgreement && (
                                <Tooltip title={toLocalTime(publisherInfo.user.publisherAgreement?.timestamp) ?? ''}>
                                    <Chip
                                        icon={<GavelIcon fontSize='small' />}
                                        label={`Publisher agreement: ${agreement.label}`}
                                        size='small'
                                        color={agreement.color}
                                        variant={agreementStatus === 'none' ? 'outlined' : 'filled'}
                                    />
                                </Tooltip>
                            )}
                            <Chip
                                icon={<VpnKeyIcon fontSize='small' />}
                                label={`${publisherInfo.activeAccessTokenNum} active access token${
                                    publisherInfo.activeAccessTokenNum === 1 ? '' : 's'
                                }`}
                                size='small'
                                variant='outlined'
                            />
                        </Stack>

                        <DetailSection
                            icon={<ExtensionIcon fontSize='small' color='action' />}
                            title='Published extensions'
                            count={publisherInfo.extensions.length}>
                            {publisherInfo.extensions.length > 0 ? (
                                <ExtensionCardList
                                    extensions={publisherInfo.extensions}
                                    loading={false}
                                    routePrefix={extensionRoutePrefix}
                                />
                            ) : (
                                <Typography variant='body2' color='text.secondary'>
                                    This user has not published any extensions.
                                </Typography>
                            )}
                        </DetailSection>

                        <Box sx={{ mt: 'auto' }}>
                            <Typography variant='h6' sx={{ mb: 1.5 }}>
                                Danger Zone
                            </Typography>
                            <Box
                                sx={{
                                    border: 1,
                                    borderColor: 'error.light',
                                    borderRadius: 1,
                                    overflow: 'hidden'
                                }}>
                                {publisherInfo.activeAccessTokenNum > 0 && (
                                    <>
                                        <Stack
                                            direction='row'
                                            alignItems='center'
                                            justifyContent='space-between'
                                            sx={{ px: 2, py: 1.5 }}>
                                            <Box>
                                                <Typography variant='body2' fontWeight={600}>
                                                    Revoke access tokens
                                                </Typography>
                                                <Typography variant='body2' color='text.secondary'>
                                                    Deactivate {publisherInfo.activeAccessTokenNum} active access token
                                                    {publisherInfo.activeAccessTokenNum === 1 ? '' : 's'} for{' '}
                                                    {user.loginName}. This cannot be undone.
                                                </Typography>
                                            </Box>
                                            <Box sx={{ flexShrink: 0, ml: 2 }}>
                                                <PublisherRevokeTokensButton publisherInfo={publisherInfo} />
                                            </Box>
                                        </Stack>
                                        <Divider />
                                    </>
                                )}
                                <Stack
                                    direction='row'
                                    alignItems='center'
                                    justifyContent='space-between'
                                    sx={{ px: 2, py: 1.5 }}>
                                    <Box>
                                        <Typography variant='body2' fontWeight={600}>
                                            Revoke publisher contributions
                                        </Typography>
                                        <Typography variant='body2' color='text.secondary'>
                                            Deactivate all extensions, access tokens, and revoke the publisher agreement
                                            for {user.loginName}. This cannot be undone.
                                        </Typography>
                                    </Box>
                                    <Box sx={{ flexShrink: 0, ml: 2 }}>
                                        <PublisherRevokeContributionsButton publisherInfo={publisherInfo} />
                                    </Box>
                                </Stack>
                                <Divider />
                                <Stack
                                    direction='row'
                                    alignItems='center'
                                    justifyContent='space-between'
                                    sx={{ px: 2, py: 1.5 }}>
                                    <Box>
                                        <Typography variant='body2' fontWeight={600}>
                                            Forget user
                                        </Typography>
                                        <Typography variant='body2' color='text.secondary'>
                                            Erase {user.loginName} in response to a data-protection erasure request.
                                            This cannot be undone.
                                        </Typography>
                                    </Box>
                                    <Box sx={{ flexShrink: 0, ml: 2 }}>
                                        <PublisherForgetUserButton publisherInfo={publisherInfo} />
                                    </Box>
                                </Stack>
                            </Box>
                        </Box>
                    </>
                )}
            </Box>
        </Paper>
    );
};
