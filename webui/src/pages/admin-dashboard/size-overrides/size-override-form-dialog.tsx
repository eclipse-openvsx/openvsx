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

import { FC, useContext, useEffect, useState } from 'react';
import {
    Alert,
    Autocomplete,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    InputAdornment,
    MenuItem,
    Stack,
    TextField
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { MainContext } from '../../../context';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';

const UNIT_MULTIPLIERS: Record<string, number> = {
    bytes: 1,
    KB: 1024,
    MB: 1024 * 1024,
    GB: 1024 * 1024 * 1024
};

/** Idle period after a keystroke before the namespace is looked up. */
const LOOKUP_DEBOUNCE_MS = 400;

const UNVERIFIED_MESSAGE = 'Not verified - a size override can only be granted to a verified namespace';

/**
 * What the registry says about the namespace currently typed. `verified` is the only state the form
 * can be submitted from, so the server's own precondition is what gates the dialog.
 */
type NamespaceCheck =
    | { state: 'idle' }
    | { state: 'checking' }
    // carries the name as checked, which is what gets submitted - the field may hold stray whitespace
    | { state: 'verified'; name: string; extensions: string[] }
    | { state: 'rejected'; reason: string };

const splitSize = (bytes: number): { value: number; unit: string } => {
    const unit = ['GB', 'MB', 'KB'].find(
        candidate => bytes >= UNIT_MULTIPLIERS[candidate] && bytes % UNIT_MULTIPLIERS[candidate] === 0
    );
    return unit ? { value: bytes / UNIT_MULTIPLIERS[unit], unit } : { value: bytes, unit: 'bytes' };
};

const describeVerified = (extensions: string[]): string => {
    if (extensions.length === 0) {
        return 'Verified namespace, no extensions yet';
    }
    return `Verified namespace, ${extensions.length} extension${extensions.length === 1 ? '' : 's'}`;
};

export interface SizeOverrideFormDialogProps {
    open: boolean;
    sizeOverride?: SizeOverride;
    onClose: () => void;
    onSubmit: (override: SizeOverride) => Promise<void>;
}

export const SizeOverrideFormDialog: FC<SizeOverrideFormDialogProps> = ({ open, sizeOverride, onClose, onSubmit }) => {
    const { service } = useContext(MainContext);
    const isEditMode = sizeOverride !== undefined;

    const [namespace, setNamespace] = useState('');
    const [extension, setExtension] = useState<string | null>(null);
    const [check, setCheck] = useState<NamespaceCheck>({ state: 'idle' });
    const [sizeValue, setSizeValue] = useState('100');
    const [sizeUnit, setSizeUnit] = useState('MB');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | undefined>();

    useEffect(() => {
        if (!open) {
            return;
        }
        setError(undefined);
        setSaving(false);
        if (sizeOverride) {
            const split = splitSize(sizeOverride.maxSize);
            setNamespace(sizeOverride.namespace);
            setExtension(sizeOverride.extension ?? null);
            // An existing override is on a namespace that was verified when it was granted; the name
            // cannot be edited here, so there is nothing to re-check.
            setCheck({
                state: 'verified',
                name: sizeOverride.namespace,
                extensions: sizeOverride.extension ? [sizeOverride.extension] : []
            });
            setSizeValue(String(split.value));
            setSizeUnit(split.unit);
        } else {
            setNamespace('');
            setExtension(null);
            setCheck({ state: 'idle' });
            setSizeValue('100');
            setSizeUnit('MB');
        }
    }, [open, sizeOverride]);

    // Checks the namespace as it is typed. The cleanup drops both the pending timer and the answer to
    // a request already superseded, so only the name currently in the field can decide the outcome.
    useEffect(() => {
        if (!open || isEditMode) {
            return;
        }
        const name = namespace.trim();
        if (name.length === 0) {
            setCheck({ state: 'idle' });
            return;
        }

        setCheck({ state: 'checking' });
        let superseded = false;
        const timer = setTimeout(async () => {
            try {
                const found = await service.admin.getNamespace(new AbortController(), name);
                if (superseded) {
                    return;
                }
                setCheck(
                    found.verified
                        ? { state: 'verified', name, extensions: Object.keys(found.extensions) }
                        : { state: 'rejected', reason: UNVERIFIED_MESSAGE }
                );
            } catch (err) {
                if (!superseded) {
                    setCheck({ state: 'rejected', reason: handleError(err) });
                }
            }
        }, LOOKUP_DEBOUNCE_MS);

        return () => {
            superseded = true;
            clearTimeout(timer);
        };
    }, [open, isEditMode, namespace, service]);

    const extensionNames = check.state === 'verified' ? check.extensions : [];

    // Number, not parseInt: parseInt stops at the first non-digit, so 1.5 would be submitted as 1 and
    // 1e3 as 1, neither of which is what the field showed.
    const maxSize = Number(sizeValue) * UNIT_MULTIPLIERS[sizeUnit];
    const canSubmit = check.state === 'verified' && Number.isSafeInteger(maxSize) && maxSize > 0 && !saving;

    const handleSubmit = async () => {
        if (!canSubmit || check.state !== 'verified') {
            return;
        }
        setSaving(true);
        setError(undefined);
        try {
            await onSubmit({
                id: sizeOverride?.id ?? 0,
                namespace: check.name,
                extension: extension ?? undefined,
                maxSize
            });
            onClose();
        } catch (err) {
            setError(handleError(err));
        } finally {
            setSaving(false);
        }
    };

    const namespaceStatusIcon = () => {
        switch (check.state) {
            case 'checking':
                return <CircularProgress size={20} />;
            case 'verified':
                return <CheckCircleOutlineIcon color='success' fontSize='small' />;
            case 'rejected':
                return <ErrorOutlineIcon color='error' fontSize='small' />;
            default:
                return undefined;
        }
    };

    const namespaceHelperText = () => {
        switch (check.state) {
            case 'verified':
                return describeVerified(check.extensions);
            case 'rejected':
                return check.reason;
            default:
                return ' ';
        }
    };

    return (
        <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{isEditMode ? 'Edit size override' : 'Create size override'}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    {error && <Alert severity='error'>{error}</Alert>}
                    <TextField
                        label='Namespace'
                        value={namespace}
                        fullWidth
                        disabled={isEditMode}
                        error={check.state === 'rejected'}
                        helperText={namespaceHelperText()}
                        onChange={event => {
                            setNamespace(event.target.value);
                            setExtension(null);
                        }}
                        InputProps={{
                            endAdornment: <InputAdornment position='end'>{namespaceStatusIcon()}</InputAdornment>
                        }}
                    />
                    <Autocomplete
                        options={extensionNames}
                        value={extension}
                        disabled={check.state !== 'verified' || isEditMode}
                        onChange={(_event, value) => setExtension(value)}
                        renderInput={params => (
                            <TextField
                                {...params}
                                label='Extension (optional)'
                                helperText='Leave empty to apply the override to the whole namespace'
                            />
                        )}
                    />
                    <Stack direction='row' spacing={1}>
                        <TextField
                            label='Max size'
                            type='number'
                            value={sizeValue}
                            fullWidth
                            inputProps={{ min: '1' }}
                            onChange={event => setSizeValue(event.target.value)}
                        />
                        <TextField
                            select
                            label='Unit'
                            value={sizeUnit}
                            sx={{ minWidth: '8rem' }}
                            onChange={event => setSizeUnit(event.target.value)}>
                            {Object.keys(UNIT_MULTIPLIERS).map(unit => (
                                <MenuItem key={unit} value={unit}>
                                    {unit}
                                </MenuItem>
                            ))}
                        </TextField>
                    </Stack>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>
                    Cancel
                </Button>
                <Button
                    variant='contained'
                    onClick={handleSubmit}
                    disabled={!canSubmit}
                    startIcon={saving ? <CircularProgress size={20} /> : undefined}>
                    {isEditMode ? 'Update' : 'Create'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
