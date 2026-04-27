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

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.VersionAlias;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class LatestExtensionVersionsByPlatformCacheKeyGenerator implements KeyGenerator {

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
        var extensionName = extension.getName();
        var namespaceName = extension.getNamespace().getName();
        return NamingUtil.toFileFormat(namespaceName, extensionName, null, VersionAlias.LATEST) + ",pre-releases=" + preReleases;
    }
}
