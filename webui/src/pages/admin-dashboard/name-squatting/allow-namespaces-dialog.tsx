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
import { pluralize } from './name-squatting-model';
import { GroupedPairs } from './name-squatting-parts';
import { useAllowNameSquattingNamespaces } from './use-name-squatting';

export interface AllowNamespacesDialogProps {
    /** The selected pairs. */
    pairs: NameSquattingNamespacePair[];
    onAllowed: () => void;
    onClose: () => void;
}

const describePair = ({ target, lookalike }: NameSquattingNamespacePair) =>
    `${lookalike.name} alongside ${target.name}`;

/** Mount it per selection, so the reason and any failure do not carry over to the next one. */
export const AllowNamespacesDialog: FC<AllowNamespacesDialogProps> = ({ pairs, onAllowed, onClose }) => {
    const [reason, setReason] = useState('');
    const { mutateAsync: allowNamespaces, isPending: allowing, error } = useAllowNameSquattingNamespaces();
    const single = pairs.length === 1;
    const sharedMembers = pairs.filter(pair => pair.sharedMember).length;

    const handleConfirm = async () => {
        const trimmed = reason.trim();
        try {
            await allowNamespaces(
                pairs.map(({ target, lookalike }) => ({
                    namespace: lookalike.name,
                    otherNamespace: target.name,
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
                {single ? `Allow namespace ${describePair(pairs[0])}?` : `Allow ${pluralize(pairs.length, 'pair')}?`}
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {error && (
                        <Alert severity='error'>
                            {handleError(error)}
                            {!single && ' Pairs allowed before the failure stay allowed.'}
                        </Alert>
                    )}
                    <Typography>
                        {single ? 'This pair' : `These ${pairs.length} pairs`} will no longer appear on the Namespaces
                        tab, with or without the popular filter. Other namespaces that resemble them are still reported.
                        {!single && ' Each look-alike is listed under its target.'}
                    </Typography>
                    {!single && (
                        <GroupedPairs
                            label='Pairs to allow'
                            pairs={pairs.map(({ target, lookalike }) => ({
                                first: target.name,
                                second: lookalike.name
                            }))}
                        />
                    )}
                    {sharedMembers > 0 && (
                        <Alert severity='info'>
                            {single
                                ? 'A user is a member of both namespaces, so they are likely the same publisher.'
                                : `In ${pluralize(sharedMembers, 'pair')}, a user is a member of both namespaces, so they are likely the same publisher.`}
                        </Alert>
                    )}
                    <TextField
                        label={single ? 'Reason (optional)' : 'Reason (optional, saved on every pair)'}
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
                    {single ? 'Allow pair' : `Allow ${pluralize(pairs.length, 'pair')}`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
