/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.micrometer.observation.annotation.Observed;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.FileUtil;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.SizeLimitInputStream;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.UrlUtil;

import static org.eclipse.openvsx.cache.CacheService.*;

@Service
public class WebResourceService {
    private final Logger logger = LoggerFactory.getLogger(WebResourceService.class);

    private final StorageUtilService storageUtil;
    private final RepositoryService repositories;
    private final CacheService cache;
    private final FilesCacheKeyGenerator filesCacheKeyGenerator;
    private final JsonMapper jsonMapper;

    // Limit the decompressed size of a single served web resource. Disabled (unbounded, matching
    // pre-existing behavior) by default: unlike the handful of known-small metadata files bounded by
    // ArchiveUtil.MAX_ENTRY_SIZE at publish time, an arbitrary file requested through /vscode/unpkg
    // can legitimately be large (e.g. a bundled WASM binary), so there's no one-size-fits-all default
    // that doesn't risk rejecting something that published successfully. A negative value disables the
    // limit; set a positive byte value to opt into bounding it, e.g. to guard against a small VSIX
    // containing a highly compressed entry that could exhaust the java.io.tmpdir filesystem when
    // extracted on request.
    private final long maxFileSize;

    public WebResourceService(
            StorageUtilService storageUtil,
            RepositoryService repositories,
            CacheService cache,
            FilesCacheKeyGenerator filesCacheKeyGenerator,
            @Value("${ovsx.caching.files-webresource.max-file-size:-1}") long maxFileSize
    ) {
        this.storageUtil = storageUtil;
        this.repositories = repositories;
        this.cache = cache;
        this.filesCacheKeyGenerator = filesCacheKeyGenerator;
        this.jsonMapper = JsonMapper.shared();
        this.maxFileSize = maxFileSize;
    }

    public Path getExtensionDownload(String namespace, String extension, String targetPlatform, String version) {
        var download = repositories
                .findFileByType(namespace, extension, targetPlatform, version, FileResource.DOWNLOAD);
        if (download == null) {
            return null;
        }

        var path = storageUtil.getCachedFile(download);
        if (path != null && !Files.exists(path)) {
            logger.error("File doesn't exist {}", path);
            cache.evictExtensionFile(download);
            path = null;
        }

        return path;
    }

    @Observed
    @Cacheable(
        value = CACHE_WEB_RESOURCE_FILES,
        keyGenerator = GENERATOR_FILES,
        cacheManager = "fileCacheManager",
        sync = true
    )
    public Path getWebResource(
            String namespace,
            String extension,
            String targetPlatform,
            String version,
            String name,
            Path extensionDownloadPath
    ) {
        try (var zip = new ZipFile(extensionDownloadPath.toFile())) {
            var fileEntry = zip.getEntry(name);
            if (fileEntry != null) {
                var fileExt = getFileExtension(fileEntry);
                var file = filesCacheKeyGenerator
                        .generateCachedWebResourcePath(namespace, extension, targetPlatform, version, name, fileExt);
                writeBinaryFile(file, zip, fileEntry);
                return file;
            } else {
                return null;
            }
        } catch (IOException | UncheckedIOException e) {
            throw new ErrorResultException(
                    "Failed to read extension files for " +
                            NamingUtil.toLogFormat(namespace, extension, targetPlatform, version) + ": "
                            + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Cacheable(value = CACHE_BROWSE_EXTENSION_FILES, keyGenerator = GENERATOR_FILES, cacheManager = "fileCacheManager")
    public ArrayNode browseExtensionPackage(
            String namespace,
            String extension,
            String targetPlatform,
            String version,
            String name,
            Path extensionDownloadPath
    ) {
        try (var zip = new ZipFile(extensionDownloadPath.toFile())) {
            var dirName = getDirectoryName(name);
            var dirEntries = zip.stream()
                    .filter(entry -> entry.getName().startsWith(dirName))
                    .map(entry -> getFileInDirectory(dirName, entry))
                    .collect(Collectors.toSet());
            if (dirEntries.isEmpty()) {
                return null;
            }

            // The listed URLs are followed as-is, so the target has to survive the round trip or
            // walking into a subdirectory silently drops back to whichever version matches first.
            var versionSegment = TargetPlatform.isValid(targetPlatform) && !TargetPlatform.isUniversal(targetPlatform)
                    ? version + "+" + targetPlatform
                    : version;
            var baseUrl = UrlUtil.createApiUrl("", "vscode", "unpkg", namespace, extension, versionSegment);
            var node = jsonMapper.createArrayNode();
            for (var entry : dirEntries) {
                node.add(baseUrl + "/" + entry);
            }

            return node;
        } catch (IOException | UncheckedIOException e) {
            throw new ErrorResultException(
                    "Failed to read extension files for " +
                            NamingUtil.toLogFormat(namespace, extension, targetPlatform, version) + ": "
                            + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getFileExtension(ZipEntry fileEntry) {
        var fileExtIndex = fileEntry.getName().lastIndexOf('.');
        return fileExtIndex != -1 ? fileEntry.getName().substring(fileExtIndex) : "";
    }

    private void writeBinaryFile(Path file, ZipFile zip, ZipEntry fileEntry) {
        var declaredSize = fileEntry.getSize();
        if (maxFileSize >= 0) {
            if (declaredSize < 0) {
                throw new ErrorResultException("The file " + fileEntry.getName() + " has an unknown size.");
            }
            if (declaredSize > maxFileSize) {
                var maxSize = FileUtils.byteCountToDisplaySize(maxFileSize);
                throw new ErrorResultException(
                        "The file " + fileEntry.getName() + " exceeds the size limit of " + maxSize + ".",
                        HttpStatus.CONTENT_TOO_LARGE);
            }
        }

        FileUtil.writeSync(file, p -> {
            try (var in = zip.getInputStream(fileEntry)) {
                if (maxFileSize >= 0) {
                    // Wrap in SizeLimitInputStream bounded to the declared size: a zip entry can lie
                    // about its uncompressed size, so this stops the copy the moment more bytes than
                    // declared have been read instead of trusting the header.
                    try (var limited = new SizeLimitInputStream(in, declaredSize)) {
                        Files.copy(limited, p);
                    }
                } else {
                    Files.copy(in, p);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private String getDirectoryName(String name) {
        return name.isEmpty() || name.endsWith("/") ? name : name + "/";
    }

    private String getFileInDirectory(String dirName, ZipEntry entry) {
        var folderNameEndIndex = entry.getName().indexOf("/", dirName.length());
        return folderNameEndIndex == -1 ? entry.getName() : entry.getName().substring(0, folderNameEndIndex + 1);
    }
}
