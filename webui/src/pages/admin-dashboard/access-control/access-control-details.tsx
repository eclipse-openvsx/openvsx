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

import { FunctionComponent, useContext, useEffect, useState } from 'react';
import {
    Alert,
    Avatar,
    Box,
    Button,
    Checkbox,
    Chip,
    Divider,
    FormControlLabel,
    FormGroup,
    LinearProgress,
    Paper,
    Stack,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from '@mui/material';
import GitHubIcon from '@mui/icons-material/GitHub';
import PersonIcon from '@mui/icons-material/Person';
import { AdminPermission, UserRelationships } from '../../../extension-registry-types';
import { ErrorResponse } from '../../../server-request';
import { MainContext } from '../../../context';
import { SaveButton } from '../../../components/save-button';
import { useSavedFlash } from '../../../hooks/use-saved-flash';
import { handleError as formatError } from '../../../utils';
import { type AccessRole, useUpdateUserAccess } from './use-access-control';

// Ordered as an escalating permission scale, low → high.
const ROLE_OPTIONS: { value: AccessRole; label: string }[] = [
    { value: 'none', label: 'No role' },
    { value: 'privileged', label: 'Privileged' },
    { value: 'admin', label: 'Admin' }
];

const PERMISSION_OPTIONS: { value: AdminPermission; label: string }[] = [
    { value: 'manage_namespaces', label: 'Manage namespaces' },
    { value: 'manage_extensions', label: 'Manage extensions' },
    { value: 'manage_publishers', label: 'Manage publishers' },
    { value: 'manage_scans', label: 'Manage scans' },
    { value: 'manage_consistency', label: 'Manage data consistency' },
    { value: 'manage_rate_limits', label: 'Manage rate limiting' },
    { value: 'manage_caches', label: 'Manage caches' },
    { value: 'manage_search_index', label: 'Manage search index' },
    { value: 'manage_settings', label: 'Manage settings' },
    { value: 'view_reports', label: 'View reports' }
];

/**
 * The details card for the user selected in Access Control search: their global role and
 * individually granted permissions. Both are edited here, nowhere else - see the comment on the
 * Publisher admin page's role chip.
 *
 * Edits are drafted and applied together on Save, as one request: role and permissions are one
 * decision, and a half-applied one would leave a user with access nobody chose to give them.
 */
export const AccessControlDetails: FunctionComponent<AccessControlDetailsProps> = ({ entry, onDirtyChange }) => {
    const { user } = entry;
    const { user: currentUser, updateUser } = useContext(MainContext);
    const isCurrentUser = currentUser?.loginName === user.loginName && currentUser?.provider === user.provider;

    // What the server holds, as far as this card knows. Not read back from `entry`, which comes from
    // the search result and is never refetched while this stays mounted - after a save it would name
    // access the server gave up on.
    const [savedRole, setSavedRole] = useState<AccessRole>(() => (user.role as AccessRole) ?? 'none');
    const [savedPermissions, setSavedPermissions] = useState(() => new Set(user.permissions ?? []));
    const [role, setRole] = useState(savedRole);
    const [permissions, setPermissions] = useState(savedPermissions);
    const [error, setError] = useState<string | null>(null);

    const updateAccess = useUpdateUserAccess();
    const { saved, flash: flashSaved, clear: clearSaved } = useSavedFlash(2000);
    const busy = updateAccess.isPending;
    const isAdmin = role === 'admin';

    // Compared as whole sets rather than over PERMISSION_OPTIONS: a permission this build has no
    // checkbox for is still carried in the draft, and so still saved back rather than revoked.
    const dirty =
        role !== savedRole ||
        permissions.size !== savedPermissions.size ||
        Array.from(permissions).some(p => !savedPermissions.has(p));

    useEffect(() => {
        onDirtyChange?.(dirty);
        return () => onDirtyChange?.(false);
    }, [dirty, onDirtyChange]);

    const handlePermissionToggle = (permission: AdminPermission, grant: boolean) => {
        clearSaved();
        setPermissions(current => {
            const next = new Set(current);
            if (grant) {
                next.add(permission);
            } else {
                next.delete(permission);
            }
            return next;
        });
    };

    const handleReset = () => {
        setRole(savedRole);
        setPermissions(new Set(savedPermissions));
        setError(null);
        clearSaved();
    };

    const handleSave = () => {
        if (!user.provider || !dirty) {
            return;
        }
        setError(null);
        updateAccess.mutate(
            {
                provider: user.provider,
                login: user.loginName,
                access: { role, permissions: Array.from(permissions) }
            },
            {
                onSuccess: () => {
                    setSavedRole(role);
                    setSavedPermissions(new Set(permissions));
                    flashSaved();
                    // MainContext.user is read once at startup, so an admin who just changed their
                    // own access would keep the menu and dashboard they no longer qualify for until
                    // a reload - every page of it answering 403.
                    if (isCurrentUser) {
                        updateUser();
                    }
                },
                onError: err => setError(formatError(err as Error | Partial<ErrorResponse>))
            }
        );
    };

    return (
        <Paper
            variant='outlined'
            sx={{
                position: 'relative',
                overflow: 'hidden',
                p: { xs: 2, md: 3 },
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                gap: 3
            }}>
            {busy && <LinearProgress color='secondary' sx={{ position: 'absolute', top: 0, left: 0, right: 0 }} />}

            <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                sx={{ minWidth: 0, alignItems: { md: 'center' } }}>
                <Avatar variant='rounded' src={user.avatarUrl} sx={{ width: 56, height: 56 }} />
                <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Stack direction='row' spacing={1} alignItems='center' useFlexGap sx={{ flexWrap: 'wrap' }}>
                        <Typography variant='h6' noWrap>
                            {user.loginName}
                        </Typography>
                        <Chip
                            icon={
                                user.provider === 'github' ? (
                                    <GitHubIcon fontSize='small' />
                                ) : (
                                    <PersonIcon fontSize='small' />
                                )
                            }
                            label={user.provider ?? '—'}
                            size='small'
                            variant='outlined'
                        />
                        {isCurrentUser && (
                            <Chip
                                label='you'
                                size='small'
                                color='info'
                                variant='outlined'
                                sx={{ height: 18, '& .MuiChip-label': { px: 0.5, fontSize: '0.65rem' } }}
                            />
                        )}
                    </Stack>
                    <Typography variant='body2' color='text.secondary' noWrap>
                        {user.fullName || '—'}
                    </Typography>
                </Box>
            </Stack>

            {error && (
                <Alert severity='error' onClose={() => setError(null)}>
                    {error}
                </Alert>
            )}

            <Box>
                <Typography variant='subtitle2' sx={{ mb: 1 }}>
                    Role
                </Typography>
                <ToggleButtonGroup
                    exclusive
                    size='small'
                    color='primary'
                    value={role}
                    disabled={!user.provider || busy}
                    onChange={(_event, value) => {
                        if (value) {
                            clearSaved();
                            setRole(value);
                        }
                    }}>
                    {ROLE_OPTIONS.map(o => (
                        <ToggleButton key={o.value} value={o.value} sx={{ textTransform: 'none', px: 1.5 }}>
                            {o.label}
                        </ToggleButton>
                    ))}
                </ToggleButtonGroup>
            </Box>

            <Divider />

            <Box>
                <Typography variant='subtitle2'>Permissions</Typography>
                <Typography variant='body2' color='text.secondary' sx={{ mb: 1 }}>
                    {isAdmin
                        ? 'Admin implies every permission, including ones added later - individual grants have no effect.'
                        : 'Individual admin capabilities granted to this user.'}
                </Typography>
                <FormGroup>
                    {PERMISSION_OPTIONS.map(o => (
                        <FormControlLabel
                            key={o.value}
                            control={
                                <Checkbox
                                    checked={isAdmin || permissions.has(o.value)}
                                    disabled={!user.provider || busy || isAdmin}
                                    onChange={e => handlePermissionToggle(o.value, e.target.checked)}
                                />
                            }
                            label={o.label}
                        />
                    ))}
                </FormGroup>
            </Box>

            <Stack direction='row' spacing={1} sx={{ justifyContent: 'flex-end' }}>
                <Button onClick={handleReset} disabled={!dirty || busy}>
                    Reset
                </Button>
                <SaveButton saved={saved} disabled={!user.provider || !dirty || busy} onClick={handleSave} />
            </Stack>
        </Paper>
    );
};

export interface AccessControlDetailsProps {
    entry: UserRelationships;
    /** Lets the page warn before a selection change would discard edits made here. */
    onDirtyChange?: (dirty: boolean) => void;
}
