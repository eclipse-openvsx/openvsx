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

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.VersionAlias;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class LatestExtensionVersionCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Extension extension;
        String targetPlatform;
        var preRelease = false;
        var onlyActive = false;
        var type = ExtensionVersion.Type.EXTENDED;

        if(params[0] instanceof Extension) {
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
            if(params.length == 3) {
                preRelease = (boolean) params[2];
            }
        }

        return generate(extension, targetPlatform, preRelease, onlyActive, type);
    }

    public String generate(Extension extension, String targetPlatform, boolean preRelease, boolean onlyActive, ExtensionVersion.Type type) {
        var extensionName = extension.getName();
        var namespaceName = extension.getNamespace().getName();
        return NamingUtil.toFileFormat(namespaceName, extensionName, targetPlatform, VersionAlias.LATEST) +
                ",pre-release=" + preRelease + ",only-active=" + onlyActive + ",type=" + type;
    }

    public String generateWildcard(Extension extension) {
        var extensionName = extension.getName();
        var namespaceName = extension.getNamespace().getName();
        return NamingUtil.toExtensionId(namespaceName, extensionName) + "*";
    }
}
