/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

class WebResourceServiceTest {

    private final FilesCacheKeyGenerator filesCacheKeyGenerator = new FilesCacheKeyGenerator();

    @Test
    void testRejectsEntryDeclaredLargerThanLimit() throws Exception {
        var extensionDownloadPath = resourcePath("wrong-size.zip");
        var service = newService(8192L);

        assertThatThrownBy(
                () -> service.getWebResource(
                        "ns",
                        "ext",
                        null,
                        "1.0.0",
                        "extension/README.md",
                        extensionDownloadPath))
                .isExactlyInstanceOf(ErrorResultException.class)
                .hasMessage("The file extension/README.md exceeds the size limit of 8 KB.")
                .satisfies(e -> assertThat(((ErrorResultException) e).getStatus()).isEqualTo(CONTENT_TOO_LARGE));

        // rejected before any extraction: nothing should have been written to the file cache
        var cachedPath = filesCacheKeyGenerator
                .generateCachedWebResourcePath("ns", "ext", null, "1.0.0", "extension/README.md", ".md");
        assertThat(Files.exists(cachedPath)).isFalse();
    }

    @Test
    void testStopsCopyWhenActualBytesExceedDeclaredSize() throws Exception {
        // extension/package.json declares a size of 0 but its compressed payload decompresses to
        // more than 0 bytes, simulating a zip entry whose header lies about the decompressed size
        var extensionDownloadPath = resourcePath("wrong-size.zip");
        var service = newService(33_554_432L);

        assertThatThrownBy(
                () -> service.getWebResource(
                        "ns",
                        "ext",
                        null,
                        "1.0.0",
                        "extension/package.json",
                        extensionDownloadPath))
                .isExactlyInstanceOf(ErrorResultException.class)
                .hasMessageContaining("File size exceeds limit of 0 bytes")
                .satisfies(e -> assertThat(((ErrorResultException) e).getStatus()).isEqualTo(INTERNAL_SERVER_ERROR));

        // the partial file written before the limit tripped must not be left behind
        var cachedPath = filesCacheKeyGenerator
                .generateCachedWebResourcePath("ns", "ext", null, "1.0.0", "extension/package.json", ".json");
        assertThat(Files.exists(cachedPath)).isFalse();
    }

    @Test
    void testAllowsLargeFileWhenLimitIsDisabled() throws Exception {
        // extension/README.md declares 31781 bytes -- larger than the 8 KB limit rejected in
        // testRejectsEntryDeclaredLargerThanLimit -- but a negative maxFileSize (the default) means
        // no file is too large, matching the pre-existing behavior of this endpoint.
        var extensionDownloadPath = resourcePath("wrong-size.zip");
        var service = newService(-1L);

        var cachedPath = service.getWebResource(
                "ns",
                "ext",
                null,
                "1.0.0",
                "extension/README.md",
                extensionDownloadPath);

        assertThat(cachedPath).isNotNull();
        assertThat(Files.size(cachedPath)).isEqualTo(31781);
        Files.deleteIfExists(cachedPath);
    }

    @Test
    void testServesFileWithinLimit() throws Exception {
        var extensionDownloadPath = resourcePath("todo-tree.zip");
        var service = newService(33_554_432L);

        var cachedPath = service.getWebResource(
                "ns",
                "ext",
                null,
                "1.0.0",
                "extension/package.json",
                extensionDownloadPath);

        assertThat(cachedPath).isNotNull();
        assertThat(Files.size(cachedPath)).isEqualTo(44712);
        Files.deleteIfExists(cachedPath);
    }

    private WebResourceService newService(long maxFileSize) {
        return new WebResourceService(
                mock(StorageUtilService.class),
                mock(RepositoryService.class),
                mock(CacheService.class),
                filesCacheKeyGenerator,
                maxFileSize);
    }

    private Path resourcePath(String name) throws Exception {
        var url = getClass().getResource("/org/eclipse/openvsx/util/" + name);
        assertThat(url).isNotNull();
        return Paths.get(url.toURI());
    }
}
