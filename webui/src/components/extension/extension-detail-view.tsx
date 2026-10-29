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

import { FunctionComponent, ReactNode, useEffect, useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import { Extension, VERSION_ALIASES, VersionTargetPlatforms } from '../../extension-registry-types';
import { ExtensionHeader } from './extension-header';
import { ExtensionVersionTable } from './extension-version-table';
import { DeleteVersionDialog, VersionDeleteTarget } from './extension-version-delete-dialog';
import { DeleteAllVersionsDialog } from './extension-delete-all-versions-dialog';
import { ExtensionTrustedPublishers } from '../../pages/user/trusted-publishing/trusted-publishers-section';
import { useTrustedPublishingStatus } from '../../pages/user/trusted-publishing/use-trusted-publishers';
import { Eyebrow } from '../page-primitives';
import { DetailRow, DetailsCard } from '../details-card';
import { getExtensionStatus } from './extension-status';
import { formatCompactNumber } from '../../utils';
import { MONO_FONT } from '../../default/theme';
import { NamespaceClaimNotice } from '../namespace/namespace-claim-notice';

// One key/value row per line; on wide screens three cells per row with hairline separators.
const GeneralGrid = styled(DetailsCard)(({ theme }) => ({
    display: 'grid',
    gridTemplateColumns: '1fr',
    marginBottom: '1.75rem',
    [theme.breakpoints.up('lg')]: {
        gridTemplateColumns: 'repeat(3, 1fr)',
        '& > *': { borderBottom: 0 },
        '& > *:nth-of-type(-n+3)': { borderBottom: `1px solid ${theme.palette.border2}` },
        '& > *:not(:nth-of-type(3n))': { borderRight: `1px solid ${theme.palette.border2}` }
    }
}));

/** Bordered panel grouping destructive actions ("danger zone"). */
const DangerZonePanel = styled(Box)(({ theme }) => ({
    border: `1px solid ${alpha(theme.palette.error.main, 0.4)}`,
    borderRadius: theme.shape.borderRadiusCard,
    overflow: 'hidden',
    '& > *': { borderBottom: `1px solid ${alpha(theme.palette.error.main, 0.25)}` },
    '& > *:last-child': { borderBottom: 0 }
}));

const DangerRow = styled(Box)({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '1rem',
    flexWrap: 'wrap',
    padding: '1rem 1.25rem'
});

export const ExtensionDetailView: FunctionComponent<ExtensionDetailViewProps> = props => {
    const { extension, actions, onRemoveVersion, onVersionDeleted, onPurgeVersion } = props;
    const canPurge = !!onPurgeVersion;

    const [page, setPage] = useState(0);
    const [deleteDialogVersion, setDeleteDialogVersion] = useState<VersionTargetPlatforms | null>(null);
    const [purgeDialogVersion, setPurgeDialogVersion] = useState<VersionTargetPlatforms | null>(null);
    const [deleteAllOpen, setDeleteAllOpen] = useState(false);
    const [purgeAllOpen, setPurgeAllOpen] = useState(false);
    const { data: trustedPublishingStatus } = useTrustedPublishingStatus();

    useEffect(() => {
        setPage(0);
    }, [extension]);

    const allVersions = (extension.allTargetPlatformVersions ?? []).filter(v => !VERSION_ALIASES.includes(v.version));
    // Versions the current user is allowed to delete. canDelete is only populated in the user settings
    // view (undefined means unrestricted, e.g. admin/purge); a non-owner member may only delete the
    // versions they published themselves. "Delete all versions" therefore operates on this subset.
    const deletableVersions = canPurge ? allVersions : allVersions.filter(v => v.canDelete !== false);
    // A version can still be (soft-)deleted while it has at least one target platform that is not removed.
    const hasDeletableVersions = deletableVersions.some(v => v.targetPlatforms.some(tp => !tp.removed));

    // Single most relevant publishing state, shown with a colored dot in the general card.
    const status = getExtensionStatus(extension) ?? { label: 'Public', color: 'success.main' };
    // "Latest" describes what the registry actually serves, so an extension the registry is holding
    // back — inactive, or removed — has no latest version to mark.
    const publishedLatest = extension.publishedLatestVersion;
    const localLatest = allVersions.find(
        v => v.version === publishedLatest && v.targetPlatforms.some(tp => tp.active && !tp.removed)
    );

    return (
        <Box>
            <ExtensionHeader extension={extension} actions={actions} />
            {extension.namespaceOwnershipConflict && (
                <NamespaceClaimNotice
                    namespace={extension.namespace}
                    extension={extension}
                    // Claiming is the publisher's action to take, not an admin's on someone else's behalf.
                    showAction={!canPurge}
                    sx={{ mb: '1.75rem' }}>
                    <Typography component='code' sx={{ fontFamily: MONO_FONT, fontSize: 'inherit' }}>
                        {extension.namespace}
                    </Typography>{' '}
                    already exists in another gallery, so this extension stays inactive until the namespace is claimed
                    and ownership verified.
                </NamespaceClaimNotice>
            )}
            <Eyebrow sx={{ mb: '0.75rem' }}>General</Eyebrow>
            <GeneralGrid>
                <DetailRow label='Namespace' mono noWrap>
                    {extension.namespace}
                </DetailRow>
                <DetailRow label='Latest version' mono noWrap>
                    {publishedLatest ?? '—'}
                    {publishedLatest && !localLatest ? ' (upstream)' : ''}
                </DetailRow>
                <DetailRow label='Category' noWrap>
                    {extension.categories?.join(', ') || '—'}
                </DetailRow>
                <DetailRow label='License' noWrap>
                    {extension.license ?? '—'}
                </DetailRow>
                <DetailRow label='Status' noWrap>
                    <Box component='span' sx={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem' }}>
                        <Box
                            component='span'
                            sx={{
                                width: '0.4375rem',
                                height: '0.4375rem',
                                borderRadius: '50%',
                                bgcolor: status.color,
                                flexShrink: 0
                            }}
                        />
                        {status.label}
                    </Box>
                </DetailRow>
                <DetailRow label='Total downloads' mono noWrap>
                    {formatCompactNumber(extension.downloadCount ?? 0)}
                </DetailRow>
            </GeneralGrid>
            {trustedPublishingStatus?.enabled ? (
                <ExtensionTrustedPublishers namespace={extension.namespace} extension={extension.name} />
            ) : null}
            <Box sx={{ mb: '0.75rem' }}>
                <Eyebrow sx={{ mb: '0.25rem' }}>Published versions</Eyebrow>
                <Typography sx={{ fontSize: '0.84375rem', color: 'text.disabled' }}>
                    Every version published to the registry. Deleting a version permanently removes its files from
                    storage.
                </Typography>
            </Box>
            <ExtensionVersionTable
                versions={allVersions}
                latestVersion={localLatest?.version}
                rejected={extension.reviewStatus === 'rejected'}
                page={page}
                onPageChange={setPage}
                onDeleteVersion={setDeleteDialogVersion}
                onPurgeVersion={canPurge ? setPurgeDialogVersion : undefined}
            />
            {allVersions.length > 0 && (
                <Box sx={{ mt: '3.25rem' }}>
                    <Typography
                        component='h2'
                        sx={{
                            fontSize: '1.1875rem',
                            fontWeight: 800,
                            letterSpacing: '-0.01em',
                            color: 'error.main',
                            mb: '0.75rem'
                        }}>
                        Danger Zone
                    </Typography>
                    <DangerZonePanel>
                        <DangerRow>
                            <Box sx={{ minWidth: 0 }}>
                                <Typography sx={{ fontSize: '0.90625rem', fontWeight: 700, mb: '0.125rem' }}>
                                    Delete all versions
                                </Typography>
                                <Typography sx={{ fontSize: '0.8125rem', color: 'text.disabled' }}>
                                    Remove every published version of this extension. This cannot be undone.
                                </Typography>
                            </Box>
                            <Button
                                variant='outlined'
                                color='error'
                                sx={{ flexShrink: 0 }}
                                disabled={!hasDeletableVersions}
                                onClick={() => setDeleteAllOpen(true)}>
                                Delete all versions
                            </Button>
                        </DangerRow>
                        {canPurge && (
                            <DangerRow>
                                <Box sx={{ minWidth: 0 }}>
                                    <Typography sx={{ fontSize: '0.90625rem', fontWeight: 700, mb: '0.125rem' }}>
                                        Purge all versions
                                    </Typography>
                                    <Typography sx={{ fontSize: '0.8125rem', color: 'text.disabled' }}>
                                        Erase every version, including already removed ones, so the extension can be
                                        published again.
                                    </Typography>
                                </Box>
                                <Button
                                    variant='contained'
                                    color='error'
                                    sx={{ flexShrink: 0 }}
                                    onClick={() => setPurgeAllOpen(true)}>
                                    Purge all versions
                                </Button>
                            </DangerRow>
                        )}
                    </DangerZonePanel>
                </Box>
            )}
            {deleteDialogVersion && (
                <DeleteVersionDialog
                    open={true}
                    onClose={() => setDeleteDialogVersion(null)}
                    extension={extension}
                    version={deleteDialogVersion}
                    onRemove={onRemoveVersion}
                    onDeleted={onVersionDeleted}
                />
            )}
            {purgeDialogVersion && onPurgeVersion && (
                <DeleteVersionDialog
                    open={true}
                    mode='purge'
                    onClose={() => setPurgeDialogVersion(null)}
                    extension={extension}
                    version={purgeDialogVersion}
                    onRemove={onPurgeVersion}
                    onDeleted={onVersionDeleted}
                />
            )}
            {deleteAllOpen && (
                <DeleteAllVersionsDialog
                    open={true}
                    onClose={() => setDeleteAllOpen(false)}
                    extension={extension}
                    versions={deletableVersions}
                    onRemove={onRemoveVersion}
                    onDeleted={onVersionDeleted}
                />
            )}
            {purgeAllOpen && onPurgeVersion && (
                <DeleteAllVersionsDialog
                    open={true}
                    mode='purge'
                    onClose={() => setPurgeAllOpen(false)}
                    extension={extension}
                    versions={allVersions}
                    onRemove={onPurgeVersion}
                    onDeleted={onVersionDeleted}
                />
            )}
        </Box>
    );
};

export interface ExtensionDetailViewProps {
    extension: Extension;
    actions?: ReactNode;
    onRemoveVersion: (targets: VersionDeleteTarget[]) => Promise<unknown>;
    onVersionDeleted: () => void;
    // When provided (admin only), enables the permanent-purge affordances.
    onPurgeVersion?: (targets: VersionDeleteTarget[]) => Promise<unknown>;
}
