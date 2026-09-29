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
package org.eclipse.openvsx.admin;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.storage.StorageUtilService;

@Component
public class RemoveFileJobRequestHandler implements JobRequestHandler<RemoveFileJobRequest> {

    private final StorageUtilService storageUtil;

    public RemoveFileJobRequestHandler(StorageUtilService storageUtil) {
        this.storageUtil = storageUtil;
    }

    @Override
    @Job(name = "Remove file in storage", retries = 10)
    public void run(RemoveFileJobRequest jobRequest) throws Exception {
        storageUtil.removeFile(jobRequest.getResource());
    }
}
