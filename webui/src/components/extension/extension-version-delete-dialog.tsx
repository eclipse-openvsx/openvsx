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

import { ChangeEvent, FunctionComponent, useContext, useEffect, useState } from 'react';
import {
    Box,
    Button,
    Checkbox,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControlLabel,
    FormGroup
} from '@mui/material';
import { Extension, TargetPlatformVersion, VersionTargetPlatforms } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { getTargetPlatformDisplayName } from '../../utils';
import {
    VERSION_DIALOG_WILDCARD,
    buildVersionDialogItems,
    handleVersionDialogChange,
    isConflictError
} from './extension-version-dialog-shared';

export interface VersionDeleteTarget {
    version: string;
    targetPlatform: string;
}

export const DeleteVersionDialog: FunctionComponent<DeleteVersionDialogProps> = props => {
    const { handleError } = useContext(MainContext);
    const [items, setItems] = useState<TargetPlatformVersion[]>([]);
    const [working, setWorking] = useState(false);

    const mode = props.mode ?? 'delete';
    const isPurge = mode === 'purge';

    useEffect(() => {
        // Delete (soft) only applies to versions that are still present; purge can also target
        // versions that were already removed (to free their identity for republishing).
        const targetPlatforms = props.version.targetPlatforms
            .filter(tp => isPurge || !tp.removed)
            .map(tp => tp.targetPlatform);
        setItems(buildVersionDialogItems(targetPlatforms));
    }, [props.version, isPurge]);

    const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
        setItems(prev => handleVersionDialogChange(event, prev));
    };

    const selected = items.filter(i => i.targetPlatform !== VERSION_DIALOG_WILDCARD && i.checked);

    const handleRemove = async () => {
        try {
            setWorking(true);
            await props.onRemove(
                selected.map(({ targetPlatform }) => ({ version: props.version.version, targetPlatform }))
            );
            props.onDeleted();
            props.onClose();
        } catch (err) {
            if (isConflictError(err)) {
                // The underlying data is stale; keep this dialog open behind the error
                // dialog and only close/refresh once the user acknowledges it.
                handleError(err, {
                    title: 'Couldn’t delete the version',
                    onClose: () => {
                        props.onDeleted();
                        props.onClose();
                    }
                });
            } else {
                handleError(err, { title: 'Couldn’t delete the version' });
            }
        } finally {
            setWorking(false);
        }
    };

    const allItem = items.find(i => i.targetPlatform === VERSION_DIALOG_WILDCARD);
    const platformItems = items.filter(i => i.targetPlatform !== VERSION_DIALOG_WILDCARD);

    return (
        <Dialog open={props.open} onClose={props.onClose} maxWidth='xs' fullWidth>
            <DialogTitle>
                {isPurge ? 'Purge' : 'Delete'} version {props.version.version} of{' '}
                {props.extension.displayName ?? props.extension.name}
            </DialogTitle>
            <DialogContent>
                <DialogContentText sx={{ mb: 2 }}>
                    {isPurge
                        ? 'Purging permanently removes the selected version(s) from the database and storage, ' +
                          'freeing the version identity so it can be published again. This cannot be undone.'
                        : 'The selected version(s) will be removed and their files deleted. Extension versions are ' +
                          'immutable, so a removed version stays permanently reserved and can never be republished.'}
                </DialogContentText>
                <FormGroup>
                    {allItem && (
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={allItem.checked}
                                    onChange={handleChange}
                                    name={VERSION_DIALOG_WILDCARD}
                                />
                            }
                            label='All Targets'
                        />
                    )}
                    {platformItems.map(item => (
                        <FormControlLabel
                            key={item.targetPlatform}
                            sx={{ pl: 4 }}
                            control={
                                <Checkbox checked={item.checked} onChange={handleChange} name={item.targetPlatform} />
                            }
                            label={getTargetPlatformDisplayName(item.targetPlatform) || item.targetPlatform}
                        />
                    ))}
                </FormGroup>
            </DialogContent>
            <DialogActions>
                <Button onClick={props.onClose}>Cancel</Button>
                <Box sx={{ ml: 1, position: 'relative', display: 'inline-flex' }}>
                    <Button
                        variant='contained'
                        color={isPurge ? 'error' : 'secondary'}
                        disabled={selected.length === 0 || working}
                        onClick={handleRemove}>
                        {isPurge ? 'Purge' : 'Remove'}
                    </Button>
                    {working && (
                        <CircularProgress
                            size={24}
                            sx={{
                                position: 'absolute',
                                top: '50%',
                                left: '50%',
                                mt: '-12px',
                                ml: '-12px'
                            }}
                        />
                    )}
                </Box>
            </DialogActions>
        </Dialog>
    );
};

export interface DeleteVersionDialogProps {
    open: boolean;
    onClose: () => void;
    extension: Extension;
    version: VersionTargetPlatforms;
    onRemove: (targets: VersionDeleteTarget[]) => Promise<unknown>;
    onDeleted: () => void;
    // 'delete' (default) soft-deletes; 'purge' permanently removes (admin only).
    mode?: 'delete' | 'purge';
}
