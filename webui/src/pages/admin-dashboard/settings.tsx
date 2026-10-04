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

import { ChangeEvent, FC, useCallback, useEffect, useState } from 'react';
import { SaveButton } from '../../components/save-button';
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Paper,
    Stack,
    TextField,
    Typography
} from '@mui/material';
import type { Settings } from '../../extension-registry-types';
import { handleError } from '../../utils';
import { useSavedFlash } from '../../hooks/use-saved-flash';
import { SettingsItem } from './settings-item';
import { useSettings, useUpdateSettings } from './use-settings';

interface NotificationState {
    id: string;
    message: string;
    severity: 'error';
    timeout: ReturnType<typeof setTimeout>;
}

const NOTIFICATION_TIMEOUT = 2000;
const BYTES_PER_MB = 1024 * 1024;

/**
 * The settings rendered as toggles. Keyed by the boolean members of `Settings` only: a numeric
 * setting such as `maxExtensionSize` has its own control and must not be routed through the toggle
 * handler, which would write a boolean into it.
 */
type BooleanSettingKey = {
    [K in keyof Settings]: Settings[K] extends boolean ? K : never;
}[keyof Settings];

const SETTINGS: Record<BooleanSettingKey, { title: string; description: string }> = {
    readOnly: {
        title: 'Read-only mode',
        description: 'Blocks write operations while keeping browsing, search, and downloads available.'
    }
};

/** The settings whose value differs from what the server last reported. */
const changedSettings = (draft: Settings, current: Settings): Partial<Settings> =>
    (Object.keys(draft) as (keyof Settings)[]).reduce<Partial<Settings>>((changed, key) => {
        if (draft[key] !== current[key]) {
            // the key and its value come from the same object, so the pair is sound; the cast is only
            // needed because TypeScript widens the value to a union across all keys
            (changed as Record<string, unknown>)[key] = draft[key];
        }
        return changed;
    }, {});

export const RuntimeSettingsPage: FC = () => {
    const { data: settings, isLoading: loading, error: loadError } = useSettings();
    const { mutate: saveSettings, isPending: saving } = useUpdateSettings();

    const [draftSettings, setDraftSettings] = useState<Settings | null>(null);
    const [errorDismissed, setErrorDismissed] = useState(false);
    const [notifications, setNotifications] = useState<NotificationState[]>([]);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const { saved: saveSuccess, flash: flashSaved, clear: clearSaved } = useSavedFlash(2000);

    // Keep the editable draft in sync with the loaded (and freshly saved) settings.
    useEffect(() => {
        if (settings) {
            setDraftSettings(settings);
        }
    }, [settings]);

    // A fresh load error should be shown again even if a previous one was dismissed.
    useEffect(() => {
        setErrorDismissed(false);
    }, [loadError]);

    useEffect(
        () => () => {
            notifications.forEach(n => clearTimeout(n.timeout));
        },
        []
    );

    const error = loadError && !errorDismissed ? handleError(loadError as Error) : null;

    const addNotification = useCallback((notification: Pick<NotificationState, 'message'>) => {
        const id = crypto.randomUUID();
        const timeout = setTimeout(() => {
            setNotifications(current => current.filter(n => n.id !== id));
        }, NOTIFICATION_TIMEOUT);
        setNotifications(current => [...current, { ...notification, severity: 'error', id, timeout }]);
    }, []);

    const handleNotificationClose = (id: string) => {
        setNotifications(current => {
            const notification = current.find(n => n.id === id);
            if (notification) clearTimeout(notification.timeout);
            return current.filter(n => n.id !== id);
        });
    };

    const handleFlagChange = useCallback(
        (key: BooleanSettingKey) => (_event: ChangeEvent<HTMLInputElement>, checked: boolean) => {
            setDraftSettings(current => (current ? { ...current, [key]: checked } : current));
            clearSaved();
        },
        [clearSaved]
    );

    const handleMaxExtensionSizeChange = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => {
            // Number, not parseInt: parseInt stops at the first non-digit, so 1.5 would be stored as
            // 1 MB and 1e3 as 1 MB while the field kept showing what was typed.
            const mb = Number(event.target.value);
            const bytes = Number.isFinite(mb) ? mb * BYTES_PER_MB : Number.NaN;
            setDraftSettings(current => (current ? { ...current, maxExtensionSize: bytes } : current));
            clearSaved();
        },
        [clearSaved]
    );

    const maxExtensionSizeChanged =
        draftSettings !== null && settings != null && draftSettings.maxExtensionSize !== settings.maxExtensionSize;

    const hasChanges =
        draftSettings !== null &&
        settings != null &&
        ((Object.keys(SETTINGS) as BooleanSettingKey[]).some(k => draftSettings[k] !== settings[k]) ||
            maxExtensionSizeChanged);

    // Only validated once the admin has actually edited it, because only then is it sent. The server
    // stores the limit as a long, and one beyond JavaScript's safe-integer range would otherwise fail
    // this check on arrival and block every unrelated setting from being saved.
    const maxExtensionSizeValid =
        draftSettings === null ||
        !maxExtensionSizeChanged ||
        (Number.isSafeInteger(draftSettings.maxExtensionSize) && draftSettings.maxExtensionSize > 0);

    const handleSaveClick = () => setConfirmOpen(true);

    const handleConfirmClose = () => setConfirmOpen(false);

    const handleConfirmSave = useCallback(() => {
        if (!draftSettings || !settings) return;
        setConfirmOpen(false);
        // Only what this admin actually changed. Sending the whole object would carry every other
        // setting as this page last read it, silently reverting anything someone else changed in the
        // meantime. It does not help when two people edit the same setting - the last save still wins.
        saveSettings(changedSettings(draftSettings, settings), {
            onSuccess: flashSaved,
            onError: err => {
                addNotification({
                    message: `Failed to save runtime settings. ${handleError(err as Error)}`
                });
            }
        });
    }, [draftSettings, settings, saveSettings, addNotification, flashSaved]);

    return (
        <>
            <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 3 }}>
                <Box>
                    <Typography variant='h4' component='h1' gutterBottom>
                        Settings
                    </Typography>
                    <Typography variant='body1' color='text.secondary'>
                        Manage runtime settings that apply across the registry.
                    </Typography>
                </Box>

                {error && (
                    <Alert severity='error' onClose={() => setErrorDismissed(true)}>
                        {error}
                    </Alert>
                )}

                <Paper
                    variant='outlined'
                    elevation={0}
                    sx={{ overflow: 'hidden', borderColor: hasChanges ? 'red' : 'grey' }}>
                    {(Object.entries(SETTINGS) as [BooleanSettingKey, { title: string; description: string }][]).map(
                        ([key, flag]) => (
                            <SettingsItem
                                key={key}
                                title={flag.title}
                                description={flag.description}
                                checked={draftSettings?.[key] ?? false}
                                loading={loading || !draftSettings}
                                disabled={loading || saving || !draftSettings}
                                onChange={handleFlagChange(key)}
                            />
                        )
                    )}
                </Paper>

                <Paper variant='outlined' elevation={0} sx={{ p: 3 }}>
                    <Typography variant='subtitle1' gutterBottom>
                        Default max extension size
                    </Typography>
                    <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                        The largest extension package accepted for publishing when no namespace or extension override
                        applies.
                    </Typography>
                    <TextField
                        label='Max extension size (MB)'
                        type='number'
                        value={
                            draftSettings && Number.isFinite(draftSettings.maxExtensionSize)
                                ? draftSettings.maxExtensionSize / BYTES_PER_MB
                                : ''
                        }
                        onChange={handleMaxExtensionSizeChange}
                        disabled={loading || saving || !draftSettings}
                        error={!maxExtensionSizeValid}
                        helperText={
                            maxExtensionSizeValid ? undefined : 'Must be a whole number of bytes, greater than 0'
                        }
                        inputProps={{ min: '1' }}
                        sx={{ maxWidth: 240 }}
                    />
                </Paper>

                <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <SaveButton
                        size='large'
                        saved={saveSuccess}
                        disabled={!hasChanges || saving || !maxExtensionSizeValid}
                        onClick={handleSaveClick}
                    />
                </Box>
            </Box>

            <Dialog open={confirmOpen} onClose={handleConfirmClose} maxWidth='xs' fullWidth>
                <DialogTitle>Apply settings?</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        These changes will be applied <strong>immediately</strong> and will affect all users of the
                        registry. Make sure you understand the impact before proceeding.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleConfirmClose}>Cancel</Button>
                    <Button variant='contained' onClick={handleConfirmSave} autoFocus>
                        Apply
                    </Button>
                </DialogActions>
            </Dialog>

            {notifications.length > 0 && (
                <Stack
                    spacing={1.5}
                    sx={{
                        position: 'fixed',
                        right: 24,
                        bottom: 24,
                        zIndex: theme => theme.zIndex.snackbar,
                        width: 'min(420px, calc(100vw - 32px))'
                    }}>
                    {notifications.map(notification => (
                        <Alert
                            key={notification.id}
                            onClose={() => handleNotificationClose(notification.id)}
                            severity={notification.severity}
                            variant='filled'
                            sx={{ width: '100%' }}>
                            {notification.message}
                        </Alert>
                    ))}
                </Stack>
            )}
        </>
    );
};
