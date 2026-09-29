/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.adapter;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

public class VSCodeIdNewExtensionJobRequest implements JobRequest {

    private String namespace;
    private String extension;

    public VSCodeIdNewExtensionJobRequest() {
    }

    public VSCodeIdNewExtensionJobRequest(String namespace, String extension) {
        this.namespace = namespace;
        this.extension = extension;
    }

    @Override
    public Class<? extends JobRequestHandler> getJobRequestHandler() {
        return VSCodeIdNewExtensionJobRequestHandler.class;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }
}
