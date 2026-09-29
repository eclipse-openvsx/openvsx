/*******************************************************************************
 * Copyright (c) 2025 Adnan Al and others
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
package org.eclipse.openvsx.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit integration tests for AWS Storage Service that don't require Docker/LocalStack.
 * These tests focus on configuration, authentication logic, and object key generation.
 */
class AwsStorageServiceTest {

    private AwsStorageService storageService;
    private Namespace namespace;
    private Extension extension;
    private ExtensionVersion extVersion;
    private FileResource resource;

    @BeforeEach
    void setUp() {
        var fileCacheDurationConfig = new FileCacheDurationConfig();
        var filesCacheKeyGenerator = new FilesCacheKeyGenerator();
        storageService = new AwsStorageService(fileCacheDurationConfig, filesCacheKeyGenerator);

        // Set up test entities
        setupTestEntities();
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
    void testServiceNotEnabledWithoutCredentials() {
        // Test with no credentials
        ReflectionTestUtils.setField(storageService, "accessKeyId", "");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "");

        assertFalse(storageService.isEnabled(), "Service should not be enabled without credentials");
    }

    @Test
    void testServiceEnabledWithStaticCredentials() {
        // Test with static credentials
        ReflectionTestUtils.setField(storageService, "accessKeyId", "AKIAIOSFODNN7EXAMPLE");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        ReflectionTestUtils.setField(storageService, "region", "us-east-1");
        ReflectionTestUtils.setField(storageService, "bucket", "test-bucket");

        assertTrue(storageService.isEnabled(), "Service should be enabled with static credentials");
    }

    @Test
    void testHasStaticCredentials() {
        // Test with both access key and secret key
        ReflectionTestUtils.setField(storageService, "accessKeyId", "AKIAIOSFODNN7EXAMPLE");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");

        Boolean hasCredentials = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        assertTrue(hasCredentials, "Should detect static credentials when both access key and secret key are present");

        // Test with missing access key
        ReflectionTestUtils.setField(storageService, "accessKeyId", "");
        hasCredentials = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        assertFalse(hasCredentials, "Should not detect static credentials when access key is missing");

        // Test with missing secret key
        ReflectionTestUtils.setField(storageService, "accessKeyId", "AKIAIOSFODNN7EXAMPLE");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "");
        hasCredentials = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        assertFalse(hasCredentials, "Should not detect static credentials when secret key is missing");
    }

    @Test
    void testHasSessionToken() {
        // Test with session token
        ReflectionTestUtils.setField(storageService, "sessionToken", "AQoDYXdzEJr...<remainder of security token>");

        Boolean hasToken = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertTrue(hasToken, "Should detect session token when present");

        // Test without session token
        ReflectionTestUtils.setField(storageService, "sessionToken", "");
        hasToken = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertFalse(hasToken, "Should not detect session token when empty");

        // Test with null session token
        ReflectionTestUtils.setField(storageService, "sessionToken", null);
        hasToken = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertFalse(hasToken, "Should not detect session token when null");
    }

    @Test
    void testCredentialPriorityLogic() {
        // Test 1: Static credentials without session token (permanent credentials)
        ReflectionTestUtils.setField(storageService, "accessKeyId", "AKIAIOSFODNN7EXAMPLE");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        ReflectionTestUtils.setField(storageService, "sessionToken", "");

        Boolean hasStaticCreds1 = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        Boolean hasSessionToken1 = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertTrue(hasStaticCreds1);
        assertFalse(hasSessionToken1);

        // Test 2: Static credentials with session token (temporary credentials)
        ReflectionTestUtils.setField(storageService, "sessionToken", "AQoDYXdzEJr...<remainder of security token>");

        Boolean hasStaticCreds2 = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        Boolean hasSessionToken2 = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertTrue(hasStaticCreds2);
        assertTrue(hasSessionToken2);

        // Test 3: No static credentials (should fall back to web identity or default provider)
        ReflectionTestUtils.setField(storageService, "accessKeyId", "");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "");
        ReflectionTestUtils.setField(storageService, "sessionToken", "");

        Boolean hasStaticCreds3 = ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials");
        Boolean hasSessionToken3 = ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken");
        assertFalse(hasStaticCreds3);
        assertFalse(hasSessionToken3);
    }

    @Test
    void testObjectKeyGeneration() {
        // Test regular file object key
        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertEquals("testnamespace/test-extension/1.0.0/extension.vsix", objectKey);

        // Test with non-universal target platform
        extVersion.setTargetPlatform("linux-x64");
        objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertEquals("testnamespace/test-extension/linux-x64/1.0.0/extension.vsix", objectKey);

        // Test namespace logo object key
        var logoKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", namespace);
        assertEquals("testnamespace/logo/logo.png", logoKey);
    }

    @Test
    void testObjectKeyAsUrlPathEncodesPlusSign() {
        // Semver build metadata, see https://github.com/eclipse-openvsx/openvsx/issues/1459
        extVersion.setVersion("1.0.0+10");

        // The object key must keep the literal '+': it identifies the stored object, and changing it
        // would leave every file published so far unreachable.
        assertEquals(
                "testnamespace/test-extension/1.0.0+10/extension.vsix",
                ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource));

        // S3 reads a '+' in a URL path as a space, so the URL form has to escape it.
        assertEquals(
                "testnamespace/test-extension/1.0.0%2B10/extension.vsix",
                storageService.getObjectKeyAsUrlPath(resource));
    }

    @Test
    void testObjectKeyAsUrlPathKeepsKeysWithoutPlusSign() {
        assertEquals(
                "testnamespace/test-extension/1.0.0/extension.vsix",
                storageService.getObjectKeyAsUrlPath(resource));
        assertEquals("testnamespace/logo/logo.png", storageService.getObjectKeyAsUrlPath(namespace));
    }

    @Test
    void testObjectKeyGenerationWithComplexNames() {
        // Test with complex namespace and extension names
        namespace.setName("my-complex-namespace");
        extension.setName("my.complex.extension-name");
        extVersion.setVersion("2.1.0-beta.1");
        resource.setName("complex/path/extension.vsix");

        var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);
        assertEquals(
                "my-complex-namespace/my.complex.extension-name/2.1.0-beta.1/complex/path/extension.vsix",
                objectKey);
    }

    @Test
    void testObjectKeyGenerationWithDifferentTargetPlatforms() {
        String[] platforms = { "universal", "win32-x64", "linux-x64", "darwin-x64", "darwin-arm64" };

        for (String platform : platforms) {
            extVersion.setTargetPlatform(platform);
            var objectKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", resource);

            if ("universal".equals(platform)) {
                assertEquals("testnamespace/test-extension/1.0.0/extension.vsix", objectKey);
            } else {
                assertEquals("testnamespace/test-extension/" + platform + "/1.0.0/extension.vsix", objectKey);
            }
        }
    }

    @Test
    void testNamespaceLogoObjectKeyGeneration() {
        // Test different logo file types
        String[] logoNames = { "logo.png", "logo.jpg", "logo.svg", "namespace-logo.webp" };

        for (String logoName : logoNames) {
            namespace.setLogoName(logoName);
            var logoKey = (String) ReflectionTestUtils.invokeMethod(storageService, "getObjectKey", namespace);
            assertEquals("testnamespace/logo/" + logoName, logoKey);
        }
    }

    @Test
    void testConfigurationProperties() {
        // Test that configuration properties are properly set
        ReflectionTestUtils.setField(storageService, "accessKeyId", "test-access-key");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "test-secret-key");
        ReflectionTestUtils.setField(storageService, "sessionToken", "test-session-token");
        ReflectionTestUtils.setField(storageService, "region", "us-west-2");
        ReflectionTestUtils.setField(storageService, "bucket", "my-test-bucket");
        ReflectionTestUtils.setField(storageService, "serviceEndpoint", "https://s3.us-west-2.amazonaws.com");
        ReflectionTestUtils.setField(storageService, "pathStyleAccess", true);

        // Verify properties are set correctly
        assertEquals("test-access-key", ReflectionTestUtils.getField(storageService, "accessKeyId"));
        assertEquals("test-secret-key", ReflectionTestUtils.getField(storageService, "secretAccessKey"));
        assertEquals("test-session-token", ReflectionTestUtils.getField(storageService, "sessionToken"));
        assertEquals("us-west-2", ReflectionTestUtils.getField(storageService, "region"));
        assertEquals("my-test-bucket", ReflectionTestUtils.getField(storageService, "bucket"));
        assertEquals(
                "https://s3.us-west-2.amazonaws.com",
                ReflectionTestUtils.getField(storageService, "serviceEndpoint"));
        assertTrue((Boolean) ReflectionTestUtils.getField(storageService, "pathStyleAccess"));
    }

    @Test
    void testAuthenticationMethodDetection() {
        // Test static credentials detection
        ReflectionTestUtils.setField(storageService, "accessKeyId", "AKIAIOSFODNN7EXAMPLE");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        ReflectionTestUtils.setField(storageService, "sessionToken", "");

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken"));

        // Test session token detection
        ReflectionTestUtils.setField(storageService, "sessionToken", "test-session-token");

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials"));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken"));

        // Test no credentials
        ReflectionTestUtils.setField(storageService, "accessKeyId", "");
        ReflectionTestUtils.setField(storageService, "secretAccessKey", "");
        ReflectionTestUtils.setField(storageService, "sessionToken", "");

        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(storageService, "hasStaticCredentials"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(storageService, "hasSessionToken"));
    }

    @Test
    void testPresignerIsCachedAsSingleton() {
        // Only region is needed to construct the presigner builder
        ReflectionTestUtils.setField(storageService, "region", "us-east-1");
        ReflectionTestUtils.setField(storageService, "serviceEndpoint", "");

        var presigner1 = ReflectionTestUtils.invokeMethod(storageService, "getS3Presigner");
        var presigner2 = ReflectionTestUtils.invokeMethod(storageService, "getS3Presigner");

        assertNotNull(presigner1);
        assertSame(
                presigner1,
                presigner2,
                "S3Presigner must be cached as a singleton; recreating it on each call " +
                        "destroys the HTTP connection pool needed for IRSA credential refresh");
    }

    @Test
    void testPresignerFieldInitiallyNull() {
        assertNull(
                ReflectionTestUtils.getField(storageService, "s3Presigner"),
                "S3Presigner should be lazily initialized");
    }
}
