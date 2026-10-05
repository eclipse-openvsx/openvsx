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
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import { MainContext } from '../context';
import { controllerFromSignal } from '../query-client';

const ADMIN_USER_SEARCH_PAGE_SIZE = 25;

export const adminUserSearchKeys = {
    list: (search: string, role: string) => ['admin', 'user-search', { search, role }] as const
};

/**
 * Loads the searchable, role-filterable admin user list one page at a time. Shared by Publisher
 * admin (finding a user to investigate/revoke) and Access Control (finding a user to edit role or
 * permissions for). `keepPreviousData` keeps the current rows on screen while a changed search or
 * role filter loads, matching the rest of the admin dashboard.
 */
export const useAdminUserSearch = (search: string, role: string) => {
    const { service } = useContext(MainContext);
    return useInfiniteQuery({
        queryKey: adminUserSearchKeys.list(search, role),
        queryFn: ({ pageParam, signal }) =>
            service.admin.getUsers(controllerFromSignal(signal), {
                search: search || undefined,
                role: role || undefined,
                size: ADMIN_USER_SEARCH_PAGE_SIZE,
                page: pageParam
            }),
        initialPageParam: 0,
        getNextPageParam: lastPage => {
            const { number, totalPages } = lastPage.page;
            return number + 1 < totalPages ? number + 1 : undefined;
        },
        placeholderData: keepPreviousData
    });
};
