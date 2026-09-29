/********************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
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
package org.eclipse.openvsx.admin;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;

public class RemoveFileJobRequest implements JobRequest {

    private FileResource resource;

    public RemoveFileJobRequest() {
    }

    public RemoveFileJobRequest(FileResource resource) {
        setResource(resource);
    }

    public FileResource getResource() {
        return resource;
    }

    public void setResource(FileResource resource) {
        this.resource = copyResource(resource);
    }

    @Override
    public Class<? extends JobRequestHandler<?>> getJobRequestHandler() {
        return RemoveFileJobRequestHandler.class;
    }

    private FileResource copyResource(FileResource resource) {
        var extVersion = resource.getExtension();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();

        var namespaceCopy = new Namespace();
        namespaceCopy.setName(namespace.getName());

        var extensionCopy = new Extension();
        extensionCopy.setName(extension.getName());
        extensionCopy.setNamespace(namespaceCopy);

        var extVersionCopy = new ExtensionVersion();
        extVersionCopy.setExtension(extensionCopy);
        extVersionCopy.setVersion(extVersion.getVersion());
        extVersionCopy.setTargetPlatform(extVersion.getTargetPlatform());

        var resourceCopy = new FileResource();
        resourceCopy.setExtension(extVersionCopy);
        resourceCopy.setType(resource.getType());
        resourceCopy.setStorageType(resource.getStorageType());
        resourceCopy.setName(resource.getName());
        return resourceCopy;
    }
}
