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
package org.eclipse.openvsx.cache;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileSizeWeigherTest {

    private final FileSizeWeigher weigher = new FileSizeWeigher(0);

    @Test
    void testWeighsByFileSize(@TempDir Path tmpDir) throws Exception {
        var path = tmpDir.resolve("resource.bin");
        Files.write(path, new byte[1234]);

        assertThat(weigher.weigh("key", path)).isEqualTo(1234);
    }

    @Test
    void testWeighsMissingFileAsMax() {
        var path = Path.of("/does/not/exist");

        assertThat(weigher.weigh("key", path)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testWeighsNonPathValueAsOne() {
        assertThat(weigher.weigh("key", "not-a-path")).isEqualTo(1);
    }

    @Test
    void testFloorsSmallFilesToTheFloorWeight(@TempDir Path tmpDir) throws Exception {
        // Simulates a cache sized for at most 10 entries out of a 10_000 byte budget: a file far
        // smaller than its 1000-byte share is still charged the full share, so 10 of them exhaust
        // the budget and a maximumWeight(10_000) cache can never hold more than 10 entries.
        var path = tmpDir.resolve("tiny.bin");
        Files.write(path, new byte[10]);
        var weigher = new FileSizeWeigher(1000);

        assertThat(weigher.weigh("key", path)).isEqualTo(1000);
    }

    @Test
    void testStillWeighsByActualSizeWhenAboveTheFloor(@TempDir Path tmpDir) throws Exception {
        // A file bigger than its floor share is charged its real size, so the byte budget is still
        // the binding constraint for entries that are individually large.
        var path = tmpDir.resolve("large.bin");
        Files.write(path, new byte[5000]);
        var weigher = new FileSizeWeigher(1000);

        assertThat(weigher.weigh("key", path)).isEqualTo(5000);
    }
}
