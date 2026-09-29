/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.benmanes.caffeine.cache.Weigher;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Weighs a cached {@link Path} by its file size in bytes, so a {@code maximumWeight} cache bounds
 * its total disk footprint instead of just the number of cached files.
 * <p>
 * Every entry is floored to at least {@code floorWeight} of the weight budget, even if its actual
 * file is smaller. Combined with a cache's {@code maximumWeight(maxTotalSize)}, setting
 * {@code floorWeight = ceil(maxTotalSize / maxEntries)} makes the same cache respect both a total
 * byte budget and a maximum entry count at once: no more than {@code maxEntries} entries can ever
 * fit, since each one claims at least a {@code 1/maxEntries} share of the budget, while entries
 * larger than that share are still weighed by their real size so the byte budget itself holds.
 */
public class FileSizeWeigher implements Weigher<Object, Object> {
    private static final Logger logger = LoggerFactory.getLogger(FileSizeWeigher.class);

    private final long floorWeight;

    /**
     * @param floorWeight the minimum weight charged to every entry, regardless of its actual file
     *                     size; pass {@code 0} to weigh purely by actual file size.
     */
    public FileSizeWeigher(long floorWeight) {
        this.floorWeight = floorWeight;
    }

    @Override
    public int weigh(@NonNull Object key, @NonNull Object value) {
        if (!(value instanceof Path path)) {
            return 1;
        }

        try {
            var size = Math.max(Files.size(path), floorWeight);
            return (int) Math.min(size, Integer.MAX_VALUE);
        } catch (IOException e) {
            // Can't determine the size, e.g. the file was already deleted; weigh it heavily so
            // it doesn't linger and displace entries whose size is known.
            logger.warn("Failed to determine size of cached file {}", path, e);
            return Integer.MAX_VALUE;
        }
    }
}
