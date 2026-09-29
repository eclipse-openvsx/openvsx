/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.admin;

import java.util.Objects;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import org.eclipse.openvsx.json.ChangeNamespaceJson;

public class ChangeNamespaceJobRequest implements JobRequest {

    private ChangeNamespaceJson data;

    public ChangeNamespaceJobRequest() {
    }

    public ChangeNamespaceJobRequest(ChangeNamespaceJson data) {
        this.data = data;
    }

    @Override
    public Class<? extends JobRequestHandler> getJobRequestHandler() {
        return ChangeNamespaceJobRequestHandler.class;
    }

    public ChangeNamespaceJson getData() {
        return data;
    }

    public void setData(ChangeNamespaceJson data) {
        this.data = data;
    }

    // equals and hashCode are used to make AdminAPITest.testChangeNamespace test succeed
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChangeNamespaceJobRequest that = (ChangeNamespaceJobRequest) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }
}
