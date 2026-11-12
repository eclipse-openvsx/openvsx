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
import type { NameSquattingNamespacePair } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { MAX_REASON_LENGTH } from './allow-dialog';
import { useAllowNameSquattingNamespaces } from './use-name-squatting';

export interface AllowNamespacesDialogProps {
    pair: NameSquattingNamespacePair;
    onClose: () => void;
}

/** Mount it per pair, so the reason and any failure do not carry over to the next one. */
export const AllowNamespacesDialog: FC<AllowNamespacesDialogProps> = ({ pair, onClose }) => {
    const [reason, setReason] = useState('');
    const { mutateAsync: allowNamespaces, isPending: allowing, error } = useAllowNameSquattingNamespaces();
    const { target, lookalike } = pair;

    const handleConfirm = async () => {
        const trimmed = reason.trim();
        try {
            await allowNamespaces({
                namespace: lookalike.name,
                otherNamespace: target.name,
                reason: trimmed.length > 0 ? trimmed : undefined
            });
            onClose();
        } catch {
            // shown from the mutation's error below
        }
    };

    return (
        <Dialog open onClose={allowing ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{`Allow namespace ${lookalike.name} alongside ${target.name}?`}</DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {error && <Alert severity='error'>{handleError(error)}</Alert>}
                    <Typography>
                        This pair will no longer appear on the Namespaces tab, with or without the popular filter. Other
                        namespaces that resemble either one are still reported.
                    </Typography>
                    {pair.sharedMember && (
                        <Alert severity='info'>
                            A user is a member of both namespaces, so they are likely the same publisher.
                        </Alert>
                    )}
                    <TextField
                        label='Reason (optional)'
                        helperText='Shown on the Allowlist tab'
                        placeholder='For example: same organization, unrelated projects with similar names'
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
                    Allow pair
                </Button>
            </DialogActions>
        </Dialog>
    );
};
