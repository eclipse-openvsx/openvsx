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

import { FC, useState } from 'react';
import {
    Alert,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Link,
    Stack,
    TextField,
    Typography
} from '@mui/material';
import { Link as RouterLink } from 'react-router';
import type { NameSquattingExtension } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { AdminDashboardRoutes } from '../admin-dashboard-routes';
import { useAdminExtension } from '../use-extension-admin';
import { pluralize } from './name-squatting-model';
import { extensionId, useDeleteExtension } from './use-name-squatting';

export interface DeleteExtensionDialogProps {
    extension: NameSquattingExtension;
    /** When deleting the extension published first of a group, how many were published after it. */
    laterCount?: number;
    onClose: () => void;
}

/**
 * Soft-deletes every active version of an extension on either side of a pair. The versions are read fresh when it opens,
 * since the delete endpoint removes only the versions it is given.
 */
export const DeleteExtensionDialog: FC<DeleteExtensionDialogProps> = ({
    extension: target,
    laterCount = 0,
    onClose
}) => {
    const [confirmation, setConfirmation] = useState('');
    const { data: extension, isFetching: loadingVersions } = useAdminExtension({
        namespace: target.namespace,
        extension: target.name
    });
    const { mutateAsync: deleteExtension, isPending: deleting, error } = useDeleteExtension();

    const id = extensionId(target);
    const targets = (extension?.allTargetPlatformVersions ?? []).flatMap(version =>
        version.targetPlatforms
            .filter(platform => !platform.removed)
            .map(({ targetPlatform }) => ({ version: version.version, targetPlatform }))
    );
    const versionCount = new Set(targets.map(target => target.version)).size;
    const platformCount = new Set(targets.map(target => target.targetPlatform)).size;
    const canDelete = confirmation.trim() === id && targets.length > 0 && !loadingVersions && !deleting;

    const handleConfirm = async () => {
        try {
            await deleteExtension({
                namespace: target.namespace,
                extension: target.name,
                targetPlatformVersions: targets
            });
            onClose();
        } catch {
            // shown from the mutation's error below
        }
    };

    const describeVersions = () => {
        if (loadingVersions) {
            return 'Every active version is removed and its files deleted from storage.';
        }
        const platforms = platformCount > 1 ? ` (${platformCount} platforms)` : '';
        return versionCount === 1
            ? `Its only version${platforms} is removed and its files deleted from storage.`
            : `All ${pluralize(versionCount, 'version')}${platforms} are removed and their files deleted from storage.`;
    };

    return (
        <Dialog open onClose={deleting ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>Delete {id}?</DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {error && <Alert severity='error'>{handleError(error)}</Alert>}
                    <Alert severity='error' icon={false}>
                        <strong>This cannot be undone.</strong>
                        <ul>
                            <li>{describeVersions()}</li>
                            <li>Users can no longer find, install or update it.</li>
                            <li>These version numbers stay reserved until an administrator purges the extension.</li>
                        </ul>
                    </Alert>
                    {laterCount > 0 && (
                        <Alert severity='warning'>
                            {laterCount === 1
                                ? `1 extension published after ${id} resembles it. It moves under the next earliest extension it resembles, or leaves this page if ${id} was its only match.`
                                : `${laterCount} extensions published after ${id} resemble it. Each moves under the next earliest extension it resembles, or leaves this page if ${id} was its only match.`}
                        </Alert>
                    )}
                    <Alert severity='info'>
                        The publisher can still publish a new version of this extension, which would show up here again.
                        To stop them publishing,{' '}
                        <Link component={RouterLink} to={AdminDashboardRoutes.PUBLISHER_ADMIN}>
                            revoke the publisher
                        </Link>
                        .
                    </Alert>
                    {!loadingVersions && extension && targets.length === 0 && (
                        <Typography color='text.secondary'>{id} has no active versions left to delete.</Typography>
                    )}
                    <TextField
                        label={`Type ${id} to confirm`}
                        value={confirmation}
                        onChange={event => setConfirmation(event.target.value)}
                        autoComplete='off'
                        spellCheck={false}
                        fullWidth
                        autoFocus
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={deleting}>
                    Cancel
                </Button>
                <Button
                    variant='contained'
                    color='error'
                    onClick={handleConfirm}
                    disabled={!canDelete}
                    startIcon={deleting || loadingVersions ? <CircularProgress size={20} /> : undefined}>
                    Delete extension
                </Button>
            </DialogActions>
        </Dialog>
    );
};
