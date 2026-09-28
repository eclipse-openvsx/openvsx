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
import { MainContext } from '../context';
import { controllerFromSignal } from '../query-client';

export const siteSettingsQueryKey = ['site-settings'] as const;

/**
 * Loads the site settings an admin configures in the dashboard, keyed by setting name. Public, so
 * it resolves for anonymous visitors too.
 */
export const useSiteSettings = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: siteSettingsQueryKey,
        queryFn: ({ signal }) => service.getSiteSettings(controllerFromSignal(signal))
    });
};
