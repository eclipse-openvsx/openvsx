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
import { MainContext } from '../../../context';

const checksQueryKey = ['admin', 'consistency', 'checks'] as const;
const findingsQueryKey = (checkId: string) => ['admin', 'consistency', 'checks', checkId, 'findings'] as const;

/**
 * The overview of every registered consistency check: its live findings count and its last recorded
 * (scheduled or manually triggered) run.
 */
export const useConsistencyChecks = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: checksQueryKey,
        queryFn: ({ signal }) => service.admin.getConsistencyChecks(signal)
    });
};

/**
 * The live findings of one check. Enabled lazily (only once its card is expanded) so opening the
 * overview page never has to compute every check's full finding list up front.
 */
export const useConsistencyFindings = (checkId: string, enabled: boolean) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: findingsQueryKey(checkId),
        queryFn: ({ signal }) => service.admin.getConsistencyFindings(signal, checkId),
        enabled
    });
};

/**
 * Invalidates both the overview and this check's findings - shared by every mutation below, since all
 * of them change what both would report.
 */
const useInvalidateConsistency = (checkId: string) => {
    const queryClient = useQueryClient();
    return () => {
        queryClient.invalidateQueries({ queryKey: checksQueryKey });
        queryClient.invalidateQueries({ queryKey: findingsQueryKey(checkId) });
    };
};

export const useFixAllConsistencyFindings = (checkId: string) => {
    const { service } = useContext(MainContext);
    const invalidate = useInvalidateConsistency(checkId);
    return useMutation({
        mutationFn: () => service.admin.fixConsistencyFindings(checkId),
        onSuccess: invalidate
    });
};

export const useFixConsistencyFinding = (checkId: string) => {
    const { service } = useContext(MainContext);
    const invalidate = useInvalidateConsistency(checkId);
    return useMutation({
        mutationFn: (entityId: number) => service.admin.fixConsistencyFinding(checkId, entityId),
        onSuccess: invalidate
    });
};

/**
 * Re-fetches every consistency query currently on screen - the overview and any expanded check's
 * findings. Purely a client-side cache refresh: findings are always computed live server-side on every
 * request anyway, so there is no server action to trigger, just a reason to ask again right now instead
 * of waiting for the next mount or mutation.
 */
export const useRefreshConsistency = () => {
    const queryClient = useQueryClient();
    return () => queryClient.invalidateQueries({ queryKey: ['admin', 'consistency'] });
};
