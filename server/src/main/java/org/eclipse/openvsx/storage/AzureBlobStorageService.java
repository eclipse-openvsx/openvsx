/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.azure.core.http.policy.UserAgentPolicy;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.CopyStatusType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.FileUtil;
import org.eclipse.openvsx.util.HttpHeadersUtil;
import org.eclipse.openvsx.util.TempFile;

import static org.eclipse.openvsx.cache.CacheService.CACHE_EXTENSION_FILES;
import static org.eclipse.openvsx.cache.CacheService.GENERATOR_FILES;

@Component
public class AzureBlobStorageService implements IStorageService {

    public static final String AZURE_USER_AGENT = "OpenVSX";

    private final FilesCacheKeyGenerator filesCacheKeyGenerator;

    @Value("${ovsx.storage.azure.service-endpoint:}")
    String serviceEndpoint;

    @Value("${ovsx.storage.azure.sas-token:}")
    String sasToken;

    @Value("${ovsx.storage.azure.blob-container:openvsx-resources}")
    String blobContainer;

    private BlobContainerClient containerClient;

    public AzureBlobStorageService(FilesCacheKeyGenerator filesCacheKeyGenerator) {
        this.filesCacheKeyGenerator = filesCacheKeyGenerator;
    }

    @Override
    public boolean isEnabled() {
        return !StringUtils.isEmpty(serviceEndpoint);
    }

    public String getServiceEndpoint() {
        return serviceEndpoint;
    }

    public String getBlobContainer() {
        return blobContainer;
    }

    protected BlobContainerClient getContainerClient() {
        if (containerClient == null) {
            containerClient = new BlobContainerClientBuilder()
                    .endpoint(serviceEndpoint)
                    .sasToken(sasToken)
                    .containerName(blobContainer)
                    .addPolicy(new UserAgentPolicy(AZURE_USER_AGENT))
                    .buildClient();
        }
        return containerClient;
    }

    private String missingEndpointMessage(String name) {
        return missingEndpointMessage("Cannot determine location of file", name);
    }

    private String missingEndpointMessage(String action, String name) {
        return action + " " + name + ": missing Azure blob service endpoint";
    }

    @Override
    public void uploadFile(TempFile tempFile) {
        var resource = tempFile.getResource();
        var blobName = getObjectKey(resource);
        uploadFile(tempFile, resource.getName(), blobName);
    }

    @Override
    public void uploadNamespaceLogo(TempFile logoFile) {
        var namespace = logoFile.getNamespace();
        var blobName = getObjectKey(namespace);
        uploadFile(logoFile, namespace.getLogoName(), blobName);
    }

    protected void uploadFile(TempFile file, String fileName, String blobName) {
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage("Cannot upload file", blobName));
        }

        var headers = HttpHeadersUtil.createFileResponseHeaders(file.getPath(), fileName);

        var blobClient = getContainerClient().getBlobClient(blobName);
        var blobHeaders = new BlobHttpHeaders();

        if (headers.getContentType() != null) {
            blobHeaders.setContentType(headers.getContentType().toString());
        }

        if (StringUtils.isNotBlank(headers.getContentDisposition().toString())) {
            blobHeaders.setContentDisposition(headers.getContentDisposition().toString());
        }

        if (headers.getCacheControl() != null) {
            blobHeaders.setCacheControl(headers.getCacheControl());
        }

        blobClient.uploadFromFile(file.getPath().toAbsolutePath().toString(), true);
        blobClient.setHttpHeaders(blobHeaders);
    }

    @Override
    public void removeFile(FileResource resource) {
        removeFile(getObjectKey(resource));
    }

    @Override
    public void removeNamespaceLogo(Namespace namespace) {
        removeFile(getObjectKey(namespace));
    }

    private void removeFile(String blobName) {
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage("Cannot remove file", blobName));
        }

        try {
            getContainerClient().getBlobClient(blobName).delete();
        } catch (BlobStorageException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND.value()) {
                // 404 indicates that the file is already deleted
                // so only throw an exception for other status codes
                throw e;
            }
        }
    }

    @Override
    public URI getLocation(FileResource resource) {
        var blobName = getObjectKey(resource);
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage(blobName));
        }
        if (!serviceEndpoint.endsWith("/")) {
            throw new IllegalStateException("The Azure blob service endpoint URL must end with a slash.");
        }
        return URI.create(serviceEndpoint + blobContainer + "/" + blobName);
    }

    @Override
    public URI getNamespaceLogoLocation(Namespace namespace) {
        var blobName = getObjectKey(namespace);
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage(blobName));
        }
        if (!serviceEndpoint.endsWith("/")) {
            throw new IllegalStateException("The Azure blob service endpoint URL must end with a slash.");
        }
        return URI.create(serviceEndpoint + blobContainer + "/" + blobName);
    }

    @Override
    public TempFile downloadFile(FileResource resource) throws IOException {
        var blobName = getObjectKey(resource);
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage(blobName));
        }

        var tempFile = new TempFile("temp_file_", "");
        getContainerClient().getBlobClient(blobName)
                .downloadToFile(tempFile.getPath().toAbsolutePath().toString(), true);
        tempFile.setResource(resource);
        return tempFile;
    }

    @Override
    public long getFileSize(FileResource resource) throws IOException {
        var blobName = getObjectKey(resource);
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage(blobName));
        }

        try {
            // getProperties is a metadata-only request; it doesn't transfer the blob's content.
            return getContainerClient().getBlobClient(blobName).getProperties().getBlobSize();
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.BLOB_NOT_FOUND) {
                throw new FileNotFoundInStorageException("Blob not found: " + blobName, e);
            }

            throw new IOException("Failed to determine file size for " + blobName, e);
        }
    }

    @Override
    public void copyFiles(List<Pair<FileResource, FileResource>> pairs) {
        var copyOperations = new ArrayList<SyncPoller<BlobCopyInfo, Void>>();
        for (var pair : pairs) {
            var oldLocation = getLocation(pair.getFirst()).toString();
            var newBlobName = getObjectKey(pair.getSecond());
            var poller = getContainerClient().getBlobClient(newBlobName)
                    .beginCopy(oldLocation, Duration.of(1, ChronoUnit.SECONDS));

            copyOperations.add(poller);
        }
        for (var poller : copyOperations) {
            var response = poller.waitForCompletion();
            if (response.getValue().getCopyStatus() != CopyStatusType.SUCCESS) {
                throw new IllegalStateException(response.getValue().getError());
            }
        }
    }

    @Override
    public void copyNamespaceLogo(Namespace oldNamespace, Namespace newNamespace) {
        var oldLocation = getNamespaceLogoLocation(oldNamespace).toString();
        var newBlobName = getObjectKey(newNamespace);
        var poller = getContainerClient().getBlobClient(newBlobName)
                .beginCopy(oldLocation, Duration.of(1, ChronoUnit.SECONDS));

        var response = poller.waitForCompletion();
        if (response.getValue().getCopyStatus() != CopyStatusType.SUCCESS) {
            throw new IllegalStateException(response.getValue().getError());
        }
    }

    @Override
    @Cacheable(
        value = CACHE_EXTENSION_FILES,
        keyGenerator = GENERATOR_FILES,
        cacheManager = "fileCacheManager",
        sync = true
    )
    public Path getCachedFile(FileResource resource) {
        var blobName = getObjectKey(resource);
        if (StringUtils.isEmpty(serviceEndpoint)) {
            throw new IllegalStateException(missingEndpointMessage(blobName));
        }

        var path = filesCacheKeyGenerator.generateCachedExtensionPath(resource);
        FileUtil.writeSync(
                path,
                p -> getContainerClient().getBlobClient(blobName).downloadToFile(p.toAbsolutePath().toString()));
        return path;
    }
}
