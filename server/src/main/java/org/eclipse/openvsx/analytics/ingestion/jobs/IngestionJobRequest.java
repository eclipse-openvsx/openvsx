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
package org.eclipse.openvsx.analytics.ingestion.jobs;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * JobRunr request for one storage type's download log ingestion. The {@code storageType} selects
 * which {@link org.eclipse.openvsx.analytics.ingestion.DownloadRecordSource} the handler drives, so
 * a single generic handler can serve every source. The type bound mirrors {@code HandlerJobRequest}
 * (rather than the self-referential {@code MigrationJobRequest}) so JobRunr can serialize the
 * recurring job without JSON type resolution recursing.
 */
public class IngestionJobRequest<T extends JobRequestHandler<?>> implements JobRequest {

    private Class<T> handler;
    private String storageType;

    // needed for serialization by jobrunr
    public IngestionJobRequest() {
    }

    public IngestionJobRequest(Class<T> handler, String storageType) {
        this.handler = handler;
        this.storageType = storageType;
    }

    @Override
    public Class<? extends JobRequestHandler<?>> getJobRequestHandler() {
        return handler;
    }

    public String getStorageType() {
        return storageType;
    }
}
