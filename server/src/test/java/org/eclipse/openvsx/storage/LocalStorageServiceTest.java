/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TempFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LocalStorageServiceTest {

    @Test
    void uploadNamespaceLogo_storesTheFileUnderTheNamespaceLogoDirectory(@TempDir Path storageDirectory)
            throws Exception {
        var storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "storageDirectory", storageDirectory.toString());

        var namespace = new Namespace();
        namespace.setName("testnamespace");
        namespace.setLogoName("logo.png");

        upload(storageService, namespace, "logo-bytes-1");

        var storedLogo = storageDirectory.resolve("testnamespace/logo/logo.png");
        assertThat(storedLogo).isRegularFile();
        assertThat(Files.readString(storedLogo)).isEqualTo("logo-bytes-1");
    }

    // The first upload for a namespace happens to succeed even with the bug: Files.copy is allowed
    // to replace a target that is still an *empty* directory. Re-uploading a logo - e.g. a namespace
    // owner changing it - is what actually fails, because by then the path holds a regular file and
    // Files.createDirectories(filePath) throws FileAlreadyExistsException on it.
    @Test
    void uploadNamespaceLogo_replacesAnExistingLogo(@TempDir Path storageDirectory) throws Exception {
        var storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "storageDirectory", storageDirectory.toString());

        var namespace = new Namespace();
        namespace.setName("testnamespace");
        namespace.setLogoName("logo.png");

        upload(storageService, namespace, "logo-bytes-1");
        upload(storageService, namespace, "logo-bytes-2");

        var storedLogo = storageDirectory.resolve("testnamespace/logo/logo.png");
        assertThat(storedLogo).isRegularFile();
        assertThat(Files.readString(storedLogo)).isEqualTo("logo-bytes-2");
    }

    private void upload(LocalStorageService storageService, Namespace namespace, String content) throws Exception {
        try (var logoFile = new TempFile("logo_", ".png")) {
            Files.writeString(logoFile.getPath(), content);
            logoFile.setNamespace(namespace);

            assertThatCode(() -> storageService.uploadNamespaceLogo(logoFile)).doesNotThrowAnyException();
        }
    }
}
