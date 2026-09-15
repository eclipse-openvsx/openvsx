/********************************************************************************
 * Copyright (c) 2024 STMicroelectronics and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.migration;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;

@Component
public class CheckPotentiallyMaliciousExtensionVersionsService {

    protected final Logger logger = LoggerFactory.getLogger(CheckPotentiallyMaliciousExtensionVersionsService.class);

    private final EntityManager entityManager;

    public CheckPotentiallyMaliciousExtensionVersionsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void checkPotentiallyMaliciousExtensionVersion(ExtensionVersion extVersion, TempFile extensionFile) {
        boolean isMalicious;
        try (var extProcessor = new ExtensionProcessor(extensionFile)) {
            isMalicious = extProcessor.isPotentiallyMalicious();
            if (isMalicious) {
                logger.atWarn()
                        .setMessage("Extension version is potentially malicious: {}")
                        .addArgument(() -> NamingUtil.toLogFormat(extVersion))
                        .log();
            }
        }

        // find-then-set rather than merge: merge writes every column of the detached copy, so
        // anything that changed on the row since it was loaded gets reverted. Only the field
        // below is this method's to change - see #989.
        var managedVersion = entityManager.find(ExtensionVersion.class, extVersion.getId());
        if (managedVersion == null) {
            return;
        }
        managedVersion.setPotentiallyMalicious(isMalicious);
    }
}
