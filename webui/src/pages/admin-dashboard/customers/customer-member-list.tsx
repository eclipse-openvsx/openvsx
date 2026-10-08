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

import { FunctionComponent, useEffect, useState, useContext } from 'react';
import {
    Box,
    Typography,
    Button,
    Divider,
    List,
    ListItem,
    ListItemAvatar,
    Avatar,
    ListItemText,
    IconButton,
    type PaperProps,
    Paper
} from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { AdminDashboardRoutes } from '../admin-dashboard-routes';
import { MainContext } from '../../../context';
import { hasPermission } from '../../../permissions';
import { Customer, UserData } from '../../../extension-registry-types';
import { AddUserDialog } from '../../../components/add-user-dialog';
import DeleteIcon from '@mui/icons-material/Delete';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import { createRoute } from '../../../utils';
import { useAddCustomerMember, useCustomerMembers, useRemoveCustomerMember } from './use-customers';

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export const CustomerMemberList: FunctionComponent<CustomerMemberListProps> = props => {
    const { handleError, user: currentUser } = useContext(MainContext);
    const canOpenPublisher = hasPermission(currentUser, 'manage_publishers');
    const [addDialogIsOpen, setAddDialogIsOpen] = useState(false);

    const { data, error } = useCustomerMembers(props.customer.name);
    const { mutateAsync: addMember } = useAddCustomerMember(props.customer.name);
    const { mutateAsync: removeMember } = useRemoveCustomerMember(props.customer.name);

    const members = data?.customerMemberships ?? [];
    const users = members.map(member => member.user);

    // Preserve the previous behaviour of surfacing fetch failures via the global error dialog.
    useEffect(() => {
        if (error) {
            handleError(error);
        }
    }, [error, handleError]);

    const handleCloseAddDialog = () => {
        setAddDialogIsOpen(false);
    };

    const handleOpenAddDialog = async () => {
        setAddDialogIsOpen(true);
    };

    const handleAddUser = async (user: UserData) => {
        try {
            await addMember(user);
        } catch (err) {
            handleError(err);
        }
    };

    const handleRemoveUser = async (user: UserData) => {
        try {
            await removeMember(user);
        } catch (err) {
            handleError(err);
        }
    };

    return (
        <Paper {...sectionPaperProps}>
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    mb: 1,
                    flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' }
                }}>
                <Typography variant='h6'>Members</Typography>
                <Button size='small' startIcon={<PersonAddIcon />} onClick={handleOpenAddDialog}>
                    Add Member
                </Button>
            </Box>
            <Divider sx={{ mb: 1 }} />
            {members.length === 0 ? (
                <Typography variant='body2' color='text.secondary' sx={{ py: 1 }}>
                    No members assigned to this customer.
                </Typography>
            ) : (
                <List dense disablePadding>
                    {users.map(user => (
                        <ListItem
                            key={`${user.loginName}-${user.provider}`}
                            secondaryAction={
                                <IconButton
                                    edge='end'
                                    size='small'
                                    color='error'
                                    onClick={() => handleRemoveUser(user)}
                                    title='Remove member'>
                                    <DeleteIcon fontSize='small' />
                                </IconButton>
                            }>
                            <ListItemAvatar>
                                <Avatar src={user.avatarUrl} sx={{ width: 32, height: 32 }} />
                            </ListItemAvatar>
                            <ListItemText
                                primary={
                                    // Publisher admin is the only page this could link to, and there is no
                                    // public equivalent - so without the permission it stays plain text.
                                    canOpenPublisher ? (
                                        <RouterLink
                                            style={{ color: 'inherit' }}
                                            to={createRoute([AdminDashboardRoutes.PUBLISHER_ADMIN, user.loginName])}>
                                            {user.loginName}
                                        </RouterLink>
                                    ) : (
                                        user.loginName
                                    )
                                }
                                secondary={user.fullName}
                            />
                        </ListItem>
                    ))}
                </List>
            )}

            {/*{members.length ?*/}
            {/*    <Paper elevation={3}>*/}
            {/*        {members.map(member =>*/}
            {/*            <NamespaceMemberRow*/}
            {/*                key={'nspcmbr-' + member.user.loginName + member.user.provider}*/}
            {/*                namespace={props.namespace}*/}
            {/*                member={member}*/}
            {/*                fixSelf={props.fixSelf}*/}
            {/*                onChangeRole={role => changeRole(member, role)}*/}
            {/*                onRemoveUser={() => changeRole(member, 'remove')} />)}*/}
            {/*    </Paper> :*/}
            {/*    <Typography variant='body1'>There are no members assigned yet.</Typography>}*/}

            <AddUserDialog
                open={addDialogIsOpen}
                title='Add Member'
                description='Search for a user by login name to add them to this customer.'
                existingUsers={members.map(member => member.user)}
                onClose={handleCloseAddDialog}
                onAddUser={handleAddUser}
            />
        </Paper>
    );
};

export interface CustomerMemberListProps {
    customer: Customer;
}
