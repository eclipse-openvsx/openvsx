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

import { useContext } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../context';
import { MembershipRole, Namespace, UserData } from '../../extension-registry-types';
import { useReportedQuery } from '../../hooks/use-reported-query';
import { NO_CACHE } from '../../query-client';

// The members URL is part of the key: user settings and the admin dashboard read the same
// namespace's members through different endpoints.
const membersKeys = {
    all: (namespace: Namespace) => ['namespace-members', namespace.name],
    list: (namespace: Namespace) => ['namespace-members', namespace.name, namespace.membersUrl]
};

/** Loads a namespace's members. */
export const useNamespaceMembers = (namespace: Namespace) => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: membersKeys.list(namespace),
            queryFn: ({ signal }) => service.getNamespaceMembers(signal, namespace),
            ...NO_CACHE
        })
    );
};

interface SetNamespaceMemberVariables {
    namespace: Namespace;
    user: UserData;
    role: MembershipRole | 'remove';
}

/**
 * Gives a user a role in a namespace, or removes them with `'remove'`. Stays pending until the
 * member list has refreshed, so a caller's loading state covers the reload too.
 */
export const useSetNamespaceMember = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useReportedQuery(
        useMutation({
            mutationFn: ({ namespace, user, role }: SetNamespaceMemberVariables) =>
                service.setNamespaceMember(namespace.roleUrl, user, role),
            onSuccess: (_result, { namespace }) =>
                queryClient.invalidateQueries({ queryKey: membersKeys.all(namespace) })
        })
    );
};
