/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage;

import java.util.concurrent.TimeUnit;

import org.springframework.http.*;

public class StorageUtil {

    private StorageUtil() {
    }

    public static CacheControl getCacheControl(String fileName) {
        // Files are requested with a version string in the URL, so their content does not change.
        // As extension versions might get deleted, cache for max 1 day and force client to revalidate
        // TODO: this is subject to change, it used to be 30 days, reduced to 1 day to prevent stale cache
        //       entries if a CDN is configured in case extensions are deleted.
        //       we need a mechanism to invalidate CDN caches
        return CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic().mustRevalidate();
    }
}
