/*******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
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
package org.eclipse.openvsx.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    private static final Map<Path, Object> LOCKS;

    static {
        var MAX_SIZE = 100;
        LOCKS = Collections.synchronizedMap(new LinkedHashMap<>(MAX_SIZE) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_SIZE;
            }
        });
    }

    private FileUtil() {
    }

    /***
     * Write to file synchronously, if it doesn't already exist. If the writer fails partway
     * through, the partial file is deleted so a later call doesn't mistake it for a completed
     * write (writeSync only (re)invokes the writer when the path doesn't yet exist) and so it
     * doesn't linger on disk.
     * @param path File path to write to
     * @param writer Writes to file
     */
    public static void writeSync(Path path, Consumer<Path> writer) {
        Object lock;
        synchronized (LOCKS) {
            lock = LOCKS.computeIfAbsent(path, key -> new Object());
        }
        synchronized (lock) {
            if (!Files.exists(path)) {
                try {
                    writer.accept(path);
                } catch (RuntimeException e) {
                    deleteQuietly(path);
                    throw e;
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed to delete partial file {}", path, e);
        }
    }
}
