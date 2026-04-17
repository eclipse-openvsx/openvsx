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

import { FunctionComponent, useEffect, useState, useContext, useRef } from 'react';
import {
    Box,
    Typography,
    Button,
    Divider,
    List,
    ListItem,
    ListItemAvatar,
    Avatar,
    ListItemText, IconButton, type PaperProps, Paper
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { AdminDashboardRoutes } from '../admin-dashboard-routes';
import { MainContext } from '../../../context';
import { CustomerMembership, Customer, UserData, isError } from '../../../extension-registry-types';
import { AddUserDialog } from '../../user/add-user-dialog';
import DeleteIcon from '@mui/icons-material/Delete';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import { createRoute } from '../../../utils';

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export const CustomerMemberList: FunctionComponent<CustomerMemberListProps> = props => {
    const { service, handleError } = useContext(MainContext);
    const [members, setMembers] = useState<CustomerMembership[]>([]);
    const [addDialogIsOpen, setAddDialogIsOpen] = useState(false);
    const abortController = useRef<AbortController>(new AbortController());

    const users = members.map(member => member.user);

    useEffect(() => {
        fetchMembers();
    }, [props.customer]);

    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

    const handleCloseAddDialog = async () => {
        setAddDialogIsOpen(false);
        fetchMembers();
    };

    const handleOpenAddDialog = async () => {
        setAddDialogIsOpen(true);
    };

    const handleAddUser = async (user: UserData) => {
        try {
            const result = await service.admin.addCustomerMember(abortController.current, props.customer.name, user);
            if (isError(result)) {
                throw result;
            }
            await fetchMembers();
        } catch (err) {
            handleError(err);
        }
    };

    const handleRemoveUser = async (user: UserData) => {
        try {
            const result = await service.admin.removeCustomerMember(abortController.current, props.customer.name, user);
            if (isError(result)) {
                throw result;
            }
            await fetchMembers();
        } catch (err) {
            handleError(err);
        }
    };

    const fetchMembers = async () => {
        try {
            const membershipList = await service.admin.getCustomerMembers(abortController.current, props.customer.name);
            const members = membershipList.customerMemberships;
            setMembers(members);
        } catch (err) {
            handleError(err);
        }
    };

    return <Paper {...sectionPaperProps}>
        <Box
            sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                mb: 1,
                flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' }
            }}
        >
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
                            <IconButton edge='end' size='small' color='error' onClick={() => handleRemoveUser(user)} title='Remove member'>
                                <DeleteIcon fontSize='small' />
                            </IconButton>
                        }
                    >
                        <ListItemAvatar>
                            <Avatar src={user.avatarUrl} sx={{ width: 32, height: 32 }} />
                        </ListItemAvatar>
                        <ListItemText
                            primary={
                                <RouterLink style={{ color: 'inherit' }} to={createRoute([AdminDashboardRoutes.PUBLISHER_ADMIN, user.loginName])}>
                                    {user.loginName}
                                </RouterLink>
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
        {/*            <UserNamespaceMember*/}
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
    </Paper>;
};

export interface CustomerMemberListProps {
    customer: Customer;
}