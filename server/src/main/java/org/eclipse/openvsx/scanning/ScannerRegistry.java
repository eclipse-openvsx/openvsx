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
package org.eclipse.openvsx.scanning;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Registry that maps scanner types to scanner implementations.
 * Supports runtime add/remove of scanners for hot-reload configuration.
 */
@Component
public class ScannerRegistry {

    private final Map<String, Scanner> scanners = new ConcurrentHashMap<>();

    /**
     * Register a scanner in the registry.
     */
    public void registerScanner(@NonNull Scanner scanner) {
        String type = scanner.getScannerType();

        // Check for duplicate scanner types
        Scanner existing = scanners.putIfAbsent(type, scanner);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Scanner type '" + type + "' is already registered by " +
                            existing.getClass().getName());
        }
    }

    /**
     * Unregister a scanner from the registry.
     */
    @Nullable
    public Scanner unregisterScanner(@NonNull String scannerType) {
        return scanners.remove(scannerType);
    }

    /**
     * Replace an existing scanner with a new instance.
     * Used when scanner configuration changes at runtime.
     */
    @Nullable
    public Scanner replaceScanner(@NonNull Scanner scanner) {
        return scanners.put(scanner.getScannerType(), scanner);
    }

    /**
     * Get a scanner by its type.
     */
    @Nullable
    public Scanner getScanner(@NonNull String scannerType) {
        return scanners.get(scannerType);
    }

    /**
     * Check if a scanner type is registered.
     */
    public boolean hasScanner(@NonNull String scannerType) {
        return scanners.containsKey(scannerType);
    }

    /**
     * Get all registered scanner types.
     */
    @NonNull
    public Set<String> getRegisteredTypes() {
        return Set.copyOf(scanners.keySet());
    }

    /**
     * Get all registered scanners.
     */
    @NonNull
    public List<Scanner> getAllScanners() {
        return List.copyOf(scanners.values());
    }

    /**
     * Get all registered async scanners (those that require polling).
     */
    @NonNull
    public List<Scanner> getAsyncScanners() {
        return scanners.values().stream()
                .filter(Scanner::isAsync)
                .toList();
    }
}
