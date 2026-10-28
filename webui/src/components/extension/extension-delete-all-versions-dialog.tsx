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
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { ButtonWithProgress } from '../button-with-progress';
import { MainContext } from '../../context';
import { Extension, VersionTargetPlatforms } from '../../extension-registry-types';
import { VersionDeleteTarget } from './extension-version-delete-dialog';
import { isConflictError } from './extension-version-dialog-shared';

export const DeleteAllVersionsDialog: FunctionComponent<DeleteAllVersionsDialogProps> = props => {
    const { handleError } = useContext(MainContext);
    const [working, setWorking] = useState(false);

    const isPurge = (props.mode ?? 'delete') === 'purge';

    const handleRemove = async () => {
        try {
            setWorking(true);
            // Delete (soft) only applies to versions still present; purge can also target already-removed ones.
            const targets: VersionDeleteTarget[] = props.versions.flatMap(v =>
                v.targetPlatforms
                    .filter(tp => isPurge || !tp.removed)
                    .map(({ targetPlatform }) => ({ version: v.version, targetPlatform }))
            );
            await props.onRemove(targets);
            props.onDeleted();
            props.onClose();
        } catch (err) {
            if (isConflictError(err)) {
                // The underlying data is stale; keep this dialog open behind the error
                // dialog and only close/refresh once the user acknowledges it.
                handleError(err, {
                    title: 'Couldn’t delete the extension',
                    onClose: () => {
                        props.onDeleted();
                        props.onClose();
                    }
                });
            } else {
                handleError(err, { title: 'Couldn’t delete the extension' });
            }
        } finally {
            setWorking(false);
        }
    };

    return (
        <Dialog open={props.open} onClose={props.onClose} maxWidth='xs' fullWidth>
            <DialogTitle>
                {isPurge ? 'Purge' : 'Delete'} all versions of {props.extension.displayName ?? props.extension.name}?
            </DialogTitle>
            <DialogContent>
                <DialogContentText>
                    {isPurge
                        ? `This permanently removes ${props.versions.length} version` +
                          `${props.versions.length === 1 ? '' : 's'} of this extension from the database and storage ` +
                          'across all target platforms, freeing the version numbers so they can be published again. ' +
                          'This cannot be undone.'
                        : `This removes ${props.versions.length} version` +
                          `${props.versions.length === 1 ? '' : 's'} of this extension across all target platforms and ` +
                          'deletes their files. Extension versions are immutable, so removed versions stay ' +
                          'permanently reserved and can never be republished. This cannot be undone.'}
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={props.onClose}>Cancel</Button>
                <ButtonWithProgress sx={{ ml: 1 }} color='error' working={working} onClick={handleRemove}>
                    {isPurge ? 'Purge All Versions' : 'Delete All Versions'}
                </ButtonWithProgress>
            </DialogActions>
        </Dialog>
    );
};

export interface DeleteAllVersionsDialogProps {
    open: boolean;
    onClose: () => void;
    extension: Extension;
    versions: VersionTargetPlatforms[];
    onRemove: (targets: VersionDeleteTarget[]) => Promise<unknown>;
    onDeleted: () => void;
    // 'delete' (default) soft-deletes; 'purge' permanently removes (admin only).
    mode?: 'delete' | 'purge';
}
