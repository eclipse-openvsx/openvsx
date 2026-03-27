/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation 
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
package org.eclipse.openvsx.scanning;

import jakarta.persistence.EntityManager;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import java.io.IOException;

/**
 * Service for retrieving extension files for scanning.
 * <p>
 * Downloads files to temporary locations with automatic cleanup via TempFile.
 * Works with all storage backends (local, S3, Azure, Google Cloud).
 */
@Service
public class ScannerFileProvider {

    protected final Logger logger = LoggerFactory.getLogger(ScannerFileProvider.class);

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final StorageUtilService storageUtil;
    
    public ScannerFileProvider(
            EntityManager entityManager,
            RepositoryService repositories,
            StorageUtilService storageUtil
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.storageUtil = storageUtil;
    }

    /**
     * Get the extension file for scanning.
     * 
     * Downloads the .vsix file to a temp location. Use in try-with-resources
     * for automatic cleanup.
     */
    @Nonnull
    public TempFile getExtensionFile(long extensionVersionId) throws ScannerException {
        try {
            // Find the extension version using EntityManager
            ExtensionVersion extVersion = entityManager.find(ExtensionVersion.class, extensionVersionId);
            if (extVersion == null) {
                throw new ScannerException("Extension version not found: " + extensionVersionId);
            }

            // Get the download file resource (the .vsix file)
            FileResource download = repositories.findFileByType(extVersion, FileResource.DOWNLOAD);

            if (download == null) {
                throw new ScannerException(
                    "Download file not found for extension version: " + extensionVersionId
                );
            }

            logger.debug("Downloading extension file for scanning: extension version {}",
                NamingUtil.toLogFormat(extVersion));

            // Download file to temporary location
            // The TempFile is AutoCloseable and will be deleted when closed
            TempFile extensionFile = storageUtil.downloadFile(download);

            if (extensionFile == null) {
                throw new ScannerException("Failed to download file for extension version: " + extensionVersionId);
            }

            logger.debug("Extension file ready for scanning: {} (extension version: {})",
                extensionFile.getPath(), NamingUtil.toLogFormat(extVersion));

            return extensionFile;

        } catch (ScannerException e) {
            throw e;
        } catch (IOException e) {
            throw new ScannerException("Failed to download file for extension version " + extensionVersionId, e);
        } catch (Exception e) {
            throw new ScannerException("Failed to retrieve file for extension version " + extensionVersionId, e);
        }
    }
}

