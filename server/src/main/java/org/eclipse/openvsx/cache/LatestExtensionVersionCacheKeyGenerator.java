/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.cache;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.VersionAlias;

@Component
public class LatestExtensionVersionCacheKeyGenerator implements KeyGenerator {

    /** The keys of both caches share the extension-id part, terminator included. */
    private final ExtensionJsonCacheKeyGenerator extensionJsonCacheKey = new ExtensionJsonCacheKeyGenerator();

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Extension extension;
        String targetPlatform;
        var preRelease = false;
        var onlyActive = false;
        var type = ExtensionVersion.Type.EXTENDED;

        if (params[0] instanceof Extension) {
            extension = (Extension) params[0];
            targetPlatform = (String) params[1];
            preRelease = (boolean) params[2];
            onlyActive = (boolean) params[3];
        } else {
            var versions = (List<ExtensionVersion>) params[0];
            var firstVersion = versions.getFirst();
            extension = firstVersion.getExtension();
            type = firstVersion.getType();
            var groupedByTargetPlatform = (boolean) params[1];
            targetPlatform = groupedByTargetPlatform ? firstVersion.getTargetPlatform() : null;
            if (params.length == 3) {
                preRelease = (boolean) params[2];
            }
        }

        return generate(extension, targetPlatform, preRelease, onlyActive, type);
    }

    public String generate(
            Extension extension,
            String targetPlatform,
            boolean preRelease,
            boolean onlyActive,
            ExtensionVersion.Type type
    ) {
        return extensionJsonCacheKey.generate(
                extension.getNamespace().getName(),
                extension.getName(),
                targetPlatform,
                VersionAlias.LATEST)
                + ",pre-release=" + preRelease + ",only-active=" + onlyActive + ",type=" + type;
    }

    /** Every key of one extension and no other; see {@link ExtensionJsonCacheKeyGenerator#generatePrefix}. */
    public String generateWildcard(Extension extension) {
        return extensionJsonCacheKey.generatePrefix(extension.getNamespace().getName(), extension.getName()) + "*";
    }
}
