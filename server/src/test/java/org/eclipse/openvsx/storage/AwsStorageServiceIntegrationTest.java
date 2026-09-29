/********************************************************************************
 * Copyright (c) 2025 Adnan Al and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Pair;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TempFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Tag("s3")
@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AwsStorageServiceIntegrationTest.TestConfig.class)
class AwsStorageServiceIntegrationTest {

    private static final String TEST_BUCKET = "openvsx-test-bucket";
    private static final String TEST_REGION = "us-east-1";

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.7"))
            .withServices("s3", "iam")
            .withReuse(true);

    @Autowired
    private AwsStorageService awsStorageService;

    private AwsStorageService storageService;
    private S3Client testS3Client;

    private Namespace namespace;
    private Extension extension;
    private ExtensionVersion extVersion;
    private FileResource resource;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "ovsx.storage.aws.service-endpoint",
                () -> localstack.getEndpoint().toString());
        registry.add("ovsx.storage.aws.access-key-id", () -> "test");
        registry.add("ovsx.storage.aws.secret-access-key", () -> "test");
        registry.add("ovsx.storage.aws.region", () -> TEST_REGION);
        registry.add("ovsx.storage.aws.bucket", () -> TEST_BUCKET);
        registry.add("ovsx.storage.aws.path-style-access", () -> "true");
    }

    @BeforeEach
    void setUp() {
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        System.setProperty("aws.region", TEST_REGION);

        storageService = awsStorageService;

        ReflectionTestUtils.setField(storageService, "region", TEST_REGION);
        ReflectionTestUtils.setField(storageService, "bucket", TEST_BUCKET);
        ReflectionTestUtils.setField(
                storageService,
                "serviceEndpoint",
                localstack.getEndpoint().toString());
        ReflectionTestUtils.setField(storageService, "pathStyleAccess", true);

        testS3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test")))
                .region(Region.of(TEST_REGION))
                .forcePathStyle(true)
                .build();

        testS3Client.createBucket(
                CreateBucketRequest.builder()
                        .bucket(TEST_BUCKET)
                        .build());

        setupTestEntities();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
        System.clearProperty("aws.region");
    }

    private void setupTestEntities() {
        namespace = new Namespace();
        namespace.setName("testnamespace");
        namespace.setLogoName("logo.png");

        extension = new Extension();
        extension.setName("test-extension");
        extension.setNamespace(namespace);

        extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform("universal");
        extVersion.setExtension(extension);

        resource = new FileResource();
        resource.setName("extension.vsix");
        resource.setExtension(extVersion);
    }

    @Test
    void testServiceIsEnabled() {
        assertTrue(storageService.isEnabled());
    }

    @Test
    void testUploadAndDownloadFile() throws IOException {
        var tempFile = new TempFile("test_", ".vsix");
        var testContent = "This is test extension content";
        Files.write(tempFile.getPath(), testContent.getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);

        assertDoesNotThrow(() -> storageService.uploadFile(tempFile));

        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertTrue(objectExists(objectKey));

        var downloadedFile = storageService.downloadFile(resource);
        assertNotNull(downloadedFile);

        var downloadedContent = Files.readString(downloadedFile.getPath());
        assertEquals(testContent, downloadedContent);

        tempFile.close();
        downloadedFile.close();
    }

    @Test
    void testGetFileSizeWithoutDownloading() throws IOException {
        var tempFile = new TempFile("test_", ".vsix");
        var testContent = "This is test extension content";
        var testContentBytes = testContent.getBytes(StandardCharsets.UTF_8);
        Files.write(tempFile.getPath(), testContentBytes, StandardOpenOption.CREATE);
        tempFile.setResource(resource);
        storageService.uploadFile(tempFile);
        tempFile.close();

        assertEquals(testContentBytes.length, storageService.getFileSize(resource));
    }

    @Test
    void testGetFileSizeThrowsFileNotFoundInStorageExceptionForAMissingObject() {
        // A name no other test in this class uploads to, so its object key is guaranteed absent --
        // the shared `resource` field's key may already exist from another test in the same reused
        // LocalStack container/bucket.
        var missingResource = new FileResource();
        missingResource.setName("never-uploaded.vsix");
        missingResource.setExtension(extVersion);

        assertThrows(FileNotFoundInStorageException.class, () -> storageService.getFileSize(missingResource));
    }

    @Test
    void testUploadAndDownloadNamespaceLogo() throws IOException {
        var logoFile = new TempFile("logo_", ".png");
        var logoContent = "fake-png-content";
        Files.write(logoFile.getPath(), logoContent.getBytes(), StandardOpenOption.CREATE);
        logoFile.setNamespace(namespace);

        assertDoesNotThrow(() -> storageService.uploadNamespaceLogo(logoFile));

        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", namespace);
        assertTrue(objectExists(objectKey));

        var location = storageService.getNamespaceLogoLocation(namespace);
        assertNotNull(location);
        assertTrue(location.toString().contains(TEST_BUCKET));

        logoFile.close();
    }

    @Test
    void testRemoveFile() throws IOException {
        var tempFile = new TempFile("test_", ".vsix");
        Files.write(tempFile.getPath(), "test content".getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);
        storageService.uploadFile(tempFile);

        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertTrue(objectExists(objectKey));

        assertDoesNotThrow(() -> storageService.removeFile(resource));
        assertFalse(objectExists(objectKey));

        tempFile.close();
    }

    @Test
    void testRemoveNamespaceLogo() throws IOException {
        var logoFile = new TempFile("logo_", ".png");
        Files.write(logoFile.getPath(), "logo content".getBytes(), StandardOpenOption.CREATE);
        logoFile.setNamespace(namespace);
        storageService.uploadNamespaceLogo(logoFile);

        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", namespace);
        assertTrue(objectExists(objectKey));

        assertDoesNotThrow(() -> storageService.removeNamespaceLogo(namespace));
        assertFalse(objectExists(objectKey));

        logoFile.close();
    }

    @Test
    void testGetPresignedUrl() throws IOException {
        var tempFile = new TempFile("test_", ".vsix");
        Files.write(tempFile.getPath(), "test content".getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);
        storageService.uploadFile(tempFile);

        var location = storageService.getLocation(resource);
        assertNotNull(location);

        var locationStr = location.toString();
        assertTrue(locationStr.contains(TEST_BUCKET));
        assertTrue(locationStr.contains("testnamespace/test-extension/1.0.0/extension.vsix"));
        assertTrue(locationStr.contains("X-Amz-Algorithm"));

        tempFile.close();
    }

    /**
     * A version with semver build metadata is stored under a key containing a literal {@code +}, which the
     * presigner has to hand out percent-encoded because S3 reads a {@code +} in a URL path as a space.
     * See <a href="https://github.com/eclipse-openvsx/openvsx/issues/1459">issue 1459</a>.
     * <p>
     * Note that LocalStack does not reproduce that behaviour — it resolves a literal {@code +} in the path
     * just fine — so this test guards the encoding of the URL rather than reproducing the failure.
     */
    @Test
    void testGetPresignedUrlForVersionWithPlusSign() throws Exception {
        extVersion.setVersion("1.0.0+10");
        var tempFile = new TempFile("test_", ".vsix");
        Files.write(tempFile.getPath(), "test content".getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);
        storageService.uploadFile(tempFile);

        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertEquals("testnamespace/test-extension/1.0.0+10/extension.vsix", objectKey);

        var location = storageService.getLocation(resource).toString();
        assertTrue(
                location.matches("(?i).*testnamespace/test-extension/1\\.0\\.0%2b10/extension\\.vsix.*"),
                "expected an encoded '+' in " + location);

        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(location)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("test content", response.body());

        tempFile.close();
    }

    @Test
    void testObjectKeyGeneration() {
        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertEquals("testnamespace/test-extension/1.0.0/extension.vsix", objectKey);

        extVersion.setTargetPlatform("linux-x64");
        objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertEquals("testnamespace/test-extension/linux-x64/1.0.0/extension.vsix", objectKey);

        var logoKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", namespace);
        assertEquals("testnamespace/logo/logo.png", logoKey);
    }

    @Test
    void testCopyFiles() throws IOException {
        var tempFile = new TempFile("test_", ".vsix");
        Files.write(tempFile.getPath(), "test content".getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);
        storageService.uploadFile(tempFile);

        var targetExtVersion = new ExtensionVersion();
        targetExtVersion.setVersion("2.0.0");
        targetExtVersion.setTargetPlatform("universal");
        targetExtVersion.setExtension(extension);

        var targetResource = new FileResource();
        targetResource.setName("extension.vsix");
        targetResource.setExtension(targetExtVersion);

        var pairs = List.of(Pair.of(resource, targetResource));
        assertDoesNotThrow(() -> storageService.copyFiles(pairs));

        var sourceKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        var targetKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", targetResource);

        assertTrue(objectExists(sourceKey));
        assertTrue(objectExists(targetKey));

        tempFile.close();
    }

    @Test
    void testCopyNamespaceLogo() throws IOException {
        var logoFile = new TempFile("logo_", ".png");
        Files.write(logoFile.getPath(), "logo content".getBytes(), StandardOpenOption.CREATE);
        logoFile.setNamespace(namespace);
        storageService.uploadNamespaceLogo(logoFile);

        var targetNamespace = new Namespace();
        targetNamespace.setName("targetnamespace");
        targetNamespace.setLogoName("logo.png");

        assertDoesNotThrow(() -> storageService.copyNamespaceLogo(namespace, targetNamespace));

        var sourceKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", namespace);
        var targetKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", targetNamespace);

        assertTrue(objectExists(sourceKey));
        assertTrue(objectExists(targetKey));

        logoFile.close();
    }

    @Test
    void testListObjects() throws IOException {
        var initialListRequest = ListObjectsV2Request.builder()
                .bucket(TEST_BUCKET)
                .prefix("testnamespace/test-extension/")
                .build();
        var initialResponse = testS3Client.listObjectsV2(initialListRequest);
        int initialCount = initialResponse.contents().size();

        for (int i = 1; i <= 3; i++) {
            var tempFile = new TempFile("test_" + i + "_", ".vsix");
            Files.write(tempFile.getPath(), ("content " + i).getBytes(), StandardOpenOption.CREATE);

            var testResource = new FileResource();
            testResource.setName("extension" + i + ".vsix");
            testResource.setExtension(extVersion);
            tempFile.setResource(testResource);

            storageService.uploadFile(tempFile);
            tempFile.close();
        }

        var listRequest = ListObjectsV2Request.builder()
                .bucket(TEST_BUCKET)
                .prefix("testnamespace/test-extension/")
                .build();

        var response = testS3Client.listObjectsV2(listRequest);
        assertEquals(initialCount + 3, response.contents().size());
    }

    @Test
    void testErrorHandling() {
        var nonExistentResource = new FileResource();
        nonExistentResource.setName("nonexistent.vsix");
        nonExistentResource.setExtension(extVersion);

        assertThrows(Exception.class, () -> storageService.downloadFile(nonExistentResource));
    }

    @Test
    void testStaticCredentialsAuthentication() {
        assertTrue(storageService.isEnabled());

        assertDoesNotThrow(() -> {
            var s3Client = ReflectionTestUtils.invokeMethod(storageService, "getS3Client");
            assertNotNull(s3Client);
        });
    }

    @Test
    void testSessionTokenAuthentication() throws IOException {
        var sessionTokenService = createStorageServiceWithCredentials(
                "ASIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "AQoDYXdzEJr...<remainder of security token>");

        assertTrue(sessionTokenService.isEnabled());

        Boolean hasStaticCreds = ReflectionTestUtils.invokeMethod(sessionTokenService, "hasStaticCredentials");
        Boolean hasSessionToken = ReflectionTestUtils.invokeMethod(sessionTokenService, "hasSessionToken");
        assertTrue(hasStaticCreds);
        assertTrue(hasSessionToken);

        testBasicFileOperation(sessionTokenService, "session-token");
    }

    @Test
    void testDefaultCredentialProviderAuthentication() throws IOException {
        var defaultService = createStorageServiceWithCredentials("", "", "");

        Boolean hasStaticCreds = ReflectionTestUtils.invokeMethod(defaultService, "hasStaticCredentials");
        Boolean hasSessionToken = ReflectionTestUtils.invokeMethod(defaultService, "hasSessionToken");
        assertFalse(hasStaticCreds);
        assertFalse(hasSessionToken);

        assertDoesNotThrow(() -> {
            var s3Client = ReflectionTestUtils.invokeMethod(defaultService, "getS3Client");
            assertNotNull(s3Client);
        });

        testBasicFileOperation(defaultService, "default-provider");
    }

    @Test
    void testAuthenticationMethodDetection() {
        var permanentCredsService = createStorageServiceWithCredentials(
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                null);
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(permanentCredsService, "hasStaticCredentials"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(permanentCredsService, "hasSessionToken"));

        var temporaryCredsService = createStorageServiceWithCredentials(
                "ASIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "AQoDYXdzEJr...");
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(temporaryCredsService, "hasStaticCredentials"));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(temporaryCredsService, "hasSessionToken"));

        var defaultProviderService = createStorageServiceWithCredentials("", "", "");
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(defaultProviderService, "hasStaticCredentials"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(defaultProviderService, "hasSessionToken"));
    }

    @Test
    void testCredentialPriorityOrder() {
        var storageService = createStorageServiceWithCredentials(
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                null);

        Boolean hasStaticCreds = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        assertTrue(hasStaticCreds);

        ReflectionTestUtils.setField(storageService, "sessionToken", "test-session-token");

        Boolean hasSessionToken = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertTrue(hasSessionToken);
        assertTrue(hasStaticCreds);
    }

    @Test
    void testInvalidCredentials() {
        var storageService = createStorageServiceWithCredentials(
                "",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                null);
        assertFalse(storageService.isEnabled());

        storageService = createStorageServiceWithCredentials("AKIAIOSFODNN7EXAMPLE", "", null);
        assertFalse(storageService.isEnabled());
    }

    @Test
    void testS3ClientCreationWithDifferentCredentials() {
        final var storageService1 = createStorageServiceWithCredentials("test-access-key", "test-secret-key", null);

        assertDoesNotThrow(() -> {
            var s3Client = ReflectionTestUtils.invokeMethod(storageService1, "getS3Client");
            assertNotNull(s3Client);
        });

        final var storageService2 = createStorageServiceWithCredentials(
                "test-access-key",
                "test-secret-key",
                "test-session-token");

        assertDoesNotThrow(() -> {
            var s3Client = ReflectionTestUtils.invokeMethod(storageService2, "getS3Client");
            assertNotNull(s3Client);
        });
    }

    @Test
    void testPresignerCreationWithDifferentCredentials() {
        final var storageService1 = createStorageServiceWithCredentials("test-access-key", "test-secret-key", null);

        assertDoesNotThrow(() -> {
            var presigner = ReflectionTestUtils.invokeMethod(storageService1, "getS3Presigner");
            assertNotNull(presigner);
        });

        final var storageService2 = createStorageServiceWithCredentials(
                "test-access-key",
                "test-secret-key",
                "test-session-token");

        assertDoesNotThrow(() -> {
            var presigner = ReflectionTestUtils.invokeMethod(storageService2, "getS3Presigner");
            assertNotNull(presigner);
        });

        final var storageService3 = createStorageServiceWithCredentials("", "", "");

        assertDoesNotThrow(() -> {
            var presigner = ReflectionTestUtils.invokeMethod(storageService3, "getS3Presigner");
            assertNotNull(presigner);
        });
    }

    @Test
    void testOperationsFailWithRevokedPermissions() throws IOException {
        var restrictedService = awsStorageService;

        ReflectionTestUtils.setField(restrictedService, "region", TEST_REGION);
        ReflectionTestUtils.setField(restrictedService, "bucket", "non-existent-bucket");
        ReflectionTestUtils.setField(restrictedService, "serviceEndpoint", "http://localhost:99999");
        ReflectionTestUtils.setField(restrictedService, "pathStyleAccess", true);

        ReflectionTestUtils.setField(restrictedService, "s3Client", null);

        var tempFile = new TempFile("test_", ".vsix");
        Files.write(tempFile.getPath(), "test content".getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);

        assertThrows(Exception.class, () -> restrictedService.uploadFile(tempFile));
        assertThrows(Exception.class, () -> restrictedService.downloadFile(resource));
        assertThrows(Exception.class, () -> restrictedService.removeFile(resource));

        tempFile.close();
    }

    @Test
    void testValidAuthenticationButInsufficientBucketPermissions() throws IOException {
        var restrictedBucketService = awsStorageService;

        ReflectionTestUtils.setField(restrictedBucketService, "region", TEST_REGION);
        ReflectionTestUtils.setField(restrictedBucketService, "bucket", "unauthorized-bucket-no-access");
        ReflectionTestUtils.setField(
                restrictedBucketService,
                "serviceEndpoint",
                localstack.getEndpoint().toString());
        ReflectionTestUtils.setField(restrictedBucketService, "pathStyleAccess", true);

        ReflectionTestUtils.setField(restrictedBucketService, "s3Client", null);

        var tempFile = new TempFile("test_", ".vsix");
        Files.write(tempFile.getPath(), "test content".getBytes(), StandardOpenOption.CREATE);
        tempFile.setResource(resource);

        assertThrows(Exception.class, () -> restrictedBucketService.uploadFile(tempFile));
        assertThrows(Exception.class, () -> restrictedBucketService.downloadFile(resource));
        assertThrows(Exception.class, () -> restrictedBucketService.removeFile(resource));

        var logoFile = new TempFile("logo_", ".png");
        Files.write(logoFile.getPath(), "logo content".getBytes(), StandardOpenOption.CREATE);
        logoFile.setNamespace(namespace);

        assertThrows(Exception.class, () -> restrictedBucketService.uploadNamespaceLogo(logoFile));
        assertThrows(Exception.class, () -> restrictedBucketService.removeNamespaceLogo(namespace));

        tempFile.close();
        logoFile.close();
    }

    private AwsStorageService createStorageServiceWithCredentials(
            String accessKeyId,
            String secretAccessKey,
            String sessionToken
    ) {
        ReflectionTestUtils.setField(awsStorageService, "accessKeyId", accessKeyId);
        ReflectionTestUtils.setField(awsStorageService, "secretAccessKey", secretAccessKey);
        ReflectionTestUtils.setField(awsStorageService, "sessionToken", sessionToken);
        ReflectionTestUtils.setField(awsStorageService, "region", TEST_REGION);
        ReflectionTestUtils.setField(awsStorageService, "bucket", TEST_BUCKET);
        ReflectionTestUtils.setField(
                awsStorageService,
                "serviceEndpoint",
                localstack.getEndpoint().toString());
        ReflectionTestUtils.setField(awsStorageService, "pathStyleAccess", true);

        return awsStorageService;
    }

    private void testBasicFileOperation(AwsStorageService storageService, String testPrefix) throws IOException {
        var tempFile = new TempFile(testPrefix + "_", ".vsix");
        var testContent = "Authentication test content for " + testPrefix;
        Files.write(tempFile.getPath(), testContent.getBytes(), StandardOpenOption.CREATE);

        var testResource = new FileResource();
        testResource.setName(testPrefix + "-extension.vsix");
        testResource.setExtension(extVersion);
        tempFile.setResource(testResource);

        assertDoesNotThrow(() -> storageService.uploadFile(tempFile));

        assertDoesNotThrow(() -> {
            var downloadedFile = storageService.downloadFile(testResource);
            assertNotNull(downloadedFile);
            var downloadedContent = Files.readString(downloadedFile.getPath());
            assertEquals(testContent, downloadedContent);
            downloadedFile.close();
        });

        assertDoesNotThrow(() -> storageService.removeFile(testResource));

        tempFile.close();
    }

    private boolean objectExists(String objectKey) {
        try {
            testS3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(TEST_BUCKET)
                            .key(objectKey)
                            .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public FileCacheDurationConfig fileCacheDurationConfig() {
            var config = new FileCacheDurationConfig();
            ReflectionTestUtils.setField(config, "cacheDuration", Duration.ofDays(7));
            return config;
        }

        @Bean
        public FilesCacheKeyGenerator filesCacheKeyGenerator() {
            return new FilesCacheKeyGenerator();
        }

        @Bean
        public AwsStorageService awsStorageService(
                FileCacheDurationConfig fileCacheDurationConfig,
                FilesCacheKeyGenerator filesCacheKeyGenerator
        ) {
            return new AwsStorageService(fileCacheDurationConfig, filesCacheKeyGenerator);
        }
    }
}
