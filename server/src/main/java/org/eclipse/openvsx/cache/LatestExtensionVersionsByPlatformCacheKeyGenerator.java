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
package org.eclipse.openvsx.cache;

import java.lang.reflect.Method;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;

@Component
public class LatestExtensionVersionsByPlatformCacheKeyGenerator implements KeyGenerator {

    /** The keys of these caches share the extension-id part, terminator included. */
    private final ExtensionJsonCacheKeyGenerator extensionJsonCacheKey = new ExtensionJsonCacheKeyGenerator();

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Extension extension;
        var preRelease = false;

        if (params[0] instanceof Extension) {
            extension = (Extension) params[0];
            preRelease = (boolean) params[1];
        } else {
            throw new IllegalStateException("unexpected method parameters");
        }

        return generate(extension, preRelease);
    }

    public String generate(Extension extension, boolean preReleases) {
        return extensionJsonCacheKey.generate(
                extension.getNamespace().getName(),
                extension.getName(),
                TargetPlatform.NAME_UNIVERSAL,
                VersionAlias.LATEST)
                + ",pre-releases=" + preReleases;
    }
}
