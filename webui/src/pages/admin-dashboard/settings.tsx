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

import { ChangeEvent, FC, useCallback, useContext, useEffect, useMemo, useState } from 'react';
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
    Stack,
    Typography
} from '@mui/material';
import type { Settings, SiteSettings } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { handleError } from '../../utils';
import { useSavedFlash } from '../../hooks/use-saved-flash';
import { SettingsBannerItem } from './settings-banner-item';
import { SettingsItem } from './settings-item';
import { BYTES_PER_MB, SettingsMaxSizeItem } from './settings-max-size-item';
import { SettingsSection } from './settings-section';
import { ResolvedSettingsSection, withContributedSections } from './settings-sections';
import { useSettings, useUpdateSettings } from './use-settings';

interface NotificationState {
    id: string;
    message: string;
    severity: 'error';
    timeout: ReturnType<typeof setTimeout>;
}

const NOTIFICATION_TIMEOUT = 2000;

const BANNER_KEYS = ['bannerEnabled', 'bannerMessage', 'bannerSeverity'] as const satisfies readonly (keyof Settings)[];

/** A setting by key, which for a contributed switch is one the library's `Settings` type does not declare. */
const valueOf = (settings: object, key: string): unknown => (settings as Record<string, unknown>)[key];

const hasMessage = (settings: SiteSettings) => (settings.bannerMessage ?? '').trim().length > 0;

/** Whether visitors would actually see a banner for these settings. */
const bannerVisible = (settings: SiteSettings) => Boolean(settings.bannerEnabled) && hasMessage(settings);

export const RuntimeSettingsPage: FC = () => {
    const { pageSettings } = useContext(MainContext);
    const { data: settings, isLoading: loading, error: loadError } = useSettings();
    const { mutate: saveSettings, isPending: saving } = useUpdateSettings();

    const contributedSections = pageSettings.elements.adminSettingsSections;
    const sections = useMemo(() => withContributedSections(contributedSections), [contributedSections]);
    // A section the server cannot honour, such as Analytics where none is configured, is left out
    // rather than shown with a switch the save would refuse. Until the settings load there is
    // nothing to judge it by, so it waits for them.
    const visibleSections = sections.filter(
        section => !section.available || (settings !== undefined && section.available(settings))
    );
    // `bannerDismissId` is not edited: it is stamped into the patch at save time, when the admin
    // asks for the banner to be shown again to everyone who dismissed it.
    const settingKeys = useMemo<string[]>(
        () => [...sections.flatMap(section => section.flags.map(flag => flag.key)), 'maxExtensionSize', ...BANNER_KEYS],
        [sections]
    );

    const [draftSettings, setDraftSettings] = useState<Settings | null>(null);
    const [showAgain, setShowAgain] = useState(true);
    // The field holds what was typed, not the draft's number rendered back. Deriving it meant an
    // emptied field parsed as 0 and was immediately rewritten as "0", so it could not be cleared.
    const [sizeInput, setSizeInput] = useState('');
    const [errorDismissed, setErrorDismissed] = useState(false);
    const [notifications, setNotifications] = useState<NotificationState[]>([]);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const { saved: saveSuccess, flash: flashSaved, clear: clearSaved } = useSavedFlash(2000);

    // Keep the editable draft in sync with the loaded (and freshly saved) settings.
    useEffect(() => {
        if (settings) {
            setDraftSettings(settings);
            setSizeInput(String((settings.maxExtensionSize ?? 0) / BYTES_PER_MB));
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
        (key: string) => (_event: ChangeEvent<HTMLInputElement>, checked: boolean) => {
            setDraftSettings(current => (current ? { ...current, [key]: checked } : current));
            clearSaved();
        },
        [clearSaved]
    );

    const handleBannerChange = useCallback(
        (patch: SiteSettings) => {
            setDraftSettings(current => (current ? { ...current, ...patch } : current));
            clearSaved();
        },
        [clearSaved]
    );

    const handleMaxExtensionSizeChange = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => {
            const typed = event.target.value;
            setSizeInput(typed);
            // Number, not parseInt: parseInt stops at the first non-digit, so 1.5 would be stored as
            // 1 MB and 1e3 as 1 MB while the field kept showing what was typed. An empty field is not
            // zero either - Number('') is - so it is held as invalid until something is typed.
            const mb = typed.trim() === '' ? Number.NaN : Number(typed);
            const bytes = Number.isFinite(mb) ? mb * BYTES_PER_MB : Number.NaN;
            setDraftSettings(current => (current ? { ...current, maxExtensionSize: bytes } : current));
            clearSaved();
        },
        [clearSaved]
    );

    const edited = draftSettings !== null && settings != null;
    const changed = (key: string) => edited && valueOf(draftSettings, key) !== valueOf(settings, key);
    const hasChanges = settingKeys.some(changed);
    const bannerChanged = BANNER_KEYS.some(changed);
    const sectionChanged = (section: ResolvedSettingsSection) => section.flags.some(flag => changed(flag.key));
    // The server rotates the token itself for a banner that was not there before, and nobody
    // dismisses a banner they cannot see. In between, showing it again is the admin's call.
    const canShowAgain = bannerChanged && bannerVisible(draftSettings ?? {}) && hasMessage(settings ?? {});

    // The offer going away takes the answer with it, so the box is checked again next time it appears.
    useEffect(() => {
        if (!canShowAgain) setShowAgain(true);
    }, [canShowAgain]);

    const maxExtensionSizeChanged = edited && draftSettings.maxExtensionSize !== settings.maxExtensionSize;
    // Only validated once the admin has actually edited it, because only then is it sent. The server
    // stores the limit as a long, and one beyond JavaScript's safe-integer range would otherwise fail
    // this check on arrival and block every unrelated setting from being saved.
    const maxExtensionSizeValid =
        !maxExtensionSizeChanged ||
        (Number.isSafeInteger(draftSettings.maxExtensionSize) &&
            draftSettings.maxExtensionSize > 0 &&
            draftSettings.maxExtensionSize <= draftSettings.maxOverrideSize);

    const handleSaveClick = () => setConfirmOpen(true);

    const handleConfirmClose = () => setConfirmOpen(false);

    const handleConfirmSave = useCallback(() => {
        if (!draftSettings || !settings) return;
        setConfirmOpen(false);
        // Only the settings this admin changed, so a save doesn't revert what another admin
        // changed while this page was open.
        const patch: Partial<Settings> = {};
        for (const key of settingKeys) {
            if (valueOf(draftSettings, key) !== valueOf(settings, key)) {
                Object.assign(patch, { [key]: valueOf(draftSettings, key) });
            }
        }
        // A fresh token is what makes a dismissed banner come back; leaving the key out keeps the
        // stored one, so the banner stays hidden for whoever dismissed it.
        if (canShowAgain && showAgain) {
            patch.bannerDismissId = crypto.randomUUID();
        }
        saveSettings(patch, {
            onSuccess: flashSaved,
            onError: err => {
                addNotification({
                    message: `Failed to save runtime settings. ${handleError(err as Error)}`
                });
            }
        });
    }, [draftSettings, settings, settingKeys, canShowAgain, showAgain, saveSettings, addNotification, flashSaved]);

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

                {/* The banner and the size limit are more than a switch, so the page puts them into their
                    sections itself; every other setting is one of the section's switches. */}
                {visibleSections.map(section => (
                    <SettingsSection
                        key={section.id}
                        title={section.title}
                        description={section.description}
                        changed={section.warnOnChange && sectionChanged(section)}>
                        {section.id === 'site' && (
                            <SettingsBannerItem
                                settings={draftSettings ?? {}}
                                changed={bannerChanged}
                                canShowAgain={canShowAgain}
                                showAgain={showAgain}
                                loading={loading || !draftSettings}
                                disabled={loading || saving || !draftSettings}
                                onChange={handleBannerChange}
                                onShowAgainChange={setShowAgain}
                            />
                        )}
                        {section.id === 'publishing' && (
                            <SettingsMaxSizeItem
                                value={sizeInput}
                                ceiling={draftSettings?.maxOverrideSize ?? 0}
                                valid={maxExtensionSizeValid}
                                disabled={loading || saving || !draftSettings}
                                onChange={handleMaxExtensionSizeChange}
                            />
                        )}
                        {section.flags.map(flag => (
                            <SettingsItem
                                key={flag.key}
                                title={flag.title}
                                description={flag.description}
                                checked={draftSettings !== null && valueOf(draftSettings, flag.key) === true}
                                loading={loading || !draftSettings}
                                disabled={loading || saving || !draftSettings}
                                onChange={handleFlagChange(flag.key)}
                            />
                        ))}
                    </SettingsSection>
                ))}

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
