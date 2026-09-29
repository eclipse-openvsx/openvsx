/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

public final class ArchiveUtil {

    // Limit the size of fetched zip entries to 32 MB
    private static final long MAX_ENTRY_SIZE = 33_554_432;

    private ArchiveUtil() {
    }

    public static @Nullable ZipEntry getEntryIgnoreCase(ZipFile archive, String entryName) {
        return archive.stream()
                .filter(entry -> entry.getName().equalsIgnoreCase(entryName))
                .findAny()
                .orElse(null);
    }

    public static TempFile readEntry(ZipFile archive, String entryName) throws IOException {
        return readEntry(archive, entryName, MAX_ENTRY_SIZE);
    }

    static TempFile readEntry(ZipFile archive, String entryName, long maxEntrySize) throws IOException {
        var entry = archive.getEntry(entryName);
        return entry != null ? readEntry(archive, entry, maxEntrySize) : null;
    }

    public static TempFile readEntry(ZipFile archive, ZipEntry entry) throws IOException {
        return readEntry(archive, entry, MAX_ENTRY_SIZE);
    }

    static TempFile readEntry(ZipFile archive, ZipEntry entry, long maxEntrySize) throws IOException {
        var fileNameIndex = entry.getName().lastIndexOf('/');
        var fileName = fileNameIndex == -1 ? entry.getName() : entry.getName().substring(fileNameIndex + 1);
        var suffixIndex = fileName.lastIndexOf('.');
        var suffix = suffixIndex == -1 ? null : fileName.substring(suffixIndex);
        var prefix = suffixIndex == -1 ? fileName : fileName.substring(0, suffixIndex);
        var file = new TempFile(prefix, suffix);
        try (var out = Files.newOutputStream(file.getPath())) {
            if (entry.getSize() < 0) {
                IOUtils.closeQuietly(file);
                throw new ErrorResultException("The file " + entry.getName() + " has a negative size.");
            }

            if (entry.getSize() > maxEntrySize) {
                IOUtils.closeQuietly(file);
                var maxSize = FileUtils.byteCountToDisplaySize(maxEntrySize);
                throw new ErrorResultException(
                        "The file " + entry.getName() + " exceeds the size limit of " + maxSize + ".");
            }

            // Read at most the number of bytes as declared by the entry
            try (var is = new SizeLimitInputStream(archive.getInputStream(entry), entry.getSize())) {
                is.transferTo(out);
            } catch (IOException e) {
                IOUtils.closeQuietly(file);
                throw new ErrorResultException("Failed to read " + entry.getName() + ": " + e.getMessage());
            }
        }
        return file;
    }

    /**
     * Enforce coarse archive limits before reading content.
     */
    public static void enforceArchiveLimits(
            List<? extends ZipEntry> entries,
            int maxEntryCount,
            long maxTotalUncompressedBytes
    ) {
        if (entries.size() > maxEntryCount) {
            throw new ErrorResultException("Archive contains too many entries (" + entries.size() + ").");
        }

        long declaredTotal = 0;
        for (ZipEntry entry : entries) {
            long size = entry.getSize();
            if (size > 0) {
                declaredTotal += size;
                if (declaredTotal > maxTotalUncompressedBytes) {
                    throw new ErrorResultException(
                            "Uncompressed archive size exceeds limit of " + maxTotalUncompressedBytes + " bytes.");
                }
            }
        }
    }

    /**
     * Returns whether the given path is considered to be a safe path for a zip entry.
     * <p>
     * The following paths are not considered to be safe:
     * <ul>
     *     <li>paths containing a null byte</li>
     *     <li>absolute paths</li>
     *     <li>paths containing traversal patterns</li>
     * </ul>
     */
    public static boolean isSafePath(String filePath) {
        // 1. Null byte injection
        if (filePath.contains("\0")) {
            return false;
        }

        // 2. Absolute paths (Unix and Windows)
        if (filePath.startsWith("/") || filePath.startsWith("\\")) {
            return false;
        }

        // 3. Windows drive letters (e.g. C:\, D:/)
        if (filePath.length() >= 2 && Character.isLetter(filePath.charAt(0)) && filePath.charAt(1) == ':') {
            return false;
        }

        try {
            // 4. Resolve and check confinement (catches ../, encoded, and Unicode tricks)
            Path tmpDir = Path.of("/tmp");
            Path resolved = tmpDir.resolve(filePath).normalize();
            if (!resolved.startsWith(tmpDir)) {
                return false;
            }

            Path entryPath = Path.of(filePath);

            // 5. Suspicious segments (defense-in-depth, catches obfuscated variants)
            for (Path segment : entryPath) {
                String seg = segment.toString();
                if (seg.equals("..") || seg.startsWith("..\\") || seg.startsWith("../")) {
                    return false;
                }
            }
        } catch (InvalidPathException e) {
            return false;
        }

        return true;
    }
}
