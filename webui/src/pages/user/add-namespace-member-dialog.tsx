/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useRef } from 'react';
import { UserData } from '../..';
import { Namespace, NamespaceMembership, isError } from '../../extension-registry-types';
import { NamespaceDetailConfigContext } from './user-settings-namespace-detail';
import { MainContext } from '../../context';
import { AddUserDialog } from './add-user-dialog';

export interface AddMemberDialogProps {
    open: boolean;
    onClose: () => void;
    filterUsers: (user: UserData) => boolean;
    namespace: Namespace;
    members: NamespaceMembership[];
    setLoadingState: (loading: boolean) => void;
}

export const AddMemberDialog: FunctionComponent<AddMemberDialogProps> = props => {
    const config = useContext(NamespaceDetailConfigContext);
    const { service, handleError } = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());

    const existingUsers = props.members.map(m => m.user);

    const handleAddUser = async (user: UserData) => {
        try {
            if (!props.namespace) {
                return;
            }
            props.setLoadingState(true);
            const endpoint = props.namespace.roleUrl;
            const result = await service.setNamespaceMember(abortController.current, endpoint, user, config.defaultMemberRole ?? 'contributor');
            if (isError(result)) {
                throw result;
            }
            props.setLoadingState(false);
            props.onClose();
        } catch (err) {
            props.setLoadingState(false);
            handleError(err);
        }
    };

    return (
        <AddUserDialog
            open={props.open}
            title='Add Member'
            description='Enter the Login Name of the User you want to add.'
            existingUsers={existingUsers}
            filterUsers={props.filterUsers}
            onClose={props.onClose}
            onAddUser={handleAddUser}
        />
    );
};