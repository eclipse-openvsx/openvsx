/********************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpiredFileListener implements RemovalListener<Object, Object> {
    protected final Logger logger = LoggerFactory.getLogger(ExpiredFileListener.class);

    @Override
    public void onRemoval(@Nullable Object key, @Nullable Object value, @NonNull RemovalCause cause) {
        logger.debug("File removal cache event: {} | key: {} | value: {}", cause, key, value);
        if (!(value instanceof Path path)) {
            return;
        }

        // If the RemovalCause is REPLACED, issue a warning as this should not happen
        // File caches need to use @Cacheable(sync = true) to avoid that values are replaced due to
        // concurrent requests for the same cache key which then would delete the cached file,
        // leading to undefined behavior as the filename is derived from the key
        if (cause == RemovalCause.REPLACED) {
            logger.warn("File removal cache event: {} | key: {} | value: {}, file NOT deleted", cause, key, value);
            return;
        }

        try {
            var deleted = Files.deleteIfExists(path);
            if (deleted) {
                logger.debug("Deleted expired file {} successfully", path);
            } else {
                logger.warn("Did NOT delete expired file {}, not present anymore", path);
            }
        } catch (IOException e) {
            logger.error("Failed to delete expired file", e);
        }
    }
}
