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

import { useContext, useEffect } from 'react';
import { MainContext } from '../context';

/**
 * Passes a TanStack Query or mutation result through untouched, reporting any
 * error to the global error dialog. Wrap a hook's definition in it, so its
 * callers never report errors themselves:
 *
 *     return useReportedQuery(useQuery({ queryKey, queryFn }));
 *
 * `ignore` leaves out the errors a page renders itself, such as a 404 shown as
 * a not-found state; pass a stable function, since a new one reports again.
 */
export const useReportedQuery = <Q extends { error: Error | null }>(
    query: Q,
    options?: { ignore?: (error: Error) => boolean }
): Q => {
    const { handleError } = useContext(MainContext);
    const { error } = query;
    const ignore = options?.ignore;
    useEffect(() => {
        if (error && !ignore?.(error)) {
            handleError(error);
        }
    }, [error, ignore, handleError]);
    return query;
};
