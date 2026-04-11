/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UrlUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Component
public class LocalStorageService implements IStorageService {

    @Value("${ovsx.storage.local.directory:}")
    String storageDirectory;

    @Override
    public boolean isEnabled() {
        return !StringUtils.isEmpty(storageDirectory);
    }
    
    @PostConstruct
    public void validateStorageDirectory() throws IOException {
        if (!isEnabled()) {
            return;
        }

        var storageDirectoryPath = Path.of(storageDirectory).toAbsolutePath();

        if (!Files.exists(storageDirectoryPath)) {
            Files.createDirectories(storageDirectoryPath);
        }

        if (!Files.isDirectory(storageDirectoryPath)) {
            throw new IllegalStateException("Local storage directory '" + storageDirectory + "' is not a directory");
        }

        if (!Files.isWritable(storageDirectoryPath)) {
            throw new IllegalStateException("Local storage directory '" + storageDirectory + "' is not writable");
        }
    }

    @Override
    public void uploadFile(TempFile tempFile) {
        try {
            var filePath = getPath(tempFile.getResource());
            Files.createDirectories(filePath.getParent());
            Files.copy(tempFile.getPath(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServerErrorException("Failed to upload file", e);
        }
    }

    @Override
    public void removeFile(FileResource resource) {
        try {
            // Use deleteIfExists to handle already-deleted files gracefully.
            // Multiple deletion jobs may be queued for the same extension.
            Files.deleteIfExists(getPath(resource));
        } catch (IOException e) {
            throw new ServerErrorException("Failed to remove file", e);
        }
    }

    public ResponseEntity<StreamingResponseBody> getFile(FileResource resource) {
        var headers = getFileResponseHeaders(resource.getName());
        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream -> {
                    var path = getPath(resource);
                    try (var in = Files.newInputStream(path)) {
                        in.transferTo(outputStream);
                    }
                });
    }

    @Override
    public URI getLocation(FileResource resource) {
        return URI.create(UrlUtil.createApiFileUrl(UrlUtil.getBaseUrl(), resource.getExtension(), resource.getName()));
    }

    public ResponseEntity<StreamingResponseBody> getNamespaceLogo(Namespace namespace) {
        if(!isEnabled()) {
            throw new IllegalStateException("Cannot determine location of logo. Configure the 'ovsx.storage.local.directory' property.");
        }

        return ResponseEntity.ok()
                .headers(getFileResponseHeaders(namespace.getLogoName()))
                .body(outputStream -> {
                    var path = getLogoPath(namespace);
                    try (var in = Files.newInputStream(path)) {
                        in.transferTo(outputStream);
                    }
                });
    }
    public URI getNamespaceLogoLocation(Namespace namespace) {
        return URI.create(UrlUtil.createApiUrl(UrlUtil.getBaseUrl(), "api", namespace.getName(), "logo", namespace.getLogoName()));
    }

    private HttpHeaders getFileResponseHeaders(String fileName) {
        var headers = new HttpHeaders();
        headers.setContentType(StorageUtil.getFileType(fileName));
        if (fileName.endsWith(".vsix")) {
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        } else {
            headers.setCacheControl(StorageUtil.getCacheControl(fileName));
        }
        return headers;
    }

    @Override
    public void uploadNamespaceLogo(TempFile logoFile) {
        try {
            var filePath = getLogoPath(logoFile.getNamespace());
            Files.createDirectories(filePath);
            Files.copy(logoFile.getPath(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServerErrorException("Failed to upload namespace logo file", e);
        }
    }

    @Override
    public void removeNamespaceLogo(Namespace namespace) {
        try {
            // Use deleteIfExists to handle already-deleted files gracefully
            Files.deleteIfExists(getLogoPath(namespace));
        } catch (IOException e) {
            throw new ServerErrorException("Failed to remove namespace logo file", e);
        }
    }

    @Override
    public TempFile downloadFile(FileResource resource) throws IOException {
        var file = new TempFile("download_", "_" + resource.getName());
        Files.copy(getPath(resource), file.getPath(), StandardCopyOption.REPLACE_EXISTING);
        file.setResource(resource);
        return file;
    }

    @Override
    public void copyFiles(List<Pair<FileResource, FileResource>> pairs) {
        try {
            for (var pair : pairs) {
                Files.copy(getPath(pair.getFirst()), getPath(pair.getSecond()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyNamespaceLogo(Namespace oldNamespace, Namespace newNamespace) {
        try {
            Files.copy(getLogoPath(oldNamespace), getLogoPath(newNamespace), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getPath(FileResource resource) {
        if(!isEnabled()) {
            throw new IllegalStateException("Cannot determine location of file. Configure the 'ovsx.storage.local.directory' property.");
        }

        var extVersion = resource.getExtension();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var segments = new ArrayList<>(List.of(storageDirectory, namespace.getName(), extension.getName()));
        if(!extVersion.isUniversalTargetPlatform()) {
            segments.add(extVersion.getTargetPlatform());
        }

        segments.add(extVersion.getVersion());
        segments.addAll(List.of(resource.getName().split("/")));
        var path = String.join("/", segments);
        return Path.of(path).toAbsolutePath();
    }

    private Path getLogoPath(Namespace namespace) {
        if(!isEnabled()) {
            throw new IllegalStateException("Cannot determine location of logo. Configure the 'ovsx.storage.local.directory' property.");
        }

        var path = storageDirectory + "/" + namespace.getName() + "/logo/" + namespace.getLogoName();
        return Path.of(path).toAbsolutePath();
    }

    @Override
    public Path getCachedFile(FileResource resource) {
        return getPath(resource);
    }
}
