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
package org.eclipse.openvsx.analytics.ingestion;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.eclipse.openvsx.entities.FileResource;

/**
 * Produces raw download records from some backing store, typically CDN or storage access logs.
 * A source knows how to list, read and clean up its named items (e.g. log files) and declares its
 * storage type, coverage, enablement and recurring schedule; the {@link DownloadIngestionRunner}
 * drives the ingestion and owns idempotency, failure handling and the time budget.
 * <p>
 * Sources are conditional beans that only exist when their configuration is present.
 */
public interface DownloadRecordSource {

    /**
     * The storage type this source ingests downloads for, see {@code FileResource.STORAGE_*}.
     */
    String getStorageType();

    /**
     * Whether this source is fully configured and ready to ingest. A source bean may exist (its
     * primary property is set) while still being disabled because a dependent service is not.
     */
    boolean isEnabled();

    /**
     * The cron expression on which this source's ingestion job should recur, in UTC.
     */
    String getCronSchedule();

    /**
     * Returns whether downloads of the given file are counted by this source. Download requests
     * for covered files are not counted on the request path, otherwise they would be counted
     * twice.
     */
    boolean covers(FileResource resource);

    /**
     * Lists the names of the items that are candidates for ingestion, in pages.
     */
    Iterator<List<String>> listBatches();

    /**
     * Reads one item and returns the downloads it contains.
     */
    List<RawDownloadRecord> read(String name) throws IOException;

    /**
     * Cleans up one successfully processed (or previously processed) item, e.g. by deleting or
     * archiving it.
     */
    void finish(String name);
}
