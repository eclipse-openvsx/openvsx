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

import { FunctionComponent, useContext, useState } from 'react';
import {
    Alert,
    Avatar,
    Box,
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
import { useIsMutating } from '@tanstack/react-query';
import GitHubIcon from '@mui/icons-material/GitHub';
import PersonIcon from '@mui/icons-material/Person';
import { AdminPermission, UserRelationships } from '../../../extension-registry-types';
import { ErrorResponse } from '../../../server-request';
import { MainContext } from '../../../context';
import { handleError as formatError } from '../../../utils';
import {
    accessControlMutationKey,
    type AccessRole,
    useUpdateUserPermission,
    useUpdateUserRole
} from './use-access-control';

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
 */
export const AccessControlDetails: FunctionComponent<{ entry: UserRelationships }> = ({ entry }) => {
    const { user } = entry;
    const { user: currentUser } = useContext(MainContext);
    const isCurrentUser = currentUser?.loginName === user.loginName && currentUser?.provider === user.provider;

    const [selectedRole, setSelectedRole] = useState<AccessRole>(() => (user.role as AccessRole) ?? 'none');
    const [permissions, setPermissions] = useState<Set<AdminPermission>>(() => new Set(user.permissions ?? []));
    const updateRole = useUpdateUserRole();
    const updatePermission = useUpdateUserPermission();
    const busy = useIsMutating({ mutationKey: accessControlMutationKey }) > 0;
    const isAdmin = selectedRole === 'admin';

    const handleRoleChange = (role: AccessRole) => {
        // What is actually in effect, which is not user.role once a change has succeeded: this entry
        // comes from the search result and is never refetched while the component stays mounted, so
        // reverting to it would show a role the server no longer holds.
        const roleInEffect = selectedRole;
        if (role === roleInEffect || !user.provider) {
            return;
        }
        // Optimistic: reflect the choice immediately, revert if the save fails.
        setSelectedRole(role);
        updateRole.mutate(
            { provider: user.provider, login: user.loginName, role },
            { onError: () => setSelectedRole(roleInEffect) }
        );
    };

    const handlePermissionToggle = (permission: AdminPermission, grant: boolean) => {
        if (!user.provider) {
            return;
        }
        setPermissions(current => {
            const next = new Set(current);
            if (grant) {
                next.add(permission);
            } else {
                next.delete(permission);
            }
            return next;
        });
        updatePermission.mutate(
            { provider: user.provider, login: user.loginName, permission, grant },
            {
                onError: () =>
                    setPermissions(current => {
                        const reverted = new Set(current);
                        if (grant) {
                            reverted.delete(permission);
                        } else {
                            reverted.add(permission);
                        }
                        return reverted;
                    })
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

            {(updateRole.isError || updatePermission.isError) && (
                <Alert
                    severity='error'
                    onClose={() => {
                        updateRole.reset();
                        updatePermission.reset();
                    }}>
                    {formatError((updateRole.error ?? updatePermission.error) as Error | Partial<ErrorResponse>)}
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
                    value={selectedRole}
                    disabled={!user.provider || busy}
                    onChange={(_event, value) => value && handleRoleChange(value)}>
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
        </Paper>
    );
};
