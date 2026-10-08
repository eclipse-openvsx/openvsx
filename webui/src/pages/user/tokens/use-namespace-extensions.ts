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
import { controllerFromSignal, NO_CACHE } from '../../../query-client';

/** Names of the active extensions of a namespace; idle while no namespace is chosen. */
export const useNamespaceExtensions = (namespace: string) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: ['namespace-extensions', namespace],
        queryFn: ({ signal }) => service.getPublicNamespace(controllerFromSignal(signal), namespace),
        select: ns => Object.keys(ns.extensions),
        enabled: namespace !== '',
        ...NO_CACHE
    });
};
