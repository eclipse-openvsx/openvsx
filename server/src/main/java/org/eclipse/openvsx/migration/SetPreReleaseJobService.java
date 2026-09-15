/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.migration;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;

@Component
public class SetPreReleaseJobService {

    private final EntityManager entityManager;

    public SetPreReleaseJobService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public List<ExtensionVersion> getExtensionVersions(MigrationJobRequest jobRequest, Logger logger) {
        var extension = entityManager.find(Extension.class, jobRequest.getEntityId());
        logger.atInfo()
                .setMessage("Setting pre-release for: {}")
                .addArgument(() -> NamingUtil.toExtensionId(extension))
                .log();

        return extension.getVersions();
    }

    @Transactional
    public void updatePreviewAndPreRelease(ExtensionVersion extVersion, TempFile extensionFile) {
        boolean preRelease;
        boolean preview;
        try (var extProcessor = new ExtensionProcessor(extensionFile)) {
            preRelease = extProcessor.isPreRelease();
            preview = extProcessor.isPreview();
        }

        // find-then-set rather than merge: merge writes every column of the detached copy, so
        // anything that changed on the row since it was loaded gets reverted. Only the field
        // below is this method's to change - see #989.
        var managedVersion = entityManager.find(ExtensionVersion.class, extVersion.getId());
        if (managedVersion == null) {
            return;
        }
        managedVersion.setPreRelease(preRelease);
        managedVersion.setPreview(preview);
    }
}
