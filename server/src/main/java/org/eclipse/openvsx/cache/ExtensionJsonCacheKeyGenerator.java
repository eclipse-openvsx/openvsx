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
import java.nio.charset.StandardCharsets;

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

    /** What a name may contain besides letters and digits, from {@code ExtensionValidator}. */
    private static final String SAFE_PUNCTUATION = "_-+$~";

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

    /**
     * Percent-encodes everything outside the character set a name is allowed to use, so that a name
     * which was never held to it cannot change what a key means.
     * <p>
     * Escaping only the terminator would not be enough. A key prefix becomes a Redis glob, where
     * {@code * ? [ ] \} are syntax: a name like {@code bar*} would turn {@code foo.bar*:*} into a
     * pattern that also sweeps {@code foo.bar-baz}. Encoding every character that is not in
     * {@code [a-z0-9_+$~-]} leaves every valid name untouched and makes the pattern literal whatever
     * the stored name happens to be.
     */
    private static String escape(String name) {
        var escaped = new StringBuilder(name.length());
        for (var b : StringUtils.lowerCase(name).getBytes(StandardCharsets.UTF_8)) {
            var c = (char) (b & 0xFF);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || SAFE_PUNCTUATION.indexOf(c) >= 0) {
                escaped.append(c);
            } else {
                escaped.append('%').append(String.format("%02X", b & 0xFF));
            }
        }

        return escaped.toString();
    }

    /** Every key of one extension and no other; see {@link #generatePrefix}. */
    public String generateWildcard(Extension extension) {
        return generateWildcard(extension.getNamespace().getName(), extension.getName());
    }

    /**
     * From names rather than the entity, for an eviction that runs after its transaction: by then the
     * entity may be detached. See {@code AfterCommitExecutor}.
     */
    public String generateWildcard(String namespaceName, String extensionName) {
        return generatePrefix(namespaceName, extensionName) + "*";
    }
}
