/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.cache;

import java.lang.reflect.Method;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.VersionAlias;

@Component
public class ExtensionJsonCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        var version = params.length == 4 ? (String) params[3] : VersionAlias.LATEST;
        return generate((String) params[0], (String) params[1], (String) params[2], version);
    }

    public String generate(String namespaceName, String extensionName, String targetPlatform, String version) {
        return NamingUtil.toFileFormat(
                StringUtils.lowerCase(namespaceName),
                StringUtils.lowerCase(extensionName),
                targetPlatform,
                version);
    }

    /**
     * Matches every key of one extension: its id, the {@code -} that starts the version, then
     * anything.
     * <p>
     * The separator matters. Without it {@code foo.bar*} also matches {@code foo.bar2-1.0.0}, and
     * clearing one extension would drop a sibling's entries too. It does not make the pattern exact -
     * {@code foo.bar-*} still matches the keys of an extension named {@code bar-baz}, because a
     * version may itself contain a {@code -} and there is nothing to tell the two apart. What is left
     * is over-eviction between same-prefix siblings of one namespace, which costs a recomputation
     * rather than a wrong answer; making it exact needs a key separator that a name cannot contain.
     */
    public String generateWildcard(Extension extension) {
        var extensionName = StringUtils.lowerCase(extension.getName());
        var namespaceName = StringUtils.lowerCase(extension.getNamespace().getName());
        return NamingUtil.toExtensionId(namespaceName, extensionName) + "-*";
    }
}
