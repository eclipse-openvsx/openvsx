/********************************************************************************
 * Copyright (c) 2025 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.analytics.ingestion.aws;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import org.eclipse.openvsx.analytics.ingestion.DownloadIngestionMetrics;
import org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource;
import org.eclipse.openvsx.analytics.ingestion.RawDownloadRecord;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.storage.AwsStorageService;
import org.eclipse.openvsx.util.TempFile;

/**
 * Reads downloads from access logs in an Amazon S3 bucket.
 * <p>
 * The following log file formats are supported:
 * <ul>
 *     <li>cloudfront</li>
 *     <li>fastly</li>
 * </ul>
 * <p>
 * See
 * <ul>
 *   <li><a href="https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/standard-logging.html">CloudFront standard logging</a></li>
 *   <li><a href="https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/standard-logs-reference.html">CloudFront log format</a></li>
 *   <li><a href="https://www.fastly.com/documentation/guides/integrations/streaming-logs/custom-log-formats/">Fastly custom log format</a></li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ovsx.logs.aws.bucket")
public class AwsDownloadRecordSource implements DownloadRecordSource {

    private static final String LOG_LOCATION_PREFIX = "AWSLogs/";

    private final AwsStorageService awsStorageService;
    private final DownloadIngestionMetrics metrics;

    @Value("${ovsx.logs.aws.bucket:}")
    String bucket;

    @Value("${ovsx.logs.aws.log-location-prefix:" + LOG_LOCATION_PREFIX + "}")
    String logLocationPrefix;

    @Value("${ovsx.logs.aws.format:cloudfront}")
    String logFormat;

    @Value("${ovsx.logs.aws.max-keys:100}")
    int maxKeys;

    @Value("${ovsx.logs.aws.archive-prefix:}")
    String archivePrefix;

    @Value("${ovsx.logs.aws.cron:0 10 * * * *}")
    String cronSchedule;

    LogFileParser logFileParser;

    public AwsDownloadRecordSource(AwsStorageService awsStorageService, DownloadIngestionMetrics metrics) {
        this.awsStorageService = awsStorageService;
        this.metrics = metrics;
    }

    @PostConstruct
    public void initialize() {
        logFileParser = switch (logFormat.toLowerCase()) {
            case "cloudfront" -> new CloudFrontLogFileParser();
            case "fastly" -> new FastlyLogFileParser();
            default -> throw new IllegalArgumentException("unsupported log file format '" + logFormat + "'");
        };
    }

    /**
     * Indicates whether this source is enabled by application config.
     */
    @Override
    public boolean isEnabled() {
        return !StringUtils.isEmpty(bucket) && awsStorageService.isEnabled();
    }

    @Override
    public String getStorageType() {
        return FileResource.STORAGE_AWS;
    }

    @Override
    public String getCronSchedule() {
        return cronSchedule;
    }

    @Override
    public boolean covers(FileResource resource) {
        return FileResource.STORAGE_AWS.equals(resource.getStorageType()) && isEnabled();
    }

    @Override
    public Iterator<List<String>> listBatches() {
        return new Iterator<>() {
            private String continuationToken;
            private boolean done;

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public List<String> next() {
                var response = listObjects(continuationToken);
                continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
                done = continuationToken == null;
                return response.contents().stream()
                        .map(S3Object::key)
                        .filter(key -> key.endsWith(".gz"))
                        .toList();
            }
        };
    }

    @Override
    public List<RawDownloadRecord> read(String name) throws IOException {
        try (
                // the response keeps an HTTP connection checked out until it is closed; leaking one
                // per log object exhausts the S3 client's pool and stalls ingestion
                var inputStream = getS3Client().getObject(
                        GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(name)
                                .build(),
                        ResponseTransformer.toInputStream());
                var downloadsTempFile = new TempFile("aws-downloads-", ".gz");
        ) {
            // records without their own timestamp fall back to the log file's date
            var lastModified = inputStream.response().lastModified();
            var fallbackTime = lastModified != null ? lastModified : Instant.now();

            Files.copy(inputStream, downloadsTempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
            try (
                    var fileStream = new FileInputStream(downloadsTempFile.getPath().toFile());
                    var gzipStream = new GZIPInputStream(fileStream);
                    var reader = new BufferedReader(new InputStreamReader(gzipStream, StandardCharsets.UTF_8));
            ) {
                var records = new ArrayList<RawDownloadRecord>();
                var totalLines = 0;
                var skippedLines = 0;
                var lines = reader.lines().iterator();
                while (lines.hasNext()) {
                    totalLines++;
                    var record = logFileParser.parse(lines.next());
                    if (record == null) {
                        skippedLines++;
                        continue;
                    }

                    var download = record.toDownloadRecord(fallbackTime);
                    if (download != null) {
                        records.add(download);
                    }
                }
                metrics.recordParsedLines(totalLines, skippedLines);
                return records;
            }
        }
    }

    /**
     * Deletes a processed log file, or moves it below the configured
     * {@code ovsx.logs.aws.archive-prefix} instead.
     */
    @Override
    public void finish(String name) {
        if (!StringUtils.isEmpty(archivePrefix)) {
            getS3Client().copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(bucket)
                            .sourceKey(name)
                            .destinationBucket(bucket)
                            .destinationKey(archivePrefix + name)
                            .build());
        }
        getS3Client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(name).build());
    }

    private S3Client getS3Client() {
        return awsStorageService.getS3Client();
    }

    private ListObjectsV2Response listObjects(String continuationToken) {
        var builder = ListObjectsV2Request.builder().bucket(bucket).maxKeys(maxKeys).prefix(logLocationPrefix);

        if (continuationToken != null) {
            builder.continuationToken(continuationToken);
        }

        return getS3Client().listObjectsV2(builder.build());
    }
}
