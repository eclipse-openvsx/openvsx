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
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;

@Component
public class ExtensionJsonCacheKeyGenerator implements KeyGenerator {

    /** Ends the extension id in a cache key. See {@link #generatePrefix} for why it is this character. */
    static final String ID_TERMINATOR = ":";

    @Override
    public Object generate(Object target, Method method, Object... params) {
        var version = params.length == 4 ? (String) params[3] : VersionAlias.LATEST;
        return generate((String) params[0], (String) params[1], (String) params[2], version);
    }

    public String generate(String namespaceName, String extensionName, String targetPlatform, String version) {
        var key = new StringBuilder(prefix(namespaceName, extensionName)).append(version);
        if (!TargetPlatform.isUniversal(targetPlatform)) {
            key.append('@').append(targetPlatform);
        }

        return key.toString();
    }

    /**
     * What every key of one extension starts with, and nothing else does: its id, then a terminator.
     * <p>
     * The terminator is what makes evicting by prefix exact, and it has to be a character a name
     * cannot contain. Names are {@code [\w\-\+\$~]+} (see {@code ExtensionValidator}), so a
     * {@code -} will not do: it is legal in a name and in a version alike, which left the keys of
     * {@code foo.bar} indistinguishable from those of {@code foo.bar-baz}. A {@code :} cannot occur
     * in either, so {@code foo.bar:} matches the one extension and no other.
     * <p>
     * A name is escaped rather than trusted, because the character set is what the validator enforces
     * now and rows older than it, or mirrored from elsewhere, were never held to it.
     */
    public String generatePrefix(String namespaceName, String extensionName) {
        return prefix(namespaceName, extensionName);
    }

    private static String prefix(String namespaceName, String extensionName) {
        return NamingUtil.toExtensionId(escape(namespaceName), escape(extensionName)) + ID_TERMINATOR;
    }

    private static String escape(String name) {
        return StringUtils.lowerCase(name).replace("%", "%25").replace(ID_TERMINATOR, "%3A");
    }

    /** Every key of one extension and no other; see {@link #generatePrefix}. */
    public String generateWildcard(Extension extension) {
        return prefix(extension.getNamespace().getName(), extension.getName()) + "*";
    }
}
