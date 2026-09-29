/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.scanning;

import org.jobrunr.jobs.lambdas.JobRequest;

/**
 * JobRunr request to poll a single scan job.
 * <p>
 * This request is enqueued by the AsyncScanPollingService every 30 seconds
 * for each pending async scan job. JobRunr workers process these requests
 * in parallel, calling the appropriate scanner to check job status.
 */
public class ScannerPollRequest implements JobRequest {

    private long scanJobId;

    /**
     * Default constructor required by JobRunr for deserialization.
     */
    public ScannerPollRequest() {
    }

    /**
     * Create a poll request for a specific scan job.
     */
    public ScannerPollRequest(long scanJobId) {
        this.scanJobId = scanJobId;
    }

    /**
     * Get the scan job ID to poll.
     */
    public long getScanJobId() {
        return scanJobId;
    }

    /**
     * Set the scan job ID.
     */
    public void setScanJobId(long scanJobId) {
        this.scanJobId = scanJobId;
    }

    /**
     * Tell JobRunr which handler processes this request.
     */
    @Override
    public Class<ScannerPollHandler> getJobRequestHandler() {
        return ScannerPollHandler.class;
    }
}
