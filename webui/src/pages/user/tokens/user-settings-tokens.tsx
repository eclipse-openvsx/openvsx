/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useEffect, useState, useRef } from 'react';
import {
    Alert,
    AlertTitle,
    Typography,
    Box,
    Button,
    Link,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogContentText,
    DialogActions
} from '@mui/material';
import { styled } from '@mui/material/styles';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';
import { Link as RouteLink } from 'react-router';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { Timestamp } from '../../../components/timestamp';
import { PersonalAccessToken } from '../../../extension-registry-types';
import { MainContext } from '../../../context';
import { DeleteIconButton } from '../../../components/delete-icon-button';
import { DetailsCard } from '../../../components/details-card';
import { GenerateAccessTokenDialog } from './generate-access-token-dialog';
import { TrustedPublishingPromo } from '../trusted-publishing/trusted-publishing-promo';
import { EmptyPlaceholder, IconTile } from '../settings/settings-primitives';
import { SettingsHeader } from '../settings/settings-header';
import { UserSettingsRoutes } from '../user-settings-routes';

// What a pending confirmation refers to: one token, or every token at once.
type DeleteTarget = PersonalAccessToken | 'all';

/** Confirmation copy for the pending deletion. */
const deletePrompt = (target: DeleteTarget) =>
    target === 'all'
        ? {
              title: 'Delete all access tokens',
              message: 'Are you sure you want to delete all access tokens?',
              confirmLabel: 'Delete'
          }
        : {
              title: 'Revoke access token',
              message: `Revoking ${target.description ? `“${target.description}”` : 'this token'} cannot be undone. Any command line or CI workflow still using it will fail to publish until you replace it.`,
              confirmLabel: 'Revoke'
          };

const TokenRow = styled(Box)({
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    padding: '1.125rem 1.25rem'
});

export const UserSettingsTokens: FunctionComponent = () => {
    const { service, user, handleError, pageSettings } = useContext(MainContext);

    const [tokens, setTokens] = useState(new Array<PersonalAccessToken>());
    const [loading, setLoading] = useState(true);
    const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>();

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        updateTokens();
        return () => {
            abortController.current.abort();
        };
    }, []);

    const updateTokens = async () => {
        if (!user) {
            return;
        }
        try {
            const tokens = await service.getAccessTokens(abortController.current, user);
            setTokens(tokens);
            setLoading(false);
        } catch (err) {
            handleError(err);
            setLoading(false);
        }
    };

    const confirmDelete = async (target: DeleteTarget) => {
        setDeleteTarget(undefined);
        setLoading(true);
        try {
            if (target === 'all') {
                await service.deleteAllAccessTokens(abortController.current, tokens);
            } else {
                await service.deleteAccessToken(abortController.current, target);
            }
            updateTokens();
        } catch (err) {
            handleError(err);
        }
    };

    const handleTokenGenerated = () => {
        setLoading(true);
        updateTokens();
    };

    const renderToken = (token: PersonalAccessToken): ReactNode => {
        return (
            <TokenRow key={'token:' + token.id}>
                <IconTile sx={{ width: '2.25rem', height: '2.25rem' }}>
                    <KeyOutlinedIcon sx={{ fontSize: '1.0625rem' }} />
                </IconTile>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography
                        noWrap
                        sx={{
                            fontSize: '0.90625rem',
                            fontWeight: 600
                        }}>
                        {token.description}
                    </Typography>
                    {token.scopeNamespace || token.publishingOnly ? (
                        <Typography sx={{ fontSize: '0.78125rem', color: 'text.disabled', mt: '0.125rem' }}>
                            {[
                                token.scopeNamespace
                                    ? `Scope: ${token.scopeNamespace}${token.scopeExtension ? `.${token.scopeExtension}` : ''}`
                                    : undefined,
                                token.publishingOnly ? 'Publishing only' : undefined
                            ]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                    ) : null}
                    <Typography sx={{ fontSize: '0.78125rem', color: 'text.disabled', mt: '0.125rem' }}>
                        Expires{' '}
                        {token.expiresTimestamp ? (
                            <Timestamp value={token.expiresTimestamp} isFutureTime={true} />
                        ) : (
                            'never'
                        )}
                    </Typography>
                    <Typography sx={{ fontSize: '0.75rem', color: 'text.disabled', mt: '0.125rem' }}>
                        Created <Timestamp value={token.createdTimestamp} />
                        {/* Only stamped by the server once the token has been used to publish. */}
                        {token.accessedTimestamp ? (
                            <>
                                {' '}
                                &middot; Last used <Timestamp value={token.accessedTimestamp} />
                            </>
                        ) : null}
                    </Typography>
                </Box>
                <DeleteIconButton
                    title='Revoke token'
                    aria-label={token.description ? `Revoke token ${token.description}` : 'Revoke token'}
                    sx={{ flexShrink: 0 }}
                    onClick={() => setDeleteTarget(token)}
                    disabled={loading}
                />
            </TokenRow>
        );
    };

    const agreement = user?.publisherAgreement;
    if (agreement && (agreement.status === 'none' || agreement.status === 'outdated')) {
        const publisherAgreementContact = pageSettings?.publisherAgreement?.email;
        // The deployment may not name its agreement, so drop the empty slot rather than double-spacing.
        const agreementName = [pageSettings?.publisherAgreement?.name, 'Publisher Agreement'].filter(Boolean).join(' ');

        return (
            <Box>
                <SettingsHeader
                    title='Access Tokens'
                    description='Personal access tokens let you publish extensions from the command line. Treat them like passwords.'
                />
                <Alert severity='warning'>
                    <AlertTitle sx={{ fontWeight: 700 }}>Publisher agreement required</AlertTitle>
                    Tokens can only be created once you have signed the {agreementName}.
                    <Box sx={{ mt: '0.75rem' }}>
                        <Link component={RouteLink} to={UserSettingsRoutes.PROFILE}>
                            Sign it on your profile
                            <ArrowForwardIcon sx={{ fontSize: '1.125rem' }} />
                        </Link>
                    </Box>
                    {publisherAgreementContact !== undefined && (
                        <Box sx={{ mt: '0.75rem', fontSize: '0.8125rem' }}>
                            If you believe this is in error, contact{' '}
                            <Link href={`mailto:${publisherAgreementContact}`}>{publisherAgreementContact}</Link>.
                        </Box>
                    )}
                </Alert>
            </Box>
        );
    }

    return (
        <Box>
            <SettingsHeader
                title='Access Tokens'
                description='Personal access tokens let you publish extensions from the command line. Treat them like passwords.'
                actions={
                    <>
                        <Button
                            variant='outlined'
                            color='error'
                            onClick={() => setDeleteTarget('all')}
                            disabled={loading || tokens.length === 0}>
                            Delete all
                        </Button>
                        <GenerateAccessTokenDialog handleTokenGenerated={handleTokenGenerated} />
                    </>
                }
            />
            <Box mb='1.375rem'>
                <TrustedPublishingPromo />
            </Box>
            <DelayedLoadIndicator loading={loading} />
            {tokens.length > 0 ? (
                <DetailsCard>{tokens.map(token => renderToken(token))}</DetailsCard>
            ) : !loading ? (
                <EmptyPlaceholder>You currently have no tokens.</EmptyPlaceholder>
            ) : null}
            {deleteTarget ? (
                <DeleteTokenDialog
                    target={deleteTarget}
                    onClose={() => setDeleteTarget(undefined)}
                    onConfirm={() => confirmDelete(deleteTarget)}
                />
            ) : null}
        </Box>
    );
};

const DeleteTokenDialog: FunctionComponent<DeleteTokenDialogProps> = ({ target, onClose, onConfirm }) => {
    const { title, message, confirmLabel } = deletePrompt(target);
    return (
        <Dialog open={true} onClose={onClose}>
            <DialogTitle>{title}</DialogTitle>
            <DialogContent>
                <DialogContentText component='div'>{message}</DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button variant='contained' color='error' autoFocus onClick={onConfirm}>
                    {confirmLabel}
                </Button>
            </DialogActions>
        </Dialog>
    );
};

interface DeleteTokenDialogProps {
    target: DeleteTarget;
    onClose: () => void;
    onConfirm: () => void;
}
