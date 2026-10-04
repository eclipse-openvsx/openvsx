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

import { FC, useEffect, useState } from 'react';
import {
    Alert,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography
} from '@mui/material';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';

export interface DeleteSizeOverrideDialogProps {
    open: boolean;
    sizeOverride?: SizeOverride;
    onClose: () => void;
    onConfirm: () => Promise<void>;
}

export const DeleteSizeOverrideDialog: FC<DeleteSizeOverrideDialogProps> = ({
    open,
    sizeOverride,
    onClose,
    onConfirm
}) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | undefined>();

    // The component stays mounted, so a failed deletion's message would greet the next open - under
    // whichever scope that one is for.
    useEffect(() => {
        if (open) {
            setError(undefined);
        }
    }, [open]);

    const scope = sizeOverride?.extension
        ? `${sizeOverride.namespace}.${sizeOverride.extension}`
        : sizeOverride?.namespace;

    const handleConfirm = async () => {
        setLoading(true);
        setError(undefined);
        try {
            await onConfirm();
            onClose();
        } catch (err) {
            setError(handleError(err));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>Delete size override</DialogTitle>
            <DialogContent>
                {error && <Alert severity='error'>{error}</Alert>}
                <Typography>
                    Delete the size override for <strong>{scope}</strong>?
                </Typography>
                <Typography sx={{ mt: 1 }} color='warning.main'>
                    Future uploads will use whichever limit applies next: the namespace override if one exists,
                    otherwise the registry default.
                </Typography>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={loading}>
                    Cancel
                </Button>
                <Button
                    variant='contained'
                    color='error'
                    onClick={handleConfirm}
                    disabled={loading}
                    startIcon={loading ? <CircularProgress size={20} /> : undefined}>
                    Delete
                </Button>
            </DialogActions>
        </Dialog>
    );
};
