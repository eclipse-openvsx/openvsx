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
import { useQuery, UseQueryOptions } from '@tanstack/react-query';
import { MainContext } from '../context';
import { UserData } from '../extension-registry-types';

/** Finds the users whose login name matches `name`. */
export const useUsersByName = (
    name: string,
    options?: Omit<UseQueryOptions<Readonly<UserData>[]>, 'queryKey' | 'queryFn'>
) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: ['users-by-name', name],
        queryFn: ({ signal }) => service.getUserByName(signal, name),
        ...options
    });
};
