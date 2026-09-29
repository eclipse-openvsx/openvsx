/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUtilTest {

    @Test
    void testDeletesPartialFileWhenWriterFails(@TempDir Path tmpDir) {
        var path = tmpDir.resolve("partial.tmp");

        assertThatThrownBy(() -> FileUtil.writeSync(path, p -> {
            try {
                Files.writeString(p, "partial content");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            throw new RuntimeException("write failed partway through");
        })).hasMessage("write failed partway through");

        assertThat(Files.exists(path)).isFalse();
    }

    @Test
    void testRetriesAfterAFailedWrite(@TempDir Path tmpDir) {
        var path = tmpDir.resolve("retry.tmp");

        assertThatThrownBy(() -> FileUtil.writeSync(path, p -> {
            throw new RuntimeException("first attempt fails");
        })).hasMessage("first attempt fails");
        assertThat(Files.exists(path)).isFalse();

        // a later call for the same path must not see a leftover partial file and skip writing
        FileUtil.writeSync(path, p -> {
            try {
                Files.writeString(p, "content");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertThat(Files.exists(path)).isTrue();
    }

    @Test
    void testDoesNotOverwriteAnExistingFile() throws IOException {
        var path = Files.createTempFile("openvsx-file-util-test", ".tmp");
        try {
            Files.writeString(path, "original");
            FileUtil.writeSync(path, p -> {
                throw new AssertionError("writer must not run when the file already exists");
            });
            assertThat(Files.readString(path)).isEqualTo("original");
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
