/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.analytics.ingestion.jobs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.eclipse.openvsx.AbstractPostgresContainerTest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class AwsLogIngestionHandlerTest extends AbstractPostgresContainerTest {

    private static final String LOGS_BUCKET = "openvsx-logs-test";
    private static final String LOGS_PREFIX = "AWSLogs/";
    private static final String ARCHIVE_PREFIX = "processed/";

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.7"))
            .withServices("s3");

    @Autowired
    LogIngestionJob handler;

    @Autowired
    org.eclipse.openvsx.analytics.ingestion.aws.AwsDownloadRecordSource source;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    S3Client s3;

    private final List<Object> seededEntities = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void awsProperties(DynamicPropertyRegistry registry) {
        registry.add("ovsx.storage.aws.service-endpoint", () -> localstack.getEndpoint().toString());
        registry.add("ovsx.storage.aws.access-key-id", () -> "test");
        registry.add("ovsx.storage.aws.secret-access-key", () -> "test");
        registry.add("ovsx.storage.aws.region", () -> "us-east-1");
        registry.add("ovsx.storage.aws.bucket", () -> "openvsx-storage-test");
        registry.add("ovsx.storage.aws.path-style-access", () -> "true");
        registry.add("ovsx.logs.aws.bucket", () -> LOGS_BUCKET);
        registry.add("ovsx.logs.aws.format", () -> "fastly");
    }

    @BeforeEach
    void setUp() {
        s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of("us-east-1"))
                .forcePathStyle(true)
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket(LOGS_BUCKET).build());
    }

    @AfterEach
    void cleanUp() {
        for (var prefix : List.of(LOGS_PREFIX, ARCHIVE_PREFIX)) {
            listKeys(prefix).forEach(
                    key -> s3.deleteObject(DeleteObjectRequest.builder().bucket(LOGS_BUCKET).key(key).build()));
        }
        runInTransaction(() -> {
            seededEntities.reversed().forEach(entity -> entityManager.remove(entityManager.merge(entity)));
            entityManager.createQuery("delete from DownloadIngestion i where i.name like 'AWSLogs/%'")
                    .executeUpdate();
        });
        seededEntities.clear();
    }

    @Test
    void testProcessesLogFileUpdatesCountsAndDeletesIt() throws Exception {
        var extension = seedExtension("awsone", "awsone.ext-1.0.0.vsix");
        putLogFile(
                "AWSLogs/awsone-file.gz",
                gzip(
                        String.join(
                                "\n",
                                downloadLine("/awsone/ext/1.0.0/file/awsone.ext-1.0.0.vsix", "VSCode 1.90.2"),
                                downloadLine("/awsone/ext/1.0.0/file/awsone.ext-1.0.0.vsix", "VSCode 1.90.2"),
                                downloadLine(
                                        "/awsone/ext/1.0.0/file/awsone.ext-1.0.0.vsix",
                                        "Mozilla/5.0 Chrome/126.0.0.0"))));

        handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));

        assertEquals(3, freshDownloadCount(extension.getId()));
        assertEquals(1, succeededIngestions("AWSLogs/awsone-file.gz"));
        assertFalse(objectExists("AWSLogs/awsone-file.gz"));
    }

    @Test
    void testAlreadyProcessedFileIsCountedOnlyOnce() throws Exception {
        var extension = seedExtension("awstwo", "awstwo.ext-1.0.0.vsix");
        var content = gzip(downloadLine("/awstwo/ext/1.0.0/file/awstwo.ext-1.0.0.vsix", "VSCode 1.90.2"));

        putLogFile("AWSLogs/awstwo-file.gz", content);
        handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));
        assertEquals(1, freshDownloadCount(extension.getId()));

        // the same log file is presented again, e.g. after a partial cleanup failure
        putLogFile("AWSLogs/awstwo-file.gz", content);
        handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));

        assertEquals(1, freshDownloadCount(extension.getId()));
        assertEquals(1, succeededIngestions("AWSLogs/awstwo-file.gz"));
        // the already-processed file is cleaned up without re-counting
        assertFalse(objectExists("AWSLogs/awstwo-file.gz"));
    }

    @Test
    void testFailingFileIsRetainedExcludedAndReprocessedAfterClearing() throws Exception {
        var extension = seedExtension("awsthree", "awsthree.ext-1.0.0.vsix");
        putLogFile("AWSLogs/awsthree-file.gz", "this is not gzip".getBytes());

        handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));
        assertEquals(0, freshDownloadCount(extension.getId()));
        assertEquals(1, failedIngestions("AWSLogs/awsthree-file.gz"));
        // the file is retained for analysis
        assertTrue(objectExists("AWSLogs/awsthree-file.gz"));

        // and is excluded from the next run instead of failing again
        handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));
        assertEquals(1, failedIngestions("AWSLogs/awsthree-file.gz"));
        assertTrue(objectExists("AWSLogs/awsthree-file.gz"));

        // clearing the ingestion entry makes the file eligible again
        runInTransaction(
                () -> entityManager
                        .createQuery("delete from DownloadIngestion i where i.name = 'AWSLogs/awsthree-file.gz'")
                        .executeUpdate());
        putLogFile(
                "AWSLogs/awsthree-file.gz",
                gzip(downloadLine("/awsthree/ext/1.0.0/file/awsthree.ext-1.0.0.vsix", "VSCode 1.90.2")));
        handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));

        assertEquals(1, freshDownloadCount(extension.getId()));
        assertEquals(1, succeededIngestions("AWSLogs/awsthree-file.gz"));
        assertFalse(objectExists("AWSLogs/awsthree-file.gz"));
    }

    @Test
    void testArchivePrefixMovesProcessedFilesInsteadOfDeleting() throws Exception {
        ReflectionTestUtils.setField(source, "archivePrefix", ARCHIVE_PREFIX);
        try {
            var extension = seedExtension("awsfour", "awsfour.ext-1.0.0.vsix");
            putLogFile(
                    "AWSLogs/awsfour-file.gz",
                    gzip(
                            downloadLine("/awsfour/ext/1.0.0/file/awsfour.ext-1.0.0.vsix", "VSCode 1.90.2")));

            handler.run(new IngestionJobRequest<>(LogIngestionJob.class, FileResource.STORAGE_AWS));

            assertEquals(1, freshDownloadCount(extension.getId()));
            assertFalse(objectExists("AWSLogs/awsfour-file.gz"));
            assertTrue(objectExists(ARCHIVE_PREFIX + "AWSLogs/awsfour-file.gz"));
        } finally {
            ReflectionTestUtils.setField(source, "archivePrefix", "");
        }
    }

    private String downloadLine(String url, String userAgent) {
        return "<134>2026-07-01T13:52:42Z cache-fra-x S3-Log-Stream[1]: {\"timestamp\": \"2026-07-01T12:20:50+0000\", "
                + "\"geo_country\": \"united states\", \"client_ip\": \"1.1.1.1\", \"url\": \"" + url
                + "\", \"request_method\": \"GET\", "
                + "\"request_user_agent\": \"" + userAgent + "\", \"response_status\": 200}";
    }

    private byte[] gzip(String content) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var gzipStream = new GZIPOutputStream(buffer)) {
            gzipStream.write(content.getBytes());
        }
        return buffer.toByteArray();
    }

    private void putLogFile(String key, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder().bucket(LOGS_BUCKET).key(key).build(),
                RequestBody.fromBytes(content));
    }

    private boolean objectExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(LOGS_BUCKET).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private List<String> listKeys(String prefix) {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(LOGS_BUCKET).prefix(prefix).build())
                .contents().stream().map(S3Object::key).toList();
    }

    private int succeededIngestions(String name) {
        return countIngestions(name, true);
    }

    private int failedIngestions(String name) {
        return countIngestions(name, false);
    }

    private int countIngestions(String name, boolean success) {
        return inTransaction(
                () -> entityManager
                        .createQuery(
                                "select count(i) from DownloadIngestion i where i.name = :name and i.success = :success",
                                Long.class)
                        .setParameter("name", name)
                        .setParameter("success", success)
                        .getSingleResult()
                        .intValue());
    }

    private int freshDownloadCount(long extensionId) {
        return inTransaction(() -> entityManager.find(Extension.class, extensionId).getDownloadCount());
    }

    private Extension seedExtension(String namespaceName, String vsixFilename) {
        return inTransaction(() -> {
            var namespace = new Namespace();
            namespace.setName(namespaceName);
            entityManager.persist(namespace);

            var extension = new Extension();
            extension.setName(namespaceName + "-ext");
            extension.setNamespace(namespace);
            extension.setActive(true);
            entityManager.persist(extension);

            var extVersion = new ExtensionVersion();
            extVersion.setVersion("1.0.0");
            extVersion.setTargetPlatform("universal");
            extVersion.setExtension(extension);
            extVersion.setActive(true);
            entityManager.persist(extVersion);

            var resource = new FileResource();
            resource.setName(vsixFilename);
            resource.setType(FileResource.DOWNLOAD);
            resource.setStorageType(FileResource.STORAGE_AWS);
            resource.setExtension(extVersion);
            entityManager.persist(resource);

            seededEntities.addAll(List.of(namespace, extension, extVersion, resource));
            return extension;
        });
    }

    private void runInTransaction(Runnable action) {
        inTransaction(() -> {
            action.run();
            return null;
        });
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
