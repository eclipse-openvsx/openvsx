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

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hibernate.exception.ConstraintViolationException;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.adapter.VSCodeIdNewExtensionJobRequest;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.extension_control.ExtensionControlService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.scanning.ExtensionScanService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.auth.AccessTokenAuthentication;
import org.eclipse.openvsx.util.auth.AuthenticatedUser;

@Component
public class PublishExtensionVersionHandler {
    protected final Logger logger = LoggerFactory.getLogger(PublishExtensionVersionHandler.class);

    private final PublishingConfig config;
    private final PublishExtensionVersionService service;
    private final ExtensionVersionIntegrityService integrityService;
    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final JobRequestScheduler scheduler;
    private final UserService users;
    private final ExtensionValidator validator;
    private final ExtensionControlService extensionControl;
    private final ExtensionScanService scanService;

    private final Predicate<String> unsupportedIconExtensions;

    public PublishExtensionVersionHandler(
            PublishingConfig config,
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
        this.config = config;
        this.service = service;
        this.integrityService = integrityService;
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.scheduler = scheduler;
        this.users = users;
        this.validator = validator;
        this.extensionControl = extensionControl;
        this.scanService = scanService;

        this.unsupportedIconExtensions = path -> {
            if (path == null) {
                return false;
            }

            var fileExtension = FilenameUtils.getExtension(path);
            return config.getUnsupportedIconFormats().stream().anyMatch(ext -> ext.equalsIgnoreCase(fileExtension));
        };
    }

    public boolean isLicenseRequired() {
        return config.isRequireLicense();
    }

    /**
     * Backstop for a lost race on the creation of the extension row: a writer that does not take the
     * namespace lock (e.g. a namespace change moving an extension in) can still commit the extension
     * between our lookup and our insert. PostgreSQL only reports the duplicate once that writer has
     * committed, so a single retry is enough — it finds the committed extension and adds the version
     * to it. Retrying is safe because the method derives all of its state from {@code processor} and
     * the retry advice runs outside the transaction advice, so every attempt gets a fresh transaction.
     * <p>
     * Note that this only holds for callers that publish without a transaction of their own, which is
     * every caller but {@link ExtensionService#mirrorVersion}: an attempt joining an outer transaction
     * can not commit after that transaction was marked for rollback, so mirroring relies on the
     * namespace lock alone.
     */
    @Retryable(
        includes = DataIntegrityViolationException.class,
        predicate = DuplicateExtensionPredicate.class,
        maxRetries = 1,
        delay = 100
    )
    @Transactional(rollbackOn = ErrorResultException.class)
    public ExtensionVersion createExtensionVersion(
            ExtensionProcessor processor,
            AuthenticatedUser au,
            LocalDateTime timestamp,
            boolean checkDependencies
    ) {
        // Extract extension metadata from its manifest
        var extVersion = createExtensionVersion(processor, au, timestamp);
        var dependencies = processor.getExtensionDependencies();
        var bundledExtensions = processor.getBundledExtensions();
        if (checkDependencies) {
            var parsedDependencies = dependencies.stream()
                    .map(id -> parseExtensionId(id, "extensionDependencies"))
                    .toList();

            if (!parsedDependencies.isEmpty()) {
                checkDependencies(parsedDependencies);
            }
            bundledExtensions.forEach(id -> parseExtensionId(id, "extensionPack"));
        }

        extVersion.setDependencies(dependencies);
        extVersion.setBundledExtensions(bundledExtensions);
        if (integrityService.isEnabled()) {
            extVersion.setSignatureKeyPair(repositories.findActiveKeyPair());
        }

        return extVersion;
    }

    /**
     * Checks publish preconditions that should be evaluated before running any package validation or scanning: the
     * publisher must exist, the user of {@code token} must be allowed to publish to it, and the version must not be
     * published already.
     * <p>
     * Callers publishing with scanning enabled have to invoke this before validating or scanning the
     * package, as neither is of any use for a package that can not be published in the first place.
     * The checks are repeated by {@link #createExtensionVersion(ExtensionProcessor, AuthenticatedUser,
     * LocalDateTime, boolean)}, which enforces them while holding the extension lock.
     *
     * @throws ErrorResultException if the extension version can not be published
     */
    public void checkPublishPreconditions(ExtensionProcessor processor, UserData userData) {
        var namespace = checkPublishPermission(processor, userData);
        var extensionName = processor.getExtensionName();
        var existingVersion = repositories
                .findVersion(processor.getVersion(), processor.getTargetPlatform(), extensionName, namespace.getName());
        if (existingVersion != null) {
            throw new ErrorResultException(
                    alreadyPublishedMessage(namespace.getName(), extensionName, existingVersion));
        }
    }

    private Namespace checkPublishPermission(ExtensionProcessor processor, UserData user) {
        var namespaceName = processor.getNamespace();
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException(
                    "Unknown publisher: " + namespaceName
                            + "\nUse the 'create-namespace' command to create a namespace corresponding to your publisher name.");
        }
        if (!users.hasPublishPermission(user, namespace)) {
            throw new ErrorResultException(
                    "Insufficient access rights for publisher: " + namespace.getName(),
                    HttpStatus.FORBIDDEN);
        }
        return namespace;
    }

    private String alreadyPublishedMessage(
            String namespaceName,
            String extensionName,
            ExtensionVersion existingVersion
    ) {
        var extVersionId = NamingUtil.toLogFormat(
                namespaceName,
                extensionName,
                existingVersion.getTargetPlatform(),
                existingVersion.getVersion());
        var message = "Extension " + extVersionId + " is already published";
        if (existingVersion.isRemoved()) {
            message += " and was removed. Extension versions are immutable, so this version's identity"
                    + " stays permanently reserved and cannot be republished."
                    + " Ask an administrator to purge it if it must be republished.";
        } else {
            message += existingVersion.isActive()
                    ? "."
                    : ", but currently isn't active and therefore not visible.";
        }
        return message;
    }

    private ExtensionVersion createExtensionVersion(
            ExtensionProcessor processor,
            AuthenticatedUser au,
            LocalDateTime timestamp
    ) {
        var namespace = checkPublishPermission(processor, au.userData());
        var namespaceName = processor.getNamespace();

        var extensionName = processor.getExtensionName();
        validateExtensionVersion(processor, namespaceName, extensionName);

        // This is the only place where extracted metadata gets persisted, so it is the one that has to
        // honour the configured tag limits.
        var extVersion = processor.getMetadata(config.getMaxTags(), config.getMaxInternalTags());
        var displayName = extVersion.getDisplayName();
        validateExtensionName(namespaceName, extensionName, displayName, au.userData());

        // Check that the metadata contained in package.json matches the one extracted from extension.vsixmanifest
        // Open VSX uses the metadata from extension.vsixmanifest as the source of truth, but VS Code
        // actually uses package.json so we need to make sure they are both aligned to avoid differentials.
        validatePackageMetadata(processor, namespaceName, extensionName, extVersion);

        extVersion.setTimestamp(timestamp);
        extVersion.setPublishedBy(au.userData());
        if (au instanceof AccessTokenAuthentication ata) {
            extVersion.setPublishedWithTt(ata.type());
            // An ephemeral token's row does not outlive its use: a one-time token is deleted before this
            // row is even written, and a trusted publishing token is deleted the moment it expires, which
            // for a slow publish can fall between authenticating the request and inserting this row.
            // Either way the foreign key would reject the insert, so the reference is only recorded for
            // tokens that stay. What identifies a trusted publish is the workflow it came from, held in
            // published_provenance.
            if (!ata.type().isEphemeral()) {
                extVersion.setPublishedWithId(ata.tokenId());
            }
            extVersion.setPublishedProvenance(ata.claims());
        }
        extVersion.setActive(false);

        // Lock the extension row while adding a version so a concurrent delete-all serializes
        // against this publish (and fails fast with a retry instead of removing it under us).
        var extension = repositories.findExtensionForUpdate(extensionName, namespace.getName());
        if (extension == null) {
            // Nothing got locked: the extension does not exist yet, and a FOR UPDATE on a row that
            // does not exist locks nothing. Serialize concurrent publications that create the same
            // extension on the namespace row instead, then look again — a racing publish may have
            // created the extension in the meantime.
            // This is only reached while holding no extension lock, so the namespace lock is always
            // taken before the extension lock and cannot invert the lock order of another path.
            repositories.lockNamespace(namespace);
            extension = repositories.findExtensionForUpdate(extensionName, namespace.getName());
        }

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
            var existingVersion = repositories
                    .findVersion(extVersion.getVersion(), extVersion.getTargetPlatform(), extension);
            if (existingVersion != null) {
                throw new ErrorResultException(
                        alreadyPublishedMessage(namespaceName, extensionName, existingVersion));
            }
        }

        extension.setLastUpdatedDate(extVersion.getTimestamp());
        // Only the owning side is set. Extension.versions is mappedBy this field and has no cascade, so
        // adding to it persists nothing; on an existing extension the collection is lazy and untouched,
        // making the add a queued operation Hibernate discards at commit (HHH90030005).
        extVersion.setExtension(extension);

        validateLicense(processor, extVersion);
        validateIcon(processor);
        validateFileResources(processor, extVersion);
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

        if (isMalicious(namespaceName, extensionName)) {
            throw new ErrorResultException(
                    NamingUtil.toExtensionId(namespaceName, extensionName) + " is a known malicious extension");
        }
    }

    /**
     * This method checks whether the metadata as contained in {@code extension.vsixmanifest} matches
     * the data in {@code package.json}.
     */
    private void validatePackageMetadata(
            ExtensionProcessor processor,
            String namespaceName,
            String extensionName,
            ExtensionVersion extVersion
    ) {
        var packageMetadata = processor.getPackageMetadata();

        // Check for equality on these items, normally they should match,
        // e.g. when the vsix extension package has been created by tools like vsce

        if (!Strings.CI.equals(namespaceName, packageMetadata.publisher())) {
            throw new ErrorResultException("Publisher in extension.vsixmanifest and package.json does not match.");
        }

        if (!Strings.CI.equals(extensionName, packageMetadata.name())) {
            throw new ErrorResultException("Extension name in extension.vsixmanifest and package.json does not match.");
        }

        if (!Strings.CI.equals(extVersion.getVersion(), packageMetadata.version())) {
            throw new ErrorResultException(
                    "Extension version in extension.vsixmanifest and package.json does not match.");
        }

        // Do not check if the displayName property is equal as it is not fully understood yet how VS Code / vsce processes
        // that property. There are some projects, where the value is `%displayName%` and publication will fail.
        // if (!Strings.CI.equals(extVersion.getDisplayName(), packageMetadata.displayName())) {
        //    throw new ErrorResultException("Display name in extension.vsixmanifest and package.json does not match.");
        // }
    }

    private void validateLicense(ExtensionProcessor processor, ExtensionVersion extVersion) {
        if (isLicenseRequired()) {
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

    private void validateIcon(ExtensionProcessor processor) {
        var iconPath = processor.getIconPath();
        if (iconPath != null && unsupportedIconExtensions.test(iconPath)) {
            throw new ErrorResultException("This extension cannot be accepted as it uses an unsupported icon format.");
        }
    }

    private void validateFileResources(ExtensionProcessor processor, ExtensionVersion extVersion) {
        // Validate that all file resources are readable/accessible during synchronous publishing
        // to avoid failing during async publishing and report errors directly back to publishers.
        // This creates unnecessary temp files, however these are usually small, and thus it is
        // acceptable in this case.
        var names = new ArrayList<String>();
        processor.getFileResources(extVersion, tempFile -> names.add(tempFile.getResource().getName()));

        // Additionally check that there are no name collisions within the file resources as the file
        // names are used as object key when uploading to the storage provider and would overwrite each other.
        checkForNameCollisions(extVersion, names);
    }

    /**
     * Object keys within a version share one flat namespace (see {@code IStorageService#getObjectKey}), so a
     * resource name that collides with another resource's name, or with the reserved name of the binary, its
     * checksum or its signature, would silently overwrite that other file on the storage backend once uploaded.
     * Resource names come from the VSIX manifest and are therefore attacker-controlled, so this has to be
     * rejected before any of these files are stored.
     * <p>
     * Storage object keys are matched case-sensitively, but {@code FileResourceJooqRepository#findByName}
     * looks up a requested file case-insensitively and returns whichever resource sorts first by type, so a
     * same-name-different-case resource can still shadow another one at the API level even though it would not
     * overwrite it in storage. Collisions are therefore checked case-insensitively as well.
     */
    private void checkForNameCollisions(ExtensionVersion extVersion, List<String> names) {
        var reservedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        reservedNames.add(NamingUtil.toFileFormat(extVersion, ".vsix"));
        reservedNames.add(NamingUtil.toFileFormat(extVersion, ".sha256"));
        reservedNames.add(NamingUtil.toFileFormat(extVersion, ".sigzip"));

        // Collect every colliding name instead of failing on the first one, so a publisher fixing one
        // collision does not have to republish repeatedly just to discover the next.
        var collisions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var name : names) {
            if (!reservedNames.add(name)) {
                collisions.add(name);
            }
        }

        if (collisions.isEmpty()) {
            return;
        }
        if (collisions.size() == 1) {
            throw new ErrorResultException(collisionMessage(collisions.first()));
        }
        throw new ErrorResultException(
                "Multiple file name collisions were found in the extension:\n"
                        + collisions.stream().map(this::collisionMessage).collect(Collectors.joining("\n")));
    }

    private String collisionMessage(String name) {
        return "Extension contains multiple files named '" + name + "' (case-insensitive)."
                + " Rename the conflicting asset so that it does not collide with another published file.";
    }

    private void validateMetadata(ExtensionVersion extVersion) {
        var metadataIssues = validator.validateMetadata(extVersion);
        if (!metadataIssues.isEmpty()) {
            if (metadataIssues.size() == 1) {
                throw new ErrorResultException(metadataIssues.getFirst().toString());
            }
            throw new ErrorResultException(
                    "Multiple issues were found in the extension metadata:\n"
                            + metadataIssues.stream().map(Object::toString).collect(Collectors.joining("\n")));
        }
    }

    private boolean isMalicious(String namespace, String extension) {
        try {
            var maliciousExtensionIds = extensionControl.getMaliciousExtensionIds();
            return maliciousExtensionIds.contains(NamingUtil.toExtensionId(namespace, extension));
        } catch (IOException e) {
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
    @Retryable(includes = Exception.class)
    public void publishAsync(TempFile extensionFile, ExtensionService extensionService) {
        publishReportingFailure(extensionFile, extensionService, null);
    }

    @Async
    @Retryable(includes = Exception.class)
    public void publishAsync(TempFile extensionFile, ExtensionService extensionService, ExtensionScan scan) {
        publishReportingFailure(extensionFile, extensionService, scan);
    }

    /**
     * Runs the publish and makes sure a failure leaves something behind that names the version it was
     * working on.
     * <p>
     * Everything below this point runs after the request that started it has already been answered, so a
     * failure here is invisible to whoever published: the row keeps {@code active = false} and the client
     * has been told the upload succeeded. What did get logged was Spring's
     * {@code SimpleAsyncUncaughtExceptionHandler} line, which names this method and not the extension, so
     * the version had to be matched up to the stack trace by hand - see #1450.
     * <p>
     * {@code Throwable} rather than {@code Exception}, because the failure that prompted this was an
     * {@link OutOfMemoryError}: an {@code Error} walked straight past the previous {@code catch}, so even
     * an instance with scanning enabled recorded nothing at all. Rethrown either way, so that the advice
     * around this still sees it and Spring's own handler keeps its account.
     */
    private void publishReportingFailure(
            TempFile extensionFile,
            ExtensionService extensionService,
            ExtensionScan scan
    ) {
        try {
            doPublish(extensionFile, extensionService, scan);
        } catch (Throwable failure) {
            logger.atError()
                    .setMessage("Publishing {} failed, the version stays inactive")
                    .addArgument(() -> NamingUtil.toLogFormat(extensionFile.getResource().getExtension()))
                    .setCause(failure)
                    .log();
            service.recordPublishError(extensionFile.getResource().getExtension(), describe(failure));
            // Null-safe: markScanAsErrored returns immediately when there is no scan to mark. An instance
            // that does not run scanning has only the column above.
            scanService.markScanAsErrored(scan, "Async processing failed: " + failure);
            throw failure;
        }
    }

    /**
     * The recorded form of a failure: its type and message, and nothing deeper.
     * <p>
     * A cause chain would carry file system paths, host names and connection strings out of the server
     * and into a column that admin tooling reads back, and none of it tells an operator more than the log
     * already has - which is where the stack trace stays.
     */
    private static String describe(Throwable failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : failure.getClass().getName() + ": " + message;
    }

    /**
     * Publish an extension - store files and optionally submit for scanning.
     */
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
                if (keyPair != null) {
                    try (var signature = integrityService.generateSignature(extensionFile, keyPair)) {
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
                        logger.warn(
                                "No scanners available, activating extension immediately: {}",
                                NamingUtil.toLogFormat(extVersion));
                        scanService.markScanPassed(scan);
                        service.activateExtension(extVersion, extensionService);
                    }
                    // If submission succeeded, extension remains inactive
                    // AsyncScanCompletionService will activate after scans complete
                } catch (Exception e) {
                    logger.error(
                            "Failed to submit scanner jobs for extension version: {}",
                            NamingUtil.toLogFormat(extVersion),
                            e);
                    scanService.markScanAsErrored(scan, "Failed to submit scanner jobs: " + e.getMessage());
                    // Extension remains inactive until scans complete or are manually approved
                }
            } else {
                logger.debug(
                        "Scanning disabled or no scan record, activating immediately: {}",
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
        if (signatureName != null) {
            service.mirrorResource(getSignatureResource(signatureName, extVersion));
        }
        try (var processor = new ExtensionProcessor(extensionFile)) {
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

    /**
     * Matches only the violation that a concurrently created extension causes, so that publications
     * failing on any other constraint are reported instead of being retried pointlessly.
     */
    public static class DuplicateExtensionPredicate implements MethodRetryPredicate {

        // Unique index on extension(namespace_id, upper(name)), see the V1_20 migration.
        private static final String UNIQUE_EXTENSION = "unique_extension";

        private static final Logger logger = LoggerFactory.getLogger(DuplicateExtensionPredicate.class);

        @Override
        public boolean shouldRetry(Method method, Throwable exception) {
            for (var cause = exception; cause != null; cause = cause.getCause()) {
                var isDuplicateExtension = cause instanceof ConstraintViolationException violation
                        && UNIQUE_EXTENSION.equals(violation.getConstraintName());
                // The constraint name is not always available, so fall back to the reported message.
                // The quotes keep this from matching unique_extension_version as well.
                isDuplicateExtension |= cause.getMessage() != null
                        && cause.getMessage().contains('"' + UNIQUE_EXTENSION + '"');

                if (isDuplicateExtension) {
                    logger.warn("Extension was created concurrently, retrying the publication", exception);
                    return true;
                }
            }
            return false;
        }
    }
}
