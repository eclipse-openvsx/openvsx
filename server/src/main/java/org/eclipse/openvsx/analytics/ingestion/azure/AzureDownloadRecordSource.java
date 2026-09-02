/********************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion.azure;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionMetrics;
import org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource;
import org.eclipse.openvsx.analytics.ingestion.RawDownloadRecord;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.util.TempFile;

import static org.eclipse.openvsx.storage.AzureBlobStorageService.AZURE_USER_AGENT;

/**
 * Reads downloads from Azure Blob Storage access logs.
 */
@Component
@ConditionalOnProperty(name = "ovsx.logs.azure.service-endpoint")
public class AzureDownloadRecordSource implements DownloadRecordSource {

    protected final Logger logger = LoggerFactory.getLogger(AzureDownloadRecordSource.class);

    private final DownloadIngestionMetrics metrics;
    private final JsonMapper jsonMapper;
    private BlobContainerClient containerClient;
    private Pattern blobItemNamePattern;

    @Value("${ovsx.logs.azure.sas-token:}")
    String sasToken;

    @Value("${ovsx.logs.azure.service-endpoint:}")
    String logsServiceEndpoint;

    @Value("${ovsx.logs.azure.blob-container:insights-logs-storageread}")
    String logsBlobContainer;

    @Value("${ovsx.storage.azure.service-endpoint:}")
    String storageServiceEndpoint;

    @Value("${ovsx.storage.azure.blob-container:openvsx-resources}")
    String storageBlobContainer;

    @Value("${ovsx.logs.azure.cron:0 5 * * * *}")
    String cronSchedule;

    public AzureDownloadRecordSource(DownloadIngestionMetrics metrics) {
        this.metrics = metrics;
        this.jsonMapper = JsonMapper.shared();
    }

    /**
     * Indicates whether this source is enabled by application config.
     */
    @Override
    public boolean isEnabled() {
        var logsEnabled = !StringUtils.isEmpty(logsServiceEndpoint);
        var storageEnabled = !StringUtils.isEmpty(storageServiceEndpoint);
        if (logsEnabled && !storageEnabled) {
            logger.warn(
                    "The ovsx.storage.azure.service-endpoint value must be set to enable AzureDownloadRecordSource");
        }

        return logsEnabled && storageEnabled;
    }

    @Override
    public String getStorageType() {
        return FileResource.STORAGE_AZURE;
    }

    @Override
    public String getCronSchedule() {
        return cronSchedule;
    }

    @Override
    public boolean covers(FileResource resource) {
        return FileResource.STORAGE_AZURE.equals(resource.getStorageType()) && isEnabled();
    }

    @Override
    public Iterator<List<String>> listBatches() {
        var pages = listBlobs().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return pages.hasNext();
            }

            @Override
            public List<String> next() {
                return getBlobNames(pages.next().getValue());
            }
        };
    }

    @Override
    public List<RawDownloadRecord> read(String name) throws IOException {
        try (
                var downloadsTempFile = downloadBlobItem(name);
                var reader = Files.newBufferedReader(downloadsTempFile.getPath())
        ) {
            // records without their own timestamp fall back to the processing time
            var fallbackTime = Instant.now();
            var records = new ArrayList<RawDownloadRecord>();
            var totalLines = 0;
            var lines = reader.lines().iterator();
            while (lines.hasNext()) {
                totalLines++;
                var line = lines.next();
                var node = jsonMapper.readTree(line);
                String[] pathParams = null;
                if (isGetBlobOperation(node) && isStatusOk(node) && isExtensionPackageUri(node)
                        && isNotOpenVSXUserAgent(node)) {
                    var uri = node.get("uri").asString();
                    pathParams = uri.substring(storageServiceEndpoint.length()).split("/");
                }
                if (pathParams != null && storageBlobContainer.equals(pathParams[1])) {
                    var fileName = UriUtils.decode(pathParams[pathParams.length - 1], StandardCharsets.UTF_8)
                            .toUpperCase();
                    // Azure Storage logs carry no country information
                    records.add(
                            new RawDownloadRecord(
                                    parseTime(node, fallbackTime),
                                    fileName,
                                    null,
                                    callerIp(node),
                                    userAgent(node)));
                }
            }
            metrics.recordParsedLines(totalLines, 0);
            return records;
        }
    }

    @Override
    public void finish(String name) {
        try {
            getContainerClient().getBlobClient(name).delete();
        } catch (BlobStorageException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND.value()) {
                // 404 indicates that the file is already deleted
                // so only throw an exception for other status codes
                throw e;
            }
        }
    }

    private Instant parseTime(JsonNode node, Instant fallbackTime) {
        var time = node.path("time");
        if (time.isString()) {
            try {
                return Instant.parse(time.asString());
            } catch (DateTimeParseException e) {
                // fall back to the processing time below
            }
        }

        return fallbackTime;
    }

    private @Nullable String userAgent(JsonNode node) {
        var userAgent = node.path("properties").path("userAgentHeader");
        return userAgent.isString() ? StringUtils.trimToNull(userAgent.asString()) : null;
    }

    /**
     * Azure logs report the caller as {@code ip:port} (or {@code [ipv6]:port}); only the address
     * part is kept.
     */
    private @Nullable String callerIp(JsonNode node) {
        var value = node.path("callerIpAddress");
        if (!value.isString() || StringUtils.isBlank(value.asString())) {
            return null;
        }

        var address = value.asString().trim();
        if (address.startsWith("[") && address.contains("]")) {
            return address.substring(1, address.indexOf(']'));
        }
        var colon = address.lastIndexOf(':');
        if (colon > -1 && address.indexOf(':') == colon) {
            return address.substring(0, colon);
        }

        return address;
    }

    private boolean isGetBlobOperation(JsonNode node) {
        return node.get("operationName").asString().equals("GetBlob");
    }

    private boolean isStatusOk(JsonNode node) {
        return node.get("statusCode").asInt() == 200;
    }

    private boolean isExtensionPackageUri(JsonNode node) {
        return node.get("uri").asString().endsWith(".vsix");
    }

    private boolean isNotOpenVSXUserAgent(JsonNode node) {
        var userAgentHeader = node.path("properties").path("userAgentHeader").asString();
        return !AZURE_USER_AGENT.equals(userAgentHeader);
    }

    private TempFile downloadBlobItem(String blobName) throws IOException {
        var downloadsTempFile = new TempFile("azure-downloads-", ".json");
        getContainerClient().getBlobClient(blobName)
                .downloadToFile(downloadsTempFile.getPath().toAbsolutePath().toString(), true);
        return downloadsTempFile;
    }

    private List<String> getBlobNames(List<BlobItem> items) {
        var blobNames = new ArrayList<String>();
        for (var item : items) {
            var name = item.getName();
            if (isCorrectName(name)) {
                blobNames.add(name);
            }
        }

        return blobNames;
    }

    private Iterable<com.azure.core.http.rest.PagedResponse<BlobItem>> listBlobs() {
        var details = new BlobListDetails()
                .setRetrieveCopy(false)
                .setRetrieveMetadata(false)
                .setRetrieveDeletedBlobs(false)
                .setRetrieveTags(false)
                .setRetrieveSnapshots(false)
                .setRetrieveUncommittedBlobs(false)
                .setRetrieveVersions(false);

        var options = new ListBlobsOptions().setMaxResultsPerPage(100).setDetails(details);
        return getContainerClient().listBlobs(options, Duration.ofMinutes(5)).iterableByPage();
    }

    private BlobContainerClient getContainerClient() {
        if (containerClient == null) {
            containerClient = new BlobContainerClientBuilder()
                    .endpoint(logsServiceEndpoint)
                    .sasToken(sasToken)
                    .containerName(logsBlobContainer)
                    .buildClient();
        }

        return containerClient;
    }

    private boolean isCorrectName(String name) {
        return getBlobItemNamePattern().matcher(name).matches();
    }

    private Pattern getBlobItemNamePattern() {
        if (blobItemNamePattern == null) {
            var host = URI.create(storageServiceEndpoint).getHost();
            var storageAccount = host.substring(0, host.indexOf('.'));

            var regex = "^resourceId=/subscriptions/.*/resourceGroups/.*/providers/Microsoft\\.Storage/storageAccounts/"
                    + storageAccount + "/blobServices/default/.*$";
            blobItemNamePattern = Pattern.compile(regex);
        }

        return blobItemNamePattern;
    }
}
