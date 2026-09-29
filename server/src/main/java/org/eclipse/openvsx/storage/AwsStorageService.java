/******************************************************************************
 * Copyright (c) 2022 Marshall Walker and others
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
package org.eclipse.openvsx.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointParams;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointProvider;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import org.eclipse.openvsx.cache.FilesCacheKeyGenerator;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.FileUtil;
import org.eclipse.openvsx.util.HttpHeadersUtil;
import org.eclipse.openvsx.util.TempFile;

import static org.eclipse.openvsx.cache.CacheService.CACHE_EXTENSION_FILES;
import static org.eclipse.openvsx.cache.CacheService.GENERATOR_FILES;

@Component
public class AwsStorageService implements IStorageService {

    private final FileCacheDurationConfig fileCacheDurationConfig;
    private final FilesCacheKeyGenerator filesCacheKeyGenerator;

    @Value("${ovsx.storage.aws.access-key-id:}")
    String accessKeyId;

    @Value("${ovsx.storage.aws.secret-access-key:}")
    String secretAccessKey;

    @Value("${ovsx.storage.aws.session-token:}")
    String sessionToken;

    @Value("${ovsx.storage.aws.region:}")
    String region;

    @Value("${ovsx.storage.aws.service-endpoint:}")
    String serviceEndpoint;

    @Value("${ovsx.storage.aws.bucket:}")
    String bucket;

    @Value("${ovsx.storage.aws.path-style-access:false}")
    boolean pathStyleAccess;

    private volatile S3Client s3Client;
    private volatile S3Presigner s3Presigner;

    public AwsStorageService(
            FileCacheDurationConfig fileCacheDurationConfig,
            FilesCacheKeyGenerator filesCacheKeyGenerator
    ) {
        this.fileCacheDurationConfig = fileCacheDurationConfig;
        this.filesCacheKeyGenerator = filesCacheKeyGenerator;
    }

    @PreDestroy
    public void close() {
        if (s3Presigner != null) {
            try {
                s3Presigner.close();
            } catch (RuntimeException _) {
            }
        }

        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (RuntimeException _) {
            }
        }
    }

    public S3Client getS3Client() {
        // lazily generates the s3 client using double-checked locking
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    var s3ClientBuilder = S3Client.builder()
                            .defaultsMode(DefaultsMode.STANDARD)
                            .forcePathStyle(pathStyleAccess)
                            .region(Region.of(region))
                            .credentialsProvider(getCredentialsProvider());

                    var serviceEndpoint = getServiceEndpoint();
                    if (serviceEndpoint != null) {
                        s3ClientBuilder = s3ClientBuilder.endpointOverride(serviceEndpoint);
                    }

                    s3Client = s3ClientBuilder.build();
                }
            }
        }
        return s3Client;
    }

    private S3Presigner getS3Presigner() {
        // lazily generates the s3 presigner using double-checked locking
        if (s3Presigner == null) {
            synchronized (this) {
                if (s3Presigner == null) {
                    var builder = S3Presigner.builder()
                            .region(Region.of(region))
                            .credentialsProvider(getCredentialsProvider());

                    if (pathStyleAccess) {
                        builder = builder.serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(true)
                                        .build());
                    }
                    var serviceEndpoint = getServiceEndpoint();
                    if (serviceEndpoint != null) {
                        builder = builder.endpointOverride(serviceEndpoint);
                    }

                    s3Presigner = builder.build();
                }
            }
        }
        return s3Presigner;
    }

    private @Nullable URI getServiceEndpoint() {
        if (StringUtils.isNotEmpty(serviceEndpoint)) {
            var endpointParams = S3EndpointParams.builder()
                    .endpoint(serviceEndpoint)
                    .region(Region.of(region))
                    .build();

            var endpoint = S3EndpointProvider
                    .defaultProvider()
                    .resolveEndpoint(endpointParams).join();

            return endpoint.url();
        }

        return null;
    }

    private AwsCredentialsProvider getCredentialsProvider() {
        // Use static credentials if provided, otherwise DefaultCredentialsProvider handles everything
        if (hasStaticCredentials()) {
            var credentials = hasSessionToken()
                    ? AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)
                    : AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            return StaticCredentialsProvider.create(credentials);
        }
        return DefaultCredentialsProvider.create();
    }

    private boolean hasStaticCredentials() {
        return !StringUtils.isEmpty(accessKeyId) && !StringUtils.isEmpty(secretAccessKey);
    }

    private boolean hasSessionToken() {
        return !StringUtils.isEmpty(sessionToken);
    }

    @Override
    public boolean isEnabled() {
        // Require region and bucket to be configured
        if (StringUtils.isEmpty(region) || StringUtils.isEmpty(bucket)) {
            return false;
        }

        // If any credential fields are provided, validate them properly
        boolean hasAccessKey = !StringUtils.isEmpty(accessKeyId);
        boolean hasSecretKey = !StringUtils.isEmpty(secretAccessKey);
        boolean hasSessionToken = !StringUtils.isEmpty(sessionToken);

        if (hasAccessKey || hasSecretKey || hasSessionToken) {
            // If any credential is provided, both access key and secret key must be present
            return hasAccessKey && hasSecretKey;
        }

        // No static credentials provided - allow AWS default credential provider chain
        return true;
    }

    @Override
    public void uploadFile(TempFile tempFile) {
        var resource = tempFile.getResource();
        uploadFile(tempFile, resource.getName(), getObjectKey(resource));
    }

    @Override
    public void uploadNamespaceLogo(TempFile logoFile) {
        var namespace = logoFile.getNamespace();
        uploadFile(logoFile, namespace.getLogoName(), getObjectKey(namespace));
    }

    protected void uploadFile(TempFile file, String fileName, String objectKey) {
        var headers = HttpHeadersUtil.createFileResponseHeaders(file.getPath(), fileName);

        var builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey);

        // see https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html
        if (headers.getContentType() != null) {
            builder.contentType(headers.getContentType().toString());
        }

        if (StringUtils.isNotBlank(headers.getContentDisposition().toString())) {
            builder.contentDisposition(headers.getContentDisposition().toString());
        }

        if (headers.getCacheControl() != null) {
            builder.cacheControl(headers.getCacheControl());
        }

        getS3Client().putObject(builder.build(), file.getPath());
    }

    @Override
    public void removeFile(FileResource resource) {
        removeFile(getObjectKey(resource));
    }

    @Override
    public void removeNamespaceLogo(Namespace namespace) {
        removeFile(getObjectKey(namespace));
    }

    private void removeFile(String objectKey) {
        var request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        getS3Client().deleteObject(request);
    }

    @Override
    public URI getLocation(FileResource resource) {
        return getLocation(getObjectKey(resource));
    }

    @Override
    public URI getNamespaceLogoLocation(Namespace namespace) {
        return getLocation(getObjectKey(namespace));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Percent-encodes the {@code +} that {@link #getObjectKey(FileResource)} leaves in the key: RFC 3986
     * allows it in a path segment, but S3 (and a CDN in front of it) decodes {@code +} in a path as a
     * space, which makes every version carrying semver build metadata such as {@code 1.0.0+10}
     * unresolvable. See <a href="https://github.com/eclipse-openvsx/openvsx/issues/1459">issue 1459</a>.
     * <p>
     * The result must only ever be passed to {@link URI#create(String)}: encoding it a second time —
     * {@code UriUtils.encodePathSegment}, {@code UriComponentsBuilder.encode()},
     * {@code UriComponents.toUri()} or the multi-argument {@link URI} constructor — escapes the
     * {@code %} again and produces {@code %252B}.
     */
    @Override
    public String getObjectKeyAsUrlPath(FileResource resource) {
        return encodeObjectKeyAsUrlPath(getObjectKey(resource));
    }

    @Override
    public String getObjectKeyAsUrlPath(Namespace namespace) {
        return encodeObjectKeyAsUrlPath(getObjectKey(namespace));
    }

    private String encodeObjectKeyAsUrlPath(String objectKey) {
        return objectKey.replace("+", "%2B");
    }

    private URI getLocation(String objectKey) {
        // The presigner encodes the object key itself ('+' becomes %2B) and signs that form, so the key
        // must be handed over unencoded here: encoding it as well would both double-encode the path and
        // invalidate the signature.
        var objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(fileCacheDurationConfig.getCacheDuration())
                .getObjectRequest(objectRequest)
                .build();

        var presignedRequest = getS3Presigner().presignGetObject(presignRequest);
        return presignedRequest.httpRequest().getUri();
    }

    @Override
    public TempFile downloadFile(FileResource resource) throws IOException {
        var objectKey = getObjectKey(resource);
        var request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        var tempFile = new TempFile("temp_file_", "");
        try (var stream = getS3Client().getObject(request)) {
            Files.copy(stream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        tempFile.setResource(resource);
        return tempFile;
    }

    @Override
    public long getFileSize(FileResource resource) throws IOException {
        // headObject is a metadata-only request; it doesn't transfer the object's content.
        var objectKey = getObjectKey(resource);
        var request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            return getS3Client().headObject(request).contentLength();
        } catch (S3Exception e) {
            // A HEAD response has no body, so S3 can't return a specific error code (e.g. NoSuchKey)
            // the way it would for a GET -- some implementations throw the specific
            // NoSuchKeyException regardless, but a plain S3Exception with a 404 status is what's
            // actually guaranteed, and what e.g. LocalStack sends. Check the status code rather than
            // the exception type to catch "not found" reliably across implementations.
            if (e.statusCode() == 404) {
                throw new FileNotFoundInStorageException("Object not found: " + objectKey, e);
            }

            throw e;
        }
    }

    @Override
    public void copyFiles(List<Pair<FileResource, FileResource>> pairs) {
        pairs.forEach(pair -> copy(getObjectKey(pair.getFirst()), getObjectKey(pair.getSecond())));
    }

    @Override
    public void copyNamespaceLogo(Namespace oldNamespace, Namespace newNamespace) {
        copy(getObjectKey(oldNamespace), getObjectKey(newNamespace));
    }

    private void copy(String oldObjectKey, String newObjectKey) {
        var request = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(oldObjectKey)
                .destinationBucket(bucket)
                .destinationKey(newObjectKey)
                .build();

        getS3Client().copyObject(request);
    }

    @Override
    @Cacheable(
        value = CACHE_EXTENSION_FILES,
        keyGenerator = GENERATOR_FILES,
        cacheManager = "fileCacheManager",
        sync = true
    )
    public Path getCachedFile(FileResource resource) {
        var objectKey = getObjectKey(resource);
        var request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        var path = filesCacheKeyGenerator.generateCachedExtensionPath(resource);
        FileUtil.writeSync(path, p -> {
            try (var stream = getS3Client().getObject(request)) {
                Files.copy(stream, p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        return path;
    }
}
