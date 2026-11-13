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
import { GroupedPairs } from './name-squatting-parts';
import { extensionId, useAllowNameSquattingPairs } from './use-name-squatting';

/** Mirrors the server's limit, so an over-long reason is caught before anything is allowed. */
export const MAX_REASON_LENGTH = 2048;

/** A look-alike and the extension it resembles. */
export interface CollisionPair {
    anchor: NameSquattingExtension;
    lookalike: NameSquattingExtension;
}

export interface AllowDialogProps {
    /** The selected pairs, from one group or several. */
    pairs: CollisionPair[];
    onAllowed: () => void;
    onClose: () => void;
}

const describePair = ({ anchor, lookalike }: CollisionPair) =>
    `${extensionId(lookalike)} alongside ${extensionId(anchor)}`;

/** Mount it per selection, so the reason and any failure do not carry over to the next one. */
export const AllowDialog: FC<AllowDialogProps> = ({ pairs, onAllowed, onClose }) => {
    const [reason, setReason] = useState('');
    const { mutateAsync: allowPairs, isPending: allowing, error } = useAllowNameSquattingPairs();

    const single = pairs.length === 1;

    const handleConfirm = async () => {
        const trimmed = reason.trim();
        try {
            await allowPairs(
                pairs.map(({ anchor, lookalike }) => ({
                    namespace: lookalike.namespace,
                    extension: lookalike.name,
                    otherNamespace: anchor.namespace,
                    otherExtension: anchor.name,
                    reason: trimmed.length > 0 ? trimmed : undefined
                }))
            );
            onAllowed();
            onClose();
        } catch {
            // shown from the mutation's error below
        }
    };

    return (
        <Dialog open onClose={allowing ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>
                {single ? `Allow ${describePair(pairs[0])}?` : `Allow ${pluralize(pairs.length, 'pair')}?`}
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
                            This pair will no longer appear on the Collisions tab. If {extensionId(pairs[0].lookalike)}{' '}
                            later resembles a different extension, it will be flagged again.
                        </Typography>
                    ) : (
                        <>
                            <Typography>
                                These {pairs.length} pairs move to the Allowlist and stop showing on the Collisions tab.
                                Each look-alike is listed under the extension it resembles.
                            </Typography>
                            <GroupedPairs
                                label='Pairs to allow'
                                pairs={pairs.map(({ anchor, lookalike }) => ({
                                    first: extensionId(anchor),
                                    second: extensionId(lookalike)
                                }))}
                            />
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
                    {single ? 'Allow pair' : `Allow ${pluralize(pairs.length, 'pair')}`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
