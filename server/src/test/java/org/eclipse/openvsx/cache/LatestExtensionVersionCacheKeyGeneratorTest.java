/*******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation AISBL
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

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion.Type;
import org.eclipse.openvsx.entities.Namespace;

import static org.assertj.core.api.Assertions.assertThat;

class LatestExtensionVersionCacheKeyGeneratorTest {

    private final LatestExtensionVersionCacheKeyGenerator generator = new LatestExtensionVersionCacheKeyGenerator();

    @Test
    void shouldLowerCaseExtensionNameForCacheKey() {
        var extension = mockExtension();
        extension.setName("BAZ");
        var results = generator.generate(extension, "linux", false, false, Type.REGULAR);

        assertThat(results).startsWith("foobar.baz");
    }

    @Test
    void shouldLowerCaseNamespaceNameForCacheKey() {
        var extension = mockExtension();
        extension.getNamespace().setName("FOoBAr");
        var results = generator.generate(extension, "linux", false, false, Type.REGULAR);

        assertThat(results).startsWith("foobar.baz");
    }

    @Test
    void shouldLowerCaseExtensionNameForWildcard() {
        var extension = mockExtension();
        extension.setName("BAZ");
        var results = generator.generate(extension, "linux", false, false, Type.REGULAR);

        assertThat(results).startsWith("foobar.baz");
    }

    @Test
    void shouldLowerCaseNamespaceNameForExtension() {
        var extension = mockExtension();
        extension.getNamespace().setName("FOoBAr");
        var results = generator.generate(extension, "linux", false, false, Type.REGULAR);

        assertThat(results).startsWith("foobar.baz");
    }

    private Extension mockExtension() {
        var namespace = new Namespace();
        namespace.setName("foobar");
        var extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("baz");
        extension.setActive(true);

        return extension;
    }
}
