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

import { FC, useState, useContext, useEffect, useRef } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
    TextField, Autocomplete, Box, Avatar
} from '@mui/material';
import type { UserData } from '../../extension-registry-types';
import { MainContext } from '../../context';

export interface AddUserDialogProps {
    open: boolean;
    title: string;
    description: string;
    existingUsers: UserData[];
    onClose: () => void;
    onAddUser: (user: UserData) => void;
    filterUsers?: (user: UserData) => boolean;
}

export const AddUserDialog: FC<AddUserDialogProps> = ({
    open,
    title,
    description,
    existingUsers,
    onClose,
    onAddUser,
    filterUsers: externalFilter
}) => {
    const { service, handleError } = useContext(MainContext);
    const [options, setOptions] = useState<UserData[]>([]);
    const [loading, setLoading] = useState(false);
    const abortController = useRef<AbortController>(new AbortController());
    const debounceTimeout = useRef<ReturnType<typeof setTimeout>>();

    useEffect(() => {
        return () => {
            abortController.current.abort();
            clearTimeout(debounceTimeout.current);
        };
    }, []);

    const isUserExcluded = (user: UserData) =>
        existingUsers.some(u => u.loginName === user.loginName && u.provider === user.provider)
        || (externalFilter && !externalFilter(user));

    const handleInputChange = (_: unknown, value: string) => {
        clearTimeout(debounceTimeout.current);
        if (!value) {
            setOptions([]);
            setLoading(false);
            return;
        }
        setLoading(true);
        debounceTimeout.current = setTimeout(async () => {
            const users = await service.getUserByName(abortController.current, value);
            if (users) {
                setOptions(users);
            }
            setLoading(false);
        }, 300);
    };

    const handleSelect = (_: unknown, user: UserData | null) => {
        if (!user) return;
        if (isUserExcluded(user)) {
            handleError({ message: `User ${user.loginName} is already added.` });
            return;
        }
        onAddUser(user);
        onClose();
    };

    const handleClose = () => {
        setOptions([]);
        onClose();
    };

    return (
        <Dialog onClose={handleClose} open={open} maxWidth='sm' fullWidth>
            <DialogTitle>{title}</DialogTitle>
            <DialogContent>
                <DialogContentText>{description}</DialogContentText>
                <Autocomplete<UserData>
                    options={options}
                    loading={loading}
                    filterOptions={(opts) => opts.filter(u => !isUserExcluded(u))}
                    getOptionLabel={(option) => option.loginName}
                    isOptionEqualToValue={(option, value) =>
                        option.loginName === value.loginName && option.provider === value.provider
                    }
                    onInputChange={handleInputChange}
                    onChange={handleSelect}
                    renderOption={(props, user) => (
                        <Box
                            component='li'
                            {...props}
                            key={user.loginName + user.provider}
                            sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}
                        >
                            <Avatar
                                variant='rounded'
                                src={user.avatarUrl}
                                sx={{ width: 36, height: 36 }}
                            />
                            <Box>
                                <Box fontWeight='bold'>{user.loginName}</Box>
                                <Box fontSize='0.75rem' color='text.secondary'>{user.fullName}</Box>
                            </Box>
                        </Box>
                    )}
                    renderInput={(params) => (
                        <TextField
                            {...params}
                            autoFocus
                            margin='dense'
                            label='Search User'
                            fullWidth
                        />
                    )}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} color='secondary'>
                    Cancel
                </Button>
            </DialogActions>
        </Dialog>
    );
};
