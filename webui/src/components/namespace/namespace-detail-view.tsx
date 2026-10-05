/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode } from 'react';
import { Link as RouteLink } from 'react-router';
import { Box, Button, Typography } from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { FetchNamespaceExtension, NamespaceExtensionList } from './namespace-extension-list';
import { NamespaceDetailRoutes } from '../../pages/namespace-detail/namespace-detail-routes';
import { NamespaceClaimNotice } from './namespace-claim-notice';
import { createRoute } from '../../utils';
import { Namespace } from '../../extension-registry-types';
import { NamespaceMemberList } from './namespace-member-list';
import { NamespaceDetailsForm } from './namespace-details-form';
import { NamespaceLogo } from './namespace-logo';
import { MediaSidebarLayout } from '../media-sidebar-layout';
import { UserNamespaceTrustedPublishers } from '../../pages/user/trusted-publishing/trusted-publishers-section';
import { VerifiedBadge } from '../verified-badge';
import { MONO_FONT } from '../../default/theme';

/**
 * Reusable view of a namespace: header, details form, member list, trusted publishers and extension
 * list, with the logo in the media sidebar. Page-specific concerns are injected — `headerActions` for
 * extra header buttons (e.g. the admin change/delete actions), `extensionRoutePrefix` and
 * `fetchExtension` for where the extension cards link and which endpoint loads them. Member roles and
 * the add-member search are configured through {@link NamespaceDetailConfigContext}.
 */
export const NamespaceDetailView: FunctionComponent<NamespaceDetailViewProps> = props => {
    return (
        <>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '1rem',
                    flexWrap: 'wrap',
                    mb: '1.875rem'
                }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.75rem', minWidth: 0 }}>
                    <Typography
                        component='h2'
                        noWrap
                        sx={{
                            fontFamily: MONO_FONT,
                            fontSize: '1.75rem',
                            fontWeight: 800,
                            letterSpacing: '-0.02em',
                            minWidth: 0
                        }}>
                        {props.namespace.name}
                    </Typography>
                    {props.namespace.verified ? (
                        <VerifiedBadge title='Verified namespace' sx={{ fontSize: '1.1875rem' }} />
                    ) : null}
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.5625rem', flexWrap: 'wrap' }}>
                    {props.headerActions}
                    <Button
                        variant='outlined'
                        component={RouteLink}
                        to={createRoute([NamespaceDetailRoutes.ROOT, props.namespace.name])}
                        target='_blank'
                        rel='noopener'
                        startIcon={<OpenInNewIcon />}>
                        View public page
                    </Button>
                </Box>
            </Box>
            {!props.namespace.verified ? (
                <NamespaceClaimNotice
                    namespace={props.namespace.name}
                    showAction={props.showClaimAction}
                    sx={{ mb: '1.875rem' }}>
                    Claiming{' '}
                    <Typography component='code' sx={{ fontFamily: MONO_FONT, fontSize: 'inherit' }}>
                        {props.namespace.name}
                    </Typography>{' '}
                    proves you own it. Until then extensions published here can stay inactive.
                </NamespaceClaimNotice>
            ) : null}
            {/* All sections share the main column, the logo stands alone on the right. */}
            <MediaSidebarLayout sidebar={<NamespaceLogo namespace={props.namespace} />}>
                {props.namespace.detailsUrl ? (
                    <Box sx={{ mb: '2.375rem' }}>
                        <NamespaceDetailsForm namespace={props.namespace} />
                    </Box>
                ) : null}
                {props.namespace.membersUrl ? (
                    <Box sx={{ mb: '2.375rem' }}>
                        <NamespaceMemberList setLoadingState={props.setLoadingState} namespace={props.namespace} />
                    </Box>
                ) : null}
                <UserNamespaceTrustedPublishers namespace={props.namespace} />
                <NamespaceExtensionList
                    namespace={props.namespace}
                    routePrefix={props.extensionRoutePrefix}
                    fetchExtension={props.fetchExtension}
                    omitUnavailable={props.omitUnavailable}
                />
            </MediaSidebarLayout>
        </>
    );
};

export interface NamespaceDetailViewProps {
    namespace: Namespace;
    setLoadingState: (loading: boolean) => void;
    // Extra actions rendered in the header, before the public-page link (e.g. the admin
    // "Change Namespace"/"Delete" buttons). Supplied by the host page so this view stays
    // free of page-specific concerns.
    headerActions?: ReactNode;
    // Base route each extension card links to: the user settings extension route on the user
    // surface, the admin extension route on the admin dashboard.
    extensionRoutePrefix: string;
    // Endpoint used to retrieve each extension's detail; forwarded to the extension list. Defaults
    // to the public registry API when omitted. The admin surface passes the admin endpoint so
    // inactive/soft-deleted extensions show up too.
    fetchExtension?: FetchNamespaceExtension;
    // Forwarded to the extension list: leave out what the fetch cannot resolve rather than
    // reporting it, for an admin namespace listed through the public API.
    omitUnavailable?: boolean;
    // Claiming is the publisher's action, so only the user surface offers it; the admin dashboard
    // shows the same explanation without it.
    showClaimAction?: boolean;
}
