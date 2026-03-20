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

import { FC, type ReactNode } from 'react';
import {
    Box,
    Typography,
    Paper,
    type PaperProps,
    Divider,
    Avatar,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
} from '@mui/material';
import type { UserData } from '../../../extension-registry-types';

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export interface MembersProps {
    users: UserData[];
    headerAction?: ReactNode;
    renderUserAction?: (user: UserData) => ReactNode;
    renderUserPrimary?: (user: UserData) => ReactNode;
}

export const Members: FC<MembersProps> = ({ users, headerAction, renderUserAction, renderUserPrimary }) => (
    <Paper {...sectionPaperProps}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
            <Typography variant='h6'>Members</Typography>
            {headerAction && <Box sx={{ ml: 'auto' }}>{headerAction}</Box>}
        </Box>
        <Divider sx={{ mb: 1 }} />
        {users.length === 0 ? (
            <Typography variant='body2' color='text.secondary' sx={{ py: 1 }}>
                No members assigned to this customer.
            </Typography>
        ) : (
            <List dense disablePadding>
                {users.map(user => (
                    <ListItem
                        key={`${user.loginName}-${user.provider}`}
                        secondaryAction={renderUserAction?.(user)}
                    >
                        <ListItemAvatar>
                            <Avatar src={user.avatarUrl} sx={{ width: 32, height: 32 }} />
                        </ListItemAvatar>
                        <ListItemText
                            primary={renderUserPrimary ? renderUserPrimary(user) : user.loginName}
                            secondary={user.fullName}
                        />
                    </ListItem>
                ))}
            </List>
        )}
    </Paper>
);
