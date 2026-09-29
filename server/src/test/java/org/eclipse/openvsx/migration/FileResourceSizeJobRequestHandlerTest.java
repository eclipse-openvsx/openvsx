/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.storage.FileNotFoundInStorageException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileResourceSizeJobRequestHandlerTest {

    @Mock
    MigrationService migrations;

    @Test
    void run_doesNothingWhenTheExtensionVersionIsGone() throws Exception {
        var jobRequest = new MigrationJobRequest<>(FileResourceSizeJobRequestHandler.class, 1L);
        when(migrations.getExtension(1L)).thenReturn(null);

        new FileResourceSizeJobRequestHandler(migrations).run(jobRequest);

        verify(migrations, never()).getFileResources(any());
    }

    @Test
    void run_recordsSizeForEveryUnsizedResourceOfTheExtensionVersionAndSkipsAlreadySizedOnes() throws Exception {
        var extVersion = extVersion();
        var unsized = fileResource(extVersion, "extension.vsix", null);
        var alreadySized = fileResource(extVersion, "README.md", 7L);
        var jobRequest = new MigrationJobRequest<>(FileResourceSizeJobRequestHandler.class, 1L);
        when(migrations.getExtension(1L)).thenReturn(extVersion);
        when(migrations.getFileResources(extVersion)).thenReturn(Streamable.of(unsized, alreadySized));
        when(migrations.getFileSize(unsized)).thenReturn(42L);

        new FileResourceSizeJobRequestHandler(migrations).run(jobRequest);

        verify(migrations).updateResourceSize(unsized);
        verify(migrations, never()).updateResourceSize(alreadySized);
        verify(migrations, never()).getFileSize(alreadySized);
    }

    @Test
    void run_skipsAMissingResourceButStillProcessesTheRestOfTheExtensionVersion() throws Exception {
        var extVersion = extVersion();
        var missing = fileResource(extVersion, "extension.vsix", null);
        var present = fileResource(extVersion, "README.md", null);
        var jobRequest = new MigrationJobRequest<>(FileResourceSizeJobRequestHandler.class, 1L);
        when(migrations.getExtension(1L)).thenReturn(extVersion);
        when(migrations.getFileResources(extVersion)).thenReturn(Streamable.of(missing, present));
        when(migrations.getFileSize(missing)).thenThrow(new FileNotFoundInStorageException("gone"));
        when(migrations.getFileSize(present)).thenReturn(11L);

        var handler = new FileResourceSizeJobRequestHandler(migrations);

        assertThatCode(() -> handler.run(jobRequest)).doesNotThrowAnyException();

        verify(migrations, never()).updateResourceSize(missing);
        verify(migrations).updateResourceSize(present);
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

    private FileResource fileResource(ExtensionVersion extVersion, String name, Long size) {
        var resource = new FileResource();
        resource.setName(name);
        resource.setExtension(extVersion);
        resource.setSize(size);
        return resource;
    }
}
