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
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { MainContext } from '../../../context';

/**
 * Loads a page of admin logs. `keepPreviousData` keeps the current rows on
 * screen while the next page (or a changed period filter) is fetched, so the
 * grid shows a loading overlay rather than flashing empty.
 */
export const useLogs = (page: number, size: number, period?: string) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: ['admin', 'logs', page, size, period ?? null] as const,
        queryFn: ({ signal }) => service.admin.getLogs(signal, page, size, period),
        placeholderData: keepPreviousData
    });
};
