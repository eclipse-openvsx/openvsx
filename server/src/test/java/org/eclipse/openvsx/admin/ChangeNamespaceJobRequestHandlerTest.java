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
package org.eclipse.openvsx.admin;

import org.jobrunr.jobs.annotations.Job;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeNamespaceJobRequestHandlerTest {

    /**
     * One retry rides out a transient storage error, and is what recreates a target namespace that was
     * deleted during the copy (see {@link ChangeNamespaceService}); without a setting, JobRunr retries ten times.
     */
    @Test
    void retriesOnceWhenItFails() throws NoSuchMethodException {
        var run = ChangeNamespaceJobRequestHandler.class.getMethod("run", ChangeNamespaceJobRequest.class);

        var job = run.getAnnotation(Job.class);

        assertThat(job).isNotNull();
        assertThat(job.retries()).isEqualTo(1);
    }
}
