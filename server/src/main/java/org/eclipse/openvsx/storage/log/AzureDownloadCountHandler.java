/********************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage.log;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.metrics.DownloadCountValidator;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.util.TempFile;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.eclipse.openvsx.storage.AzureBlobStorageService.AZURE_USER_AGENT;

/**
 * Pulls logs from Azure Blob Storage, extracts downloads from the logs
 * and updates download counts in the database.
 */
@Component
public class AzureDownloadCountHandler implements JobRequestHandler<HandlerJobRequest<?>> {

    protected final Logger logger = LoggerFactory.getLogger(AzureDownloadCountHandler.class);

    private final DownloadCountProcessor processor;
    private final Optional<DownloadCountValidator> downloadCountValidator;
    private BlobContainerClient containerClient;
    private ObjectMapper objectMapper;
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

    public AzureDownloadCountHandler(
            DownloadCountProcessor processor,
            Optional<DownloadCountValidator> downloadCountValidator
    ) {
        this.processor = processor;
        this.downloadCountValidator = downloadCountValidator;
    }

    public String getRecurringJobId() {
        return "update-azure-download-counts";
    }

    public String getCronSchedule() {
        return cronSchedule;
    }

    /**
     * Indicates whether the download service is enabled by application config.
     */
    public boolean isEnabled() {
        var logsEnabled = !StringUtils.isEmpty(logsServiceEndpoint);
        var storageEnabled = !StringUtils.isEmpty(storageServiceEndpoint);
        if (logsEnabled && !storageEnabled) {
            logger.warn("The ovsx.storage.azure.service-endpoint value must be set to enable AzureDownloadCountService");
        }

        return logsEnabled && storageEnabled;
    }

    /**
     * Task scheduled once per hour to pull logs from Azure Blob Storage and update extension download counts.
     */
    @Job(name = "Update Azure Download Counts", retries = 0)
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        if (!isEnabled()) {
            return;
        }

        logger.info("[AzureDownloadCountService] >> updateDownloadCounts");
        var maxExecutionTime = LocalDateTime.now().plusMinutes(50);
        var blobs = listBlobs();
        var iterableByPage = blobs.iterableByPage();

        var stopWatch = new StopWatch();
        while (iterableByPage != null) {
            PagedResponse<BlobItem> response = null;
            var iterator = iterableByPage.iterator();
            if (iterator.hasNext()) {
                response = iterator.next();
                if (!processResponse(response, stopWatch, maxExecutionTime)) {
                    break;
                }
            }

            var continuationToken = response != null ? response.getContinuationToken() : "";
            iterableByPage = !StringUtils.isEmpty(continuationToken) ? blobs.iterableByPage(continuationToken) : null;
        }

        logger.info("[AzureDownloadCountService] << updateDownloadCounts");
    }

    private boolean processResponse(PagedResponse<BlobItem> response, StopWatch stopWatch, LocalDateTime maxExecutionTime) {
        var blobNames = getBlobNames(response.getValue());
        var processedItems = processor.processedItems(FileResource.STORAGE_AZURE, blobNames);
        processedItems.forEach(this::deleteBlob);
        blobNames.removeAll(processedItems);
        for (var name : blobNames) {
            if (LocalDateTime.now().isAfter(maxExecutionTime)) {
                var nextJobRunTime = LocalDateTime.now().plusHours(1).withMinute(5);
                logger.info("Failed to process all download counts within timeslot, next job run is at {}", nextJobRunTime);
                return false;
            }

            var processedOn = LocalDateTime.now();
            var success = false;
            stopWatch.start();
            try {
                var files = processBlobItem(name);
                if (!files.isEmpty()) {
                    var extensionDownloads = processor.processDownloadCounts(FileResource.STORAGE_AZURE, files);
                    var updatedExtensions = processor.increaseDownloadCounts(extensionDownloads);
                    updatedExtensions.forEach(processor::evictCaches);
                    processor.updateSearchEntries(updatedExtensions);
                }

                success = true;
            } catch (Exception e) {
                logger.error("Failed to process BlobItem: {}", name, e);
            }

            stopWatch.stop();
            var executionTime = (int) stopWatch.lastTaskInfo().getTimeMillis();
            processor.persistProcessedItem(name, FileResource.STORAGE_AZURE, processedOn, executionTime, success);
            if(success) {
                deleteBlob(name);
            }
        }

        return true;
    }

    private void deleteBlob(String blobName) {
        try {
            getContainerClient().getBlobClient(blobName).delete();
        } catch(BlobStorageException e) {
            if(e.getStatusCode() != HttpStatus.NOT_FOUND.value()) {
                // 404 indicates that the file is already deleted
                // so only throw an exception for other status codes
                throw e;
            }
        }
    }

    private Map<String, Integer> processBlobItem(String blobName) throws IOException {
        try (
                var downloadsTempFile = downloadBlobItem(blobName);
                var reader = Files.newBufferedReader(downloadsTempFile.getPath())
        ) {
            var events = new ArrayList<DownloadEvent>();
            var lines = reader.lines().iterator();
            while(lines.hasNext()) {
                var line = lines.next();
                var node = getObjectMapper().readTree(line);
                String[] pathParams = null;
                if(isGetBlobOperation(node) && isStatusOk(node) && isExtensionPackageUri(node) && isNotOpenVSXUserAgent(node)) {
                    var uri = node.get("uri").asText();
                    pathParams = uri.substring(storageServiceEndpoint.length()).split("/");
                }
                if(pathParams != null && storageBlobContainer.equals(pathParams[1])) {
                    var fileName = UriUtils.decode(pathParams[pathParams.length - 1], StandardCharsets.UTF_8).toUpperCase();
                    String clientIp = node.path("callerIpAddress").asText(null);
                    String userAgent = node.path("properties").path("userAgentHeader").asText(null);
                    // Azure blob log lines have a root "time" field in ISO-8601 UTC format.
                    Instant eventTime = parseEventTime(node.path("time").asText(null));
                    events.add(new DownloadEvent(fileName, clientIp, userAgent, eventTime));
                }
            }
            return countValidatedEvents(events);
        }
    }

    Map<String, Integer> countValidatedEvents(List<DownloadEvent> events) {
        var fileCounts = new HashMap<String, Integer>();
        if (events.isEmpty()) {
            return fileCounts;
        }

        var fileNames = events.stream()
                .map(DownloadEvent::fileName)
                .distinct()
                .toList();
        var fileToExtensionId = processor.resolveDownloadFileToExtensionId(FileResource.STORAGE_AZURE, fileNames);

        for (var event : events) {
            Long extensionId = fileToExtensionId.get(event.fileName());
            if (extensionId == null) {
                continue;
            }

            boolean shouldCount = downloadCountValidator
                    .map(validator -> validator.shouldCountDownload(extensionId, event.clientIp(), event.userAgent(), event.eventTime()))
                    .orElse(true);
            if (shouldCount) {
                fileCounts.merge(event.fileName(), 1, Integer::sum);
            }
        }
        return fileCounts;
    }

    private boolean isGetBlobOperation(JsonNode node) {
        return node.get("operationName").asText().equals("GetBlob");
    }

    private boolean isStatusOk(JsonNode node) {
        return node.get("statusCode").asInt() == 200;
    }

    private boolean isExtensionPackageUri(JsonNode node) {
        return node.get("uri").asText().endsWith(".vsix");
    }

    private boolean isNotOpenVSXUserAgent(JsonNode node) {
        var userAgentHeader = node.path("properties").path("userAgentHeader").asText();
        return !AZURE_USER_AGENT.equals(userAgentHeader);
    }


    private TempFile downloadBlobItem(String blobName) throws IOException {
        var downloadsTempFile = new TempFile("azure-downloads-", ".json");
        getContainerClient().getBlobClient(blobName).downloadToFile(downloadsTempFile.getPath().toAbsolutePath().toString(), true);
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

    private PagedIterable<BlobItem> listBlobs() {
        var details = new BlobListDetails()
                .setRetrieveCopy(false)
                .setRetrieveMetadata(false)
                .setRetrieveDeletedBlobs(false)
                .setRetrieveTags(false)
                .setRetrieveSnapshots(false)
                .setRetrieveUncommittedBlobs(false)
                .setRetrieveVersions(false);

        var options = new ListBlobsOptions().setMaxResultsPerPage(100).setDetails(details);
        return getContainerClient().listBlobs(options, Duration.ofMinutes(5));
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

    private ObjectMapper getObjectMapper() {
        if(objectMapper == null) {
            objectMapper = new ObjectMapper();
        }

        return objectMapper;
    }

    private boolean isCorrectName(String name) {
        return getBlobItemNamePattern().matcher(name).matches();
    }

    private Pattern getBlobItemNamePattern() {
        if(blobItemNamePattern == null) {
            var host = URI.create(storageServiceEndpoint).getHost();
            var storageAccount = host.substring(0, host.indexOf('.'));

            var regex = "^resourceId=/subscriptions/.*/resourceGroups/.*/providers/Microsoft\\.Storage/storageAccounts/" + storageAccount + "/blobServices/default/.*$";
            blobItemNamePattern = Pattern.compile(regex);
        }

        return blobItemNamePattern;
    }

    /**
     * Parses Azure blob log ISO-8601 UTC timestamp (e.g. "2026-02-09T04:20:50Z") to Instant.
     * Returns null on parse failure so callers can fall back gracefully.
     */
    private Instant parseEventTime(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            logger.debug("Failed to parse Azure event timestamp: {}", timestamp);
            return null;
        }
    }

    record DownloadEvent(String fileName, String clientIp, String userAgent, Instant eventTime) {
    }
}
