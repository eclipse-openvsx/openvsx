/********************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import org.eclipse.openvsx.entities.DownloadIngestion;

public interface DownloadIngestionRepository extends Repository<DownloadIngestion, Long> {

    @Query(
        "select dc.name from DownloadIngestion dc where dc.success = true and dc.storageType = ?1 and dc.name in(?2)"
    )
    List<String> findAllSucceededDownloadIngestionsByStorageTypeAndNameIn(
            String storageType,
            List<String> names
    );

    @Query(
        "select dc.name from DownloadIngestion dc where dc.success = false and dc.storageType = ?1 and dc.name in(?2)"
    )
    List<String> findAllFailedDownloadIngestionsByStorageTypeAndNameIn(String storageType, List<String> names);

    @Query("select count(dc) from DownloadIngestion dc where dc.success = false")
    long countFailedDownloadIngestions();

    // Successful ingestions of the given files processed at or after the cutoff. Used to keep a
    // backfill from replaying logs that were already ingested for analytics (i.e. after analytics
    // went live), which would double-count their events.
    @Query(
        "select dc.name from DownloadIngestion dc"
                + " where dc.success = true and dc.storageType = ?1 and dc.name in(?2) and dc.processedOn >= ?3"
    )
    List<String> findAllDownloadIngestionsProcessedSince(
            String storageType,
            List<String> names,
            LocalDateTime processedOn
    );
}
