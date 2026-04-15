/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.mirror;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.UpstreamRegistryService;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.*;
import org.jobrunr.jobs.context.JobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD_SIG;
import static org.eclipse.openvsx.entities.FileResource.PUBLIC_KEY;

@Component
public class MirrorExtensionService {

    protected final Logger logger = LoggerFactory.getLogger(MirrorExtensionService.class);

    private DataMirrorService data;
    private final RepositoryService repositories;
    private final UpstreamRegistryService upstream;
    private final RestTemplate backgroundRestTemplate;
    private final RestTemplate backgroundNonRedirectingRestTemplate;
    private final AccessTokenService tokens;
    private final ExtensionService extensions;
    private final ExtensionVersionIntegrityService integrityService;

    public MirrorExtensionService(
            Optional<DataMirrorService> dataMirrorService,
            RepositoryService repositories,
            UpstreamRegistryService upstream,
            RestTemplate backgroundRestTemplate,
            RestTemplate backgroundNonRedirectingRestTemplate,
            AccessTokenService tokens,
            ExtensionService extensions,
            ExtensionVersionIntegrityService integrityService
    ) {
        dataMirrorService.ifPresent(service -> this.data = service);
        this.repositories = repositories;
        this.upstream = upstream;
        this.backgroundRestTemplate = backgroundRestTemplate;
        this.backgroundNonRedirectingRestTemplate = backgroundNonRedirectingRestTemplate;
        this.tokens = tokens;
        this.extensions = extensions;
        this.integrityService = integrityService;
    }

    /**
     * It applies delta from previous execution.
     */
    public void mirrorExtension(String namespaceName, String extensionName, UserData mirrorUser, LocalDate lastModified, JobContext jobContext) {
        var latest = upstream.getExtension(namespaceName, extensionName, null);
        if (shouldMirrorExtensionVersions(namespaceName, extensionName, lastModified, latest)) {
            mirrorExtensionVersions(namespaceName, extensionName, mirrorUser, jobContext);
        } else {
            jobContext.logger().info("all versions are up to date " + NamingUtil.toExtensionId(namespaceName, extensionName));
        }

        Supplier<Object> extensionIdSupplier = () -> NamingUtil.toExtensionId(namespaceName, extensionName);
        logger.atDebug()
                .setMessage("activating extension: {}")
                .addArgument(extensionIdSupplier)
                .log();

        data.activateExtension(namespaceName, extensionName);

        logger.atDebug()
                .setMessage("updating extension metadata: {}")
                .addArgument(extensionIdSupplier)
                .log();

        data.updateMetadata(namespaceName, extensionName, latest);
        
        logger.debug("updating namespace metadata: {}", namespaceName);
        data.mirrorNamespaceMetadata(namespaceName);
    }
    
    private boolean shouldMirrorExtensionVersions(String namespaceName, String extensionName, LocalDate lastModified, ExtensionJson latest) {
        if (lastModified == null) {
            return true;
        }

        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null) {
            return true;
        }

        var lastUpdated = extension.getLastUpdatedDate();
        return lastUpdated.toLocalDate().isBefore(lastModified) || lastUpdated.isBefore(TimeUtil.fromUTCString(latest.getTimestamp()));
    }

    private void mirrorExtensionVersions(String namespaceName, String extensionName, UserData mirrorUser, JobContext jobContext) {
        data.ensureNamespace(namespaceName);

        var toAdd = new ArrayList<ExtensionJson>();
        for(var targetPlatform : TargetPlatform.TARGET_PLATFORM_NAMES) {
            Set<String> versions;
            try {
                var json = upstream.getExtension(namespaceName, extensionName, targetPlatform);
                versions = json.getAllVersions().keySet();
                VersionAlias.ALIAS_NAMES.forEach(versions::remove);
            } catch (NotFoundException e) {
                // combination of extension and target platform doesn't exist, try next
                continue;
            }

            var targetVersions = data.getExtensionTargetVersions(namespaceName, extensionName, targetPlatform);

            targetVersions.stream()
                    .filter(extVersion -> !versions.contains(extVersion.getVersion()))
                    .forEach(extVersion -> data.deleteExtensionVersion(extVersion, mirrorUser));

            toAdd.addAll(
                versions.stream()
                    .filter(version -> targetVersions.stream().noneMatch(extVersion -> extVersion.getVersion().equals(version)))
                    .map(version -> upstream.getExtension(namespaceName, extensionName, targetPlatform, version))
                    .toList()
            );
        }
        toAdd.sort(Comparator.comparing(extensionJson -> TimeUtil.fromUTCString(extensionJson.getTimestamp())));
        
        for(var i = 0; i < toAdd.size(); i++) {
            var json = toAdd.get(i);
            jobContext.logger().info("mirroring " + NamingUtil.toLogFormat(json) + " (" + (i+1) + "/" +  toAdd.size() + ")");
            try {
                mirrorExtensionVersion(json);
                data.getMirroredVersions().increment();
            } catch (Exception e) {
                jobContext.logger().info("failure to mirror " + NamingUtil.toLogFormat(json) + ". Reason: " + t.getMessage());
                data.getFailedVersions().increment();
            }
        }
    }

    private void mirrorExtensionVersion(ExtensionJson json) {
        var download = json.getFiles().get("download");
        var userJson = new UserJson();
        userJson.setProvider(json.getPublishedBy().getProvider());
        userJson.setLoginName(json.getPublishedBy().getLoginName());
        userJson.setFullName(json.getPublishedBy().getFullName());
        userJson.setAvatarUrl(json.getPublishedBy().getAvatarUrl());
        userJson.setHomepage(json.getPublishedBy().getHomepage());
        var namespaceName = json.getNamespace();

        var vsixResourceHeaders = backgroundNonRedirectingRestTemplate.headForHeaders("{resolveVsixLocation}", Map.of("resolveVsixLocation", download));
        var vsixLocation = vsixResourceHeaders.getLocation();
        if (vsixLocation == null) {
            throw new AssertionError("Expected location header from redirected vsix url");
        }

        var segments = vsixLocation.getPath().split("/");
        var filename = segments[segments.length-1];
        if (!filename.endsWith(".vsix")) {
            throw new AssertionError("Invalid vsix filename from redirected vsix url");
        }

        String signatureName = null;
        try (var extensionFile = downloadToFile(download, "extension_", ".vsix")) {
            if(json.getFiles().containsKey(DOWNLOAD_SIG)) {
                try(
                    var signatureZip = downloadToFile(json.getFiles().get(DOWNLOAD_SIG), "extension_", ".sigzip");
                    var signatureFile = extractSignature(signatureZip);
                    var publicKeyFile = downloadToFile(json.getFiles().get(PUBLIC_KEY), "public_", ".pem");
                ) {
                    var verified = integrityService.verifyExtensionVersion(extensionFile, signatureFile, publicKeyFile);
                    if (!verified) {
                        throw new AssertionError("Unverified vsix package");
                    }
                }

                var signaturePathParams = URI.create(json.getFiles().get("signature")).getPath().split("/");
                signatureName = signaturePathParams[signaturePathParams.length - 1];
            }

            var user = data.getOrAddUser(userJson);
            var namespace = repositories.findNamespace(namespaceName);
            data.ensureNamespaceMembership(user, namespace);

            var description = "MirrorExtensionVersion";
            var accessTokenValue = data.getOrAddAccessTokenValue(user, description);

            var token = tokens.useAccessToken(accessTokenValue);
            extensions.mirrorVersion(extensionFile, signatureName, token, filename, json.getTimestamp());
            logger.atDebug()
                    .setMessage("completed mirroring of extension version: {}")
                    .addArgument(() -> NamingUtil.toLogFormat(json))
                    .log();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TempFile downloadToFile(String url, String prefix, String suffix) {
        return backgroundRestTemplate.execute("{url}", HttpMethod.GET, null, response -> {
            var file = new TempFile(prefix, suffix);
            try(var out = Files.newOutputStream(file.getPath())) {
                response.getBody().transferTo(out);
            }

            return file;
        }, Map.of("url", url));
    }

    private TempFile extractSignature(TempFile signatureZip) throws IOException {
        var signature = new TempFile("extension_",".signature.sig");
        try (var zipFile = new ZipFile(signatureZip.getPath().toFile())) {
            var sigEntry = zipFile.getEntry(".signature.sig");
            if(sigEntry == null) {
                throw new FileNotFoundException("No extension signature found");
            }
            try (
                    var sigInput = zipFile.getInputStream(sigEntry);
                    var sigOut = Files.newOutputStream(signature.getPath())
            ) {
                sigInput.transferTo(sigOut);
            }
        }

        return signature;
    }
}
