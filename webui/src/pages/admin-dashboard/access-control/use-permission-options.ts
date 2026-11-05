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
import { useQuery } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import { useReportedQuery } from '../../../hooks/use-reported-query';

/** The permissions the server can grant, in its order. */
export const usePermissionOptions = () => {
    const { service } = useContext(MainContext);
    return useReportedQuery(
        useQuery({
            queryKey: ['admin', 'permissions'],
            queryFn: ({ signal }) => service.admin.getPermissions(signal)
        }),
        { action: 'load the permissions' }
    );
};
