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
    Stack,
    TextField,
    Typography
} from '@mui/material';
import type { NameSquattingExtension } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { pluralize } from './name-squatting-model';
import { extensionId, useAllowNameSquattingPairs } from './use-name-squatting';

/** Mirrors the server's limit, so an over-long reason is caught before anything is allowed. */
const MAX_REASON_LENGTH = 2048;

export interface AllowDialogProps {
    anchor: NameSquattingExtension;
    /** One extension published later to allow, or every one of the group for "Allow all". */
    lookalikes: NameSquattingExtension[];
    onClose: () => void;
}

/** Mount it per target, so the reason and any failure do not carry over to the next pair. */
export const AllowDialog: FC<AllowDialogProps> = ({ anchor, lookalikes, onClose }) => {
    const [reason, setReason] = useState('');
    const { mutateAsync: allowPairs, isPending: allowing, error } = useAllowNameSquattingPairs();

    const anchorId = extensionId(anchor);
    const single = lookalikes.length === 1;

    const handleConfirm = async () => {
        const trimmed = reason.trim();
        try {
            await allowPairs(
                lookalikes.map(lookalike => ({
                    namespace: lookalike.namespace,
                    extension: lookalike.name,
                    otherNamespace: anchor.namespace,
                    otherExtension: anchor.name,
                    reason: trimmed.length > 0 ? trimmed : undefined
                }))
            );
            onClose();
        } catch {
            // shown from the mutation's error below
        }
    };

    return (
        <Dialog open onClose={allowing ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>
                {single
                    ? `Allow ${extensionId(lookalikes[0])} alongside ${anchorId}?`
                    : `Allow all ${lookalikes.length} extensions alongside ${anchorId}?`}
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {error && (
                        <Alert severity='error'>
                            {handleError(error)}
                            {!single && ' Pairs allowed before the failure stay allowed.'}
                        </Alert>
                    )}
                    {single ? (
                        <Typography>
                            This pair will no longer appear on the Collisions tab. If {extensionId(lookalikes[0])} later
                            resembles a different extension, it will be flagged again.
                        </Typography>
                    ) : (
                        <>
                            <Typography>
                                Each of these pairs will be added to the allowlist and hidden from the Collisions tab:
                            </Typography>
                            <ul>
                                {lookalikes.map(lookalike => (
                                    <li key={extensionId(lookalike)}>{extensionId(lookalike)}</li>
                                ))}
                            </ul>
                        </>
                    )}
                    <TextField
                        label={single ? 'Reason (optional)' : 'Reason (optional, saved on every pair)'}
                        helperText='Shown on the Allowlist tab'
                        placeholder='For example: official fork, published by the same organization'
                        value={reason}
                        onChange={event => setReason(event.target.value)}
                        error={reason.length > MAX_REASON_LENGTH}
                        multiline
                        minRows={2}
                        fullWidth
                        autoFocus
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={allowing}>
                    Cancel
                </Button>
                <Button
                    variant='contained'
                    onClick={handleConfirm}
                    disabled={allowing || reason.length > MAX_REASON_LENGTH}
                    startIcon={allowing ? <CircularProgress size={20} /> : undefined}>
                    {single ? 'Allow pair' : `Allow ${pluralize(lookalikes.length, 'pair')}`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
