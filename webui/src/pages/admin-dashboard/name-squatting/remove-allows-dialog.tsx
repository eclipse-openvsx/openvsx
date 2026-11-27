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

import { FC } from 'react';
import {
    Alert,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    Typography
} from '@mui/material';
import type { NameSquattingAllow, NameSquattingExtension } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { pluralize } from './name-squatting-model';
import { extensionId, useRemoveNameSquattingAllows } from './use-name-squatting';

export interface RemoveAllowsDialogProps {
    /** The allowed extension the pairs are listed under. */
    extension: NameSquattingExtension;
    allows: NameSquattingAllow[];
    onClose: () => void;
}

export const RemoveAllowsDialog: FC<RemoveAllowsDialogProps> = ({ extension, allows, onClose }) => {
    const { mutateAsync: removeAllows, isPending: removing, error } = useRemoveNameSquattingAllows();

    const id = extensionId(extension);
    const single = allows.length === 1;

    const handleConfirm = async () => {
        try {
            await removeAllows(allows.map(allow => allow.id));
            onClose();
        } catch {
            // shown from the mutation's error below
        }
    };

    return (
        <Dialog open onClose={removing ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>
                {single
                    ? `Remove the allow for ${id} and ${extensionId(allows[0].other)}?`
                    : `Remove all ${pluralize(allows.length, 'allowed pair')} for ${id}?`}
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {error && (
                        <Alert severity='error'>
                            {handleError(error)}
                            {!single && ' Allows removed before the failure stay removed.'}
                        </Alert>
                    )}
                    <Typography>
                        {single
                            ? 'If the two names still match, the pair will appear on the Collisions tab again.'
                            : 'Any pair whose names still match will appear on the Collisions tab again.'}
                    </Typography>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={removing}>
                    Cancel
                </Button>
                <Button
                    variant='contained'
                    color='error'
                    onClick={handleConfirm}
                    disabled={removing}
                    startIcon={removing ? <CircularProgress size={20} /> : undefined}>
                    {single ? 'Remove allow' : `Remove ${pluralize(allows.length, 'allow')}`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
