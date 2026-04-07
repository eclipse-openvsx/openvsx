/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.publish;

import com.google.common.base.Joiner;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.adapter.VSCodeIdNewExtensionJobRequest;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.extension_control.ExtensionControlService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.util.*;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Component
public class PublishExtensionVersionHandler {

    protected final Logger logger = LoggerFactory.getLogger(PublishExtensionVersionHandler.class);

    @Value("${ovsx.publishing.require-license:false}")
    boolean requireLicense;

    private final PublishExtensionVersionService service;
    private final ExtensionVersionIntegrityService integrityService;
    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final JobRequestScheduler scheduler;
    private final UserService users;
    private final ExtensionValidator validator;
    private final ExtensionControlService extensionControl;
    private final ExtensionScanService scanService;

    public PublishExtensionVersionHandler(
            PublishExtensionVersionService service,
            ExtensionVersionIntegrityService integrityService,
            EntityManager entityManager,
            RepositoryService repositories,
            JobRequestScheduler scheduler,
            UserService users,
            ExtensionValidator validator,
            ExtensionControlService extensionControl,
            ExtensionScanService scanService
    ) {
        this.service = service;
        this.integrityService = integrityService;
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.scheduler = scheduler;
        this.users = users;
        this.validator = validator;
        this.extensionControl = extensionControl;
        this.scanService = scanService;
    }

    public boolean isLicenseRequired() {
        return requireLicense;
    }

    public void setLicenseRequired(boolean requireLicense) {
        this.requireLicense = requireLicense;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ExtensionVersion createExtensionVersion(ExtensionProcessor processor, PersonalAccessToken token, LocalDateTime timestamp, boolean checkDependencies) {
        // Extract extension metadata from its manifest
        var extVersion = createExtensionVersion(processor, token.getUser(), token, timestamp);
        var dependencies = processor.getExtensionDependencies();
        var bundledExtensions = processor.getBundledExtensions();
        if (checkDependencies) {
            var parsedDependencies = dependencies.stream()
                    .map(id -> parseExtensionId(id, "extensionDependencies"))
                    .toList();

            if(!parsedDependencies.isEmpty()) {
                checkDependencies(parsedDependencies);
            }
            bundledExtensions.forEach(id -> parseExtensionId(id, "extensionPack"));
        }

        extVersion.setDependencies(dependencies);
        extVersion.setBundledExtensions(bundledExtensions);
        if(integrityService.isEnabled()) {
            extVersion.setSignatureKeyPair(repositories.findActiveKeyPair());
        }

        return extVersion;
    }

    private ExtensionVersion createExtensionVersion(ExtensionProcessor processor, UserData user, PersonalAccessToken token, LocalDateTime timestamp) {
        var namespaceName = processor.getNamespace();
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("Unknown publisher: " + namespaceName
                    + "\nUse the 'create-namespace' command to create a namespace corresponding to your publisher name.");
        }
        if (!users.hasPublishPermission(user, namespace)) {
            throw new ErrorResultException("Insufficient access rights for publisher: " + namespace.getName());
        }

        var extensionName = processor.getExtensionName();
        validateExtensionVersion(processor, namespaceName, extensionName);
        
        var extVersion = processor.getMetadata();
        var displayName = extVersion.getDisplayName();
        validateExtensionName(namespaceName, extensionName, displayName, user);

        extVersion.setTimestamp(timestamp);
        extVersion.setPublishedWith(token);
        extVersion.setActive(false);

        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null) {
            extension = new Extension();
            extension.setActive(false);
            extension.setName(extensionName);
            extension.setNamespace(namespace);
            extension.setPublishedDate(extVersion.getTimestamp());
            extension.setDeprecated(false);
            extension.setDownloadable(true);

            entityManager.persist(extension);
        } else {
            var existingVersion = repositories.findVersion(extVersion.getVersion(), extVersion.getTargetPlatform(), extension);
            if (existingVersion != null) {
                var extVersionId = NamingUtil.toLogFormat(namespaceName, extensionName, extVersion.getTargetPlatform(), extVersion.getVersion());
                var message = "Extension " + extVersionId + " is already published";
                message += existingVersion.isActive() ? "." : ", but currently isn't active and therefore not visible.";
                throw new ErrorResultException(message);
            }
        }

        extension.setLastUpdatedDate(extVersion.getTimestamp());
        extension.getVersions().add(extVersion);
        extVersion.setExtension(extension);

        validateLicense(processor, extVersion);
        validateMetadata(extVersion);
        entityManager.persist(extVersion);
        return extVersion;
    }

    private void validateExtensionVersion(ExtensionProcessor processor, String namespaceName, String extensionName) {
        var version = processor.getVersion();
        var versionIssue = validator.validateExtensionVersion(version);
        if (versionIssue.isPresent()) {
            throw new ErrorResultException(versionIssue.get().toString());
        }
    }

    private void validateExtensionName(String namespaceName, String extensionName, String displayName, UserData user) {
        var nameIssue = validator.validateExtensionName(extensionName);
        if (nameIssue.isPresent()) {
            throw new ErrorResultException(nameIssue.get().toString());
        }

        if(isMalicious(namespaceName, extensionName)) {
            throw new ErrorResultException(NamingUtil.toExtensionId(namespaceName, extensionName) + " is a known malicious extension");
        }
    }

    private void validateLicense(ExtensionProcessor processor, ExtensionVersion extVersion) {
        if (requireLicense) {
            // Check the extension's license
            try (var licenseFile = processor.getLicense(extVersion)) {
                checkLicense(extVersion, licenseFile);
            } catch (IOException e) {
                throw new ServerErrorException("Failed to read license file", e);
            }
        }
    }

    private void checkLicense(ExtensionVersion extVersion, TempFile licenseFile) {
        if (StringUtils.isEmpty(extVersion.getLicense()) &&
                (licenseFile == null || !licenseFile.getResource().getType().equals(FileResource.LICENSE))) {
            throw new ErrorResultException("This extension cannot be accepted because it has no license.");
        }
    }

    private void validateMetadata(ExtensionVersion extVersion) {
        var metadataIssues = validator.validateMetadata(extVersion);
        if (!metadataIssues.isEmpty()) {
            if (metadataIssues.size() == 1) {
                throw new ErrorResultException(metadataIssues.getFirst().toString());
            }
            throw new ErrorResultException("Multiple issues were found in the extension metadata:\n"
                    + Joiner.on("\n").join(metadataIssues));
        }
    }

    private boolean isMalicious(String namespace, String extension) {
        try {
            var maliciousExtensionIds = extensionControl.getMaliciousExtensionIds();
            return maliciousExtensionIds.contains(NamingUtil.toExtensionId(namespace, extension));
        } catch(IOException e) {
            logger.warn("Failed to check whether extension is malicious or not", e);
            return false;
        }
    }

    private void checkDependencies(List<ExtensionId> dependencies) {
        var unresolvedDependency = repositories.findFirstUnresolvedDependency(dependencies);
        if (unresolvedDependency != null) {
            throw new ErrorResultException("Cannot resolve dependency: " + unresolvedDependency);
        }
    }

    private ExtensionId parseExtensionId(String extensionIdText, String formatType) {
        var extensionId = NamingUtil.fromExtensionId(extensionIdText);
        if (extensionId == null) {
            throw new ErrorResultException("Invalid '" + formatType + "' format. Expected: '${namespace}.${name}'");
        }

        return extensionId;
    }

    @Async
    public void publishAsync(TempFile extensionFile, ExtensionService extensionService) {
        doPublish(extensionFile, extensionService, null);
    }

    @Async
    public void publishAsync(TempFile extensionFile, ExtensionService extensionService, ExtensionScan scan) {
        try {
            doPublish(extensionFile, extensionService, scan);
        } catch (Exception e) {
            if (scan != null) {
                scanService.markScanAsErrored(scan, "Async processing failed: " + e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Publish an extension - store files and optionally submit for scanning.
     */
    @Retryable
    private void doPublish(TempFile extensionFile, ExtensionService extensionService, ExtensionScan scan) {
        var download = extensionFile.getResource();
        var extVersion = download.getExtension();

        // Delete file resources in case publishAsync is retried
        service.deleteFileResources(extVersion);
        download.setId(0L);

        service.storeResource(extensionFile);
        service.persistResource(download);
        try (var processor = new ExtensionProcessor(extensionFile)) {
            // to keep backwards compatibility, mark extension versions as potentially malicious
            // if no scan service is enabled and the vsix file contains entries with extra fields.
            if (!scanService.isEnabled() && processor.isPotentiallyMalicious()) {
                service.markExtensionAsPotentiallyMalicious(extVersion);
                logger.atWarn()
                        .setMessage("Extension version is potentially malicious: {}")
                        .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                        .log();
                return;
            }

            Consumer<TempFile> consumer = tempFile -> {
                service.storeResource(tempFile);
                service.persistResource(tempFile.getResource());
            };

            if (integrityService.isEnabled()) {
                var keyPair = extVersion.getSignatureKeyPair();
                if(keyPair != null) {
                    try(var signature = integrityService.generateSignature(extensionFile, keyPair)) {
                        consumer.accept(signature);
                    }
                } else {
                    // Can happen when GenerateKeyPairJobRequestHandler hasn't run yet and there is no active SignatureKeyPair.
                    // This extension version should be assigned a SignatureKeyPair and a signature FileResource should be created
                    // by the ExtensionVersionSignatureJobRequestHandler migration.
                    logger.atWarn()
                            .setMessage("Integrity service is enabled, but {} did not have an active key pair")
                            .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                            .log();
                }
            }

            processor.getFileResources(extVersion, consumer);
            try (var sha256File = processor.generateSha256Checksum(extVersion)) {
                consumer.accept(sha256File);
            }
            
            // Submit scans to all registered scanners (if scan record provided and scanning enabled)
            // Scanning happens after file resources are stored but before activation
            // Extension remains INACTIVE until all scans complete via AsyncScanCompletionService
            if (scan != null && scanService.isEnabled() && scanService.hasRegisteredScanners()) {
                logger.info("Submitting scanner jobs for extension version: {}", NamingUtil.toLogFormat(extVersion));
                try {
                    // Submit to scanners - transitions scan to SCANNING status
                    boolean submitted = scanService.submitScannerJobs(scan, extVersion);
                    
                    if (!submitted) {
                        // No scanners available
                        logger.warn("No scanners available, activating extension immediately: {}", NamingUtil.toLogFormat(extVersion));
                        scanService.markScanPassed(scan);
                        service.activateExtension(extVersion, extensionService);
                    }
                    // If submission succeeded, extension remains inactive
                    // AsyncScanCompletionService will activate after scans complete
                } catch (Exception e) {
                    logger.error("Failed to submit scanner jobs for extension version: {}", NamingUtil.toLogFormat(extVersion), e);
                    scanService.markScanAsErrored(scan, "Failed to submit scanner jobs: " + e.getMessage());
                    // Extension remains inactive until scans complete or are manually approved
                }
            } else {
                logger.debug("Scanning disabled or no scan record, activating immediately: {}", 
                    NamingUtil.toLogFormat(extVersion));
                // If scanning is disabled or no scan record, activate the extension immediately
                if (scan != null) {
                    scanService.markScanPassed(scan);
                }
                service.activateExtension(extVersion, extensionService);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(extensionFile);
        }
    }

    public void mirror(TempFile extensionFile, String signatureName) {
        var download = extensionFile.getResource();
        var extVersion = download.getExtension();
        service.mirrorResource(extensionFile);
        if(signatureName != null) {
            service.mirrorResource(getSignatureResource(signatureName, extVersion));
        }
        try(var processor = new ExtensionProcessor(extensionFile)) {
            // don't store file resources, they can be generated on the fly to avoid traversing entire zip file
            processor.getFileResources(extVersion, service::mirrorResource);
            try (var sha256File = processor.generateSha256Checksum(extVersion)) {
                service.mirrorResource(sha256File);
            } catch (IOException e) {
                throw new RuntimeException("Failed to generate SHA-256 checksum file", e);
            }
        }
    }

    private FileResource getSignatureResource(String signatureName, ExtensionVersion extVersion) {
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName(signatureName);
        resource.setType(FileResource.DOWNLOAD_SIG);
        return resource;
    }

    public void schedulePublicIdJob(FileResource download) {
        var extension = download.getExtension().getExtension();
        if (StringUtils.isEmpty(extension.getPublicId())) {
            var namespace = extension.getNamespace();
            scheduler.enqueue(new VSCodeIdNewExtensionJobRequest(namespace.getName(), extension.getName()));
        }
    }
}