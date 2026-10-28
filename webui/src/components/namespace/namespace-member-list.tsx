/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useState, useContext } from 'react';
import { Button, Divider, Paper, Stack } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { NamespaceMemberRow } from './namespace-member-row';
import { Namespace, NamespaceMembership, MembershipRole } from '../../extension-registry-types';
import { AddMemberDialog } from './add-namespace-member-dialog';
import { MainContext } from '../../context';
import { useNamespaceMembers, useSetNamespaceMember } from './use-namespace-members';
import { EmptyPlaceholder, SectionTitleRow, SettingsSectionTitle } from '../../pages/user/settings/settings-primitives';

export const NamespaceMemberList: FunctionComponent<NamespaceMemberListProps> = props => {
    const { user } = useContext(MainContext);
    const [addDialogIsOpen, setAddDialogIsOpen] = useState(false);
    const { data: membershipList } = useNamespaceMembers(props.namespace);
    const { mutateAsync: setNamespaceMember } = useSetNamespaceMember();
    const members = membershipList?.namespaceMemberships ?? [];

    const handleCloseAddDialog = () => {
        setAddDialogIsOpen(false);
    };
    const handleOpenAddDialog = () => {
        setAddDialogIsOpen(true);
    };

    const changeRole = async (membership: NamespaceMembership, role: MembershipRole | 'remove') => {
        try {
            props.setLoadingState(true);
            await setNamespaceMember({ namespace: props.namespace, user: membership.user, role });
        } catch {
            // Reported by the hook.
        } finally {
            props.setLoadingState(false);
        }
    };

    if (!user) {
        return null;
    }
    return (
        <>
            <SectionTitleRow>
                <SettingsSectionTitle component='h3' sx={{ m: 0 }}>
                    Members
                </SettingsSectionTitle>
                <Button variant='outlined' startIcon={<AddIcon />} onClick={handleOpenAddDialog}>
                    Add member
                </Button>
            </SectionTitleRow>
            {members.length ? (
                <Paper variant='outlined'>
                    <Stack divider={<Divider />}>
                        {members.map(member => (
                            <NamespaceMemberRow
                                key={'nspcmbr-' + member.user.loginName + member.user.provider}
                                member={member}
                                onChangeRole={role => changeRole(member, role)}
                                onRemoveUser={() => changeRole(member, 'remove')}
                            />
                        ))}
                    </Stack>
                </Paper>
            ) : (
                <EmptyPlaceholder>There are no members assigned yet.</EmptyPlaceholder>
            )}
            <AddMemberDialog
                members={members}
                namespace={props.namespace}
                onClose={handleCloseAddDialog}
                open={addDialogIsOpen}
                setLoadingState={props.setLoadingState}
            />
        </>
    );
};

export interface NamespaceMemberListProps {
    namespace: Namespace;
    setLoadingState: (loadingState: boolean) => void;
}
