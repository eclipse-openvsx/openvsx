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

import { FC, useContext, useEffect, useRef, useState } from 'react';
import {
    Alert,
    Autocomplete,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Stack,
    TextField
} from '@mui/material';
import { MainContext } from '../../../context';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';

const UNIT_MULTIPLIERS: Record<string, number> = {
    bytes: 1,
    KB: 1024,
    MB: 1024 * 1024,
    GB: 1024 * 1024 * 1024
};

const splitSize = (bytes: number): { value: number; unit: string } => {
    const unit = ['GB', 'MB', 'KB'].find(
        candidate => bytes >= UNIT_MULTIPLIERS[candidate] && bytes % UNIT_MULTIPLIERS[candidate] === 0
    );
    return unit ? { value: bytes / UNIT_MULTIPLIERS[unit], unit } : { value: bytes, unit: 'bytes' };
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
    const [extensionNames, setExtensionNames] = useState<string[]>([]);
    const [namespaceConfirmed, setNamespaceConfirmed] = useState(false);
    const [sizeValue, setSizeValue] = useState('100');
    const [sizeUnit, setSizeUnit] = useState('MB');
    const [lookingUp, setLookingUp] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | undefined>();
    // Bumped by every lookup, by typing a different namespace, and by reopening the dialog, so an
    // answer that arrives after any of those is dropped instead of confirming a namespace nobody
    // looked up.
    const lookupGeneration = useRef(0);

    useEffect(() => {
        if (!open) {
            return;
        }
        setError(undefined);
        lookupGeneration.current++;
        // A lookup still in flight from the previous opening will skip its own reset, having been
        // superseded by the bump above - without this the button would stay disabled for good.
        setLookingUp(false);
        if (sizeOverride) {
            const split = splitSize(sizeOverride.maxSize);
            setNamespace(sizeOverride.namespace);
            setExtension(sizeOverride.extension ?? null);
            setExtensionNames(sizeOverride.extension ? [sizeOverride.extension] : []);
            setNamespaceConfirmed(true);
            setSizeValue(String(split.value));
            setSizeUnit(split.unit);
        } else {
            setNamespace('');
            setExtension(null);
            setExtensionNames([]);
            setNamespaceConfirmed(false);
            setSizeValue('100');
            setSizeUnit('MB');
        }
    }, [open, sizeOverride]);

    const handleLookup = async () => {
        const generation = ++lookupGeneration.current;
        setLookingUp(true);
        setError(undefined);
        try {
            const found = await service.admin.getNamespace(new AbortController(), namespace);
            if (generation !== lookupGeneration.current) {
                return;
            }
            setExtensionNames(Object.keys(found.extensions));
            setNamespaceConfirmed(true);
        } catch (err) {
            if (generation !== lookupGeneration.current) {
                return;
            }
            setExtensionNames([]);
            setNamespaceConfirmed(false);
            setError(handleError(err));
        } finally {
            if (generation === lookupGeneration.current) {
                setLookingUp(false);
            }
        }
    };

    // Number, not parseInt: parseInt stops at the first non-digit, so 1.5 would be submitted as 1 and
    // 1e3 as 1, neither of which is what the field showed.
    const maxSize = Number(sizeValue) * UNIT_MULTIPLIERS[sizeUnit];
    const canSubmit = namespaceConfirmed && Number.isSafeInteger(maxSize) && maxSize > 0 && !saving;

    const handleSubmit = async () => {
        if (!canSubmit) {
            return;
        }
        setSaving(true);
        setError(undefined);
        try {
            await onSubmit({
                id: sizeOverride?.id ?? 0,
                namespace,
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

    return (
        <Dialog open={open} onClose={onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{isEditMode ? 'Edit size override' : 'Create size override'}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    {error && <Alert severity='error'>{error}</Alert>}
                    <Stack direction='row' spacing={1}>
                        <TextField
                            label='Namespace'
                            value={namespace}
                            fullWidth
                            disabled={isEditMode}
                            onChange={event => {
                                lookupGeneration.current++;
                                setNamespace(event.target.value);
                                setNamespaceConfirmed(false);
                                setExtension(null);
                                setExtensionNames([]);
                            }}
                        />
                        <Button
                            onClick={handleLookup}
                            disabled={isEditMode || namespace.length === 0 || lookingUp}
                            startIcon={lookingUp ? <CircularProgress size={20} /> : undefined}>
                            Look up
                        </Button>
                    </Stack>
                    <Autocomplete
                        options={extensionNames}
                        value={extension}
                        disabled={!namespaceConfirmed || isEditMode}
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
                <Button onClick={onClose}>Cancel</Button>
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
