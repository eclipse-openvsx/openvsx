/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext } from 'react';
import { Namespace, NamespaceMembership, UserData } from '../../extension-registry-types';
import { NamespaceDetailConfigContext } from './namespace-detail-config';
import { AddUserDialog } from '../add-user-dialog';
import { useSetNamespaceMember } from './use-namespace-members';

export interface AddMemberDialogProps {
    open: boolean;
    onClose: () => void;
    namespace: Namespace;
    members: NamespaceMembership[];
    setLoadingState: (loading: boolean) => void;
}

export const AddMemberDialog: FunctionComponent<AddMemberDialogProps> = props => {
    const config = useContext(NamespaceDetailConfigContext);
    const { mutateAsync: setNamespaceMember } = useSetNamespaceMember();

    const existingUsers = props.members.map(m => m.user);

    const handleAddUser = async (user: UserData) => {
        try {
            if (!props.namespace) {
                return;
            }
            props.setLoadingState(true);
            await setNamespaceMember({
                namespace: props.namespace,
                user,
                role: config.defaultMemberRole ?? 'contributor'
            });
        } catch {
            // Reported by the hook.
        } finally {
            props.setLoadingState(false);
        }
    };

    return (
        <AddUserDialog
            open={props.open}
            title='Add Member'
            description='Enter the Login Name of the User you want to add.'
            existingUsers={existingUsers}
            filterUsers={config.filterUsers}
            onClose={props.onClose}
            onAddUser={handleAddUser}
        />
    );
};
