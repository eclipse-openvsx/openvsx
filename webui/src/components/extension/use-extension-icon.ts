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
import { MainContext } from '../../context';
import { controllerFromSignal } from '../../query-client';
import { Extension, SearchEntry } from '../../extension-registry-types';

/**
 * Loads an extension's icon as an object URL, returned as the react-query result
 * (`data` is `null` when the extension has no icon). The icon of a given version
 * is immutable, so it's cached and reused across remounts (no flash on back-nav);
 * the object URL is revoked when its query is evicted from the cache (see query-client).
 *
 * Pass `enabled: false` to hold the request back until the icon is worth loading; see
 * {@link useInView}, which is how the icon components defer everything below the fold.
 */
export const useExtensionIcon = (extension: Extension | SearchEntry, enabled = true) => {
    const { service } = useContext(MainContext);
    const targetPlatform = 'targetPlatform' in extension ? extension.targetPlatform : undefined;
    return useQuery({
        enabled,
        queryKey: [
            'extension-icon',
            extension.namespace,
            extension.name,
            extension.version,
            targetPlatform,
            extension.files?.icon
        ],
        queryFn: async ({ signal }) => {
            const icon = await service.getExtensionIcon(controllerFromSignal(signal), extension);
            // useQuery forbids `undefined`; normalise the "no icon" case to null.
            return icon ?? null;
        }
    });
};
