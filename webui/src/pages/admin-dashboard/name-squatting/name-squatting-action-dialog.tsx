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
    Typography
} from '@mui/material';
import type { NameSquattingFlag } from '../../../extension-registry-types';
import { handleError } from '../../../utils';

export type NameSquattingAction = 'clear' | 'delete';

export interface NameSquattingActionDialogProps {
    open: boolean;
    action: NameSquattingAction;
    flag?: NameSquattingFlag;
    onClose: () => void;
    onConfirm: () => Promise<void>;
}

export const NameSquattingActionDialog: FC<NameSquattingActionDialogProps> = ({
    open,
    action,
    flag,
    onClose,
    onConfirm
}) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleConfirm = async () => {
        try {
            setError(null);
            setLoading(true);
            await onConfirm();
            onClose();
        } catch (err) {
            setError(handleError(err as Error));
        } finally {
            setLoading(false);
        }
    };

    const extensionId = flag ? `${flag.namespace}.${flag.extensionName}` : '';
    const clearing = action === 'clear';

    return (
        <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{clearing ? 'Mark as false positive' : 'Soft delete extension'}</DialogTitle>
            <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
                {error && <Alert severity='error'>{error}</Alert>}

                {clearing ? (
                    <>
                        <Typography>
                            Clear the {flag?.findingCount ?? 0} name squatting{' '}
                            {flag?.findingCount === 1 ? 'finding' : 'findings'} recorded for{' '}
                            <strong>{extensionId}</strong>?
                        </Typography>
                        <Typography variant='body2' color='warning.main'>
                            The findings are deleted, so the extension no longer appears here. The record of the check
                            having run is kept with the scan, and this action is written to the admin log.
                        </Typography>
                    </>
                ) : (
                    <>
                        <Typography>
                            Deactivate all {flag?.activeVersionCount ?? 0} active{' '}
                            {flag?.activeVersionCount === 1 ? 'version' : 'versions'} of <strong>{extensionId}</strong>?
                        </Typography>
                        <Typography variant='body2' color='warning.main'>
                            The extension becomes unavailable for download and search. Its records are kept and its
                            version identities stay reserved, and this action is written to the admin log.
                        </Typography>
                    </>
                )}
            </DialogContent>

            <DialogActions sx={{ p: 2 }}>
                <Button onClick={onClose} disabled={loading}>
                    Cancel
                </Button>
                <Button
                    onClick={handleConfirm}
                    variant='contained'
                    color={clearing ? 'primary' : 'error'}
                    disabled={loading}
                    startIcon={loading ? <CircularProgress size={20} /> : undefined}>
                    {clearing ? 'Clear findings' : 'Soft delete'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
