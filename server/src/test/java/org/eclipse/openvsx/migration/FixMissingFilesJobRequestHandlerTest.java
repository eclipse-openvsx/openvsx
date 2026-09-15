/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.migration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixMissingFilesJobRequestHandlerTest {

    @Mock
    EntityManager entityManager;

    @Mock
    RepositoryService repositories;

    @Mock
    StorageUtilService storage;

    @Test
    void run_doesNotThrowWhenTheDownloadFileResourceRowIsEntirelyMissing() throws Exception {
        var extVersion = extVersion();
        var jobRequest = new MigrationJobRequest<>(FixMissingFilesJobRequestHandler.class, 1L);
        when(entityManager.find(ExtensionVersion.class, 1L)).thenReturn(extVersion);
        when(repositories.findFiles(extVersion)).thenReturn(Streamable.empty());

        var handler = new FixMissingFilesJobRequestHandler(entityManager, repositories, storage);

        assertThatCode(() -> handler.run(jobRequest)).doesNotThrowAnyException();
    }

    private ExtensionVersion extVersion() {
        var namespace = new Namespace();
        namespace.setName("foo");

        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform("universal");
        extVersion.setExtension(extension);
        return extVersion;
    }
}
