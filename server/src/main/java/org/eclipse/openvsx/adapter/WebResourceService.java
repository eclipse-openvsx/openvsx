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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.observation.annotation.Observed;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.FileUtil;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.eclipse.openvsx.cache.CacheService.*;

@Service
public class WebResourceService {
    private final Logger logger = LoggerFactory.getLogger(WebResourceService.class);

    private final StorageUtilService storageUtil;
    private final RepositoryService repositories;
    private final CacheService cache;
    private final FilesCacheKeyGenerator filesCacheKeyGenerator;

    public WebResourceService(
            StorageUtilService storageUtil,
            RepositoryService repositories,
            CacheService cache,
            FilesCacheKeyGenerator filesCacheKeyGenerator
    ) {
        this.storageUtil = storageUtil;
        this.repositories = repositories;
        this.cache = cache;
        this.filesCacheKeyGenerator = filesCacheKeyGenerator;
    }

    public Path getExtensionDownload(String namespace, String extension, String targetPlatform, String version) {
        var download = repositories.findFileByType(namespace, extension, targetPlatform, version, FileResource.DOWNLOAD);
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
    @Cacheable(value = CACHE_WEB_RESOURCE_FILES, keyGenerator = GENERATOR_FILES, cacheManager = "fileCacheManager", sync = true)
    public Path getWebResource(String namespace, String extension, String targetPlatform, String version, String name, Path extensionDownloadPath) {
        try (var zip = new ZipFile(extensionDownloadPath.toFile())) {
            var fileEntry = zip.getEntry(name);
            if (fileEntry != null) {
                var fileExt = getFileExtension(fileEntry);
                var file = filesCacheKeyGenerator.generateCachedWebResourcePath(namespace, extension, targetPlatform, version, name, fileExt);
                writeBinaryFile(file, zip, fileEntry);
                return file;
            } else {
                return null;
            }
        } catch (IOException | UncheckedIOException e) {
            throw new ErrorResultException(
                    "Failed to read extension files for " +
                    NamingUtil.toLogFormat(namespace, extension, targetPlatform, version) + ": " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Cacheable(value = CACHE_BROWSE_EXTENSION_FILES, keyGenerator = GENERATOR_FILES, cacheManager = "fileCacheManager")
    public ArrayNode browseExtensionPackage(String namespace, String extension, String targetPlatform, String version, String name, Path extensionDownloadPath) {
        try (var zip = new ZipFile(extensionDownloadPath.toFile())) {
            var dirName = getDirectoryName(name);
            var dirEntries = zip.stream()
                    .filter(entry -> entry.getName().startsWith(dirName))
                    .map(entry -> getFileInDirectory(dirName, entry))
                    .collect(Collectors.toSet());
            if (dirEntries.isEmpty()) {
                return null;
            }

            var baseUrl = UrlUtil.createApiUrl("", "vscode", "unpkg", namespace, extension, version);
            var mapper = new ObjectMapper();
            var node = mapper.createArrayNode();
            for (var entry : dirEntries) {
                node.add(baseUrl + "/" + entry);
            }

            return node;
        } catch (IOException | UncheckedIOException e) {
            throw new ErrorResultException(
                    "Failed to read extension files for " +
                    NamingUtil.toLogFormat(namespace, extension, targetPlatform, version) + ": " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String getFileExtension(ZipEntry fileEntry) {
        var fileExtIndex = fileEntry.getName().lastIndexOf('.');
        return fileExtIndex != -1 ? fileEntry.getName().substring(fileExtIndex) : "";
    }

    private void writeBinaryFile(Path file, ZipFile zip, ZipEntry fileEntry) {
        FileUtil.writeSync(file, p -> {
            try (var in = zip.getInputStream(fileEntry)) {
                Files.copy(in, p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void writeJsonFile(Path file, ObjectMapper mapper, JsonNode node) {
        FileUtil.writeSync(file, p -> {
            try {
                mapper.writeValue(p.toFile(), node);
            } catch(IOException e) {
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
