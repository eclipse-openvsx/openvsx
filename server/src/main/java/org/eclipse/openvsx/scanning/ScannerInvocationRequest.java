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
import org.jspecify.annotations.NonNull;

/**
 * JobRunr request to invoke a scanner for an extension version.
 * <p>
 * This request is enqueued by the ScanningService for each registered scanner.
 * JobRunr workers process these requests in parallel, invoking scanners
 * and creating ScanJob records.
 * <p>
 * The actual file is retrieved by scanners using ScanFileService,
 * avoiding the need to pass file paths through JobRunr.
 */
public class ScannerInvocationRequest implements JobRequest {

    private String scannerType;
    private long extensionVersionId;
    private String scanId;

    /**
     * Default constructor required by JobRunr for deserialization.
     */
    public ScannerInvocationRequest() {
    }

    /**
     * Create a scanner invocation request.
     */
    public ScannerInvocationRequest(
            @NonNull String scannerType,
            long extensionVersionId,
            @NonNull String scanId
    ) {
        this.scannerType = scannerType;
        this.extensionVersionId = extensionVersionId;
        this.scanId = scanId;
    }

    public String getScannerType() {
        return scannerType;
    }

    public void setScannerType(String scannerType) {
        this.scannerType = scannerType;
    }

    public long getExtensionVersionId() {
        return extensionVersionId;
    }

    public void setExtensionVersionId(long extensionVersionId) {
        this.extensionVersionId = extensionVersionId;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    @Override
    public Class<ScannerInvocationHandler> getJobRequestHandler() {
        return ScannerInvocationHandler.class;
    }

    @Override
    public String toString() {
        return "ScannerInvocationJobRequest{" +
                "scannerType='" + scannerType + '\'' +
                ", scanId='" + scanId + '\'' +
                ", extensionVersionId=" + extensionVersionId +
                '}';
    }
}
