/*******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.cache;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.adapter.WebResourceService;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.storage.IStorageService;

@Component
public class FilesCacheKeyGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (target instanceof WebResourceService) {
            var namespace = (String) params[0];
            var extension = (String) params[1];
            var targetPlatform = (String) params[2];
            var version = (String) params[3];
            var name = (String) params[4];
            return generate(namespace, extension, targetPlatform, version, name);
        }
        if (target instanceof IStorageService) {
            return generate((FileResource) params[0]);
        }

        throw new UnsupportedOperationException();
    }

    public String generate(FileResource resource) {
        var extVersion = resource.getExtension();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        return generate(
                namespace.getName(),
                extension.getName(),
                extVersion.getTargetPlatform(),
                extVersion.getVersion(),
                resource.getName());
    }

    public String generate(String namespace, String extension, String targetPlatform, String version, String name) {
        return String.join("|", namespace.toLowerCase(), extension.toLowerCase(), targetPlatform, version, name);
    }

    public Path generateCachedExtensionPath(FileResource resource) {
        var key = generate(resource);
        return generateCachedPath(key, "ce_", ".tmp");
    }

    public Path generateCachedWebResourcePath(
            String namespace,
            String extension,
            String targetPlatform,
            String version,
            String name,
            String fileExtension
    ) {
        var key = generate(namespace, extension, targetPlatform, version, name);
        return generateCachedPath(key, "cr_", fileExtension);
    }

    private Path generateCachedPath(String key, String prefix, String extension) {
        var hash = DigestUtils.sha256Hex(key);
        var tmp = System.getProperty("java.io.tmpdir");
        return Path.of(tmp, prefix + hash + extension);
    }
}
