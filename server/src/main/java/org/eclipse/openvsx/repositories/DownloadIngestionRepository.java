/******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
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
package org.eclipse.openvsx.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import org.eclipse.openvsx.entities.DownloadIngestion;

public interface DownloadIngestionRepository extends Repository<DownloadIngestion, Long> {

    // Settled: counters and analytics are both done, so the item can be cleaned up.
    @Query(
        "select dc.name from DownloadIngestion dc where dc.success = true and dc.analyticsStatus <> 'PENDING' "
                + "and dc.storageType = ?1 and dc.name in(?2)"
    )
    List<String> findAllSucceededDownloadIngestionsByStorageTypeAndNameIn(
            String storageType,
            List<String> names
    );

    // Counters are updated; the item has to stay until its analytics events are stored.
    @Query(
        "select dc.name from DownloadIngestion dc where dc.success = true and dc.analyticsStatus = 'PENDING' "
                + "and dc.storageType = ?1 and dc.name in(?2)"
    )
    List<String> findAllPendingAnalyticsDownloadIngestionsByStorageTypeAndNameIn(
            String storageType,
            List<String> names
    );

    @Modifying
    @Query(
        "update DownloadIngestion dc set dc.analyticsStatus = 'DONE' "
                + "where dc.analyticsStatus = 'PENDING' and dc.storageType = ?1 and dc.name = ?2"
    )
    int markDownloadIngestionAnalyticsDone(String storageType, String name);

    @Query("select count(dc) from DownloadIngestion dc where dc.analyticsStatus = 'PENDING'")
    long countPendingAnalyticsDownloadIngestions();

    @Query(
        "select dc.name from DownloadIngestion dc where dc.success = false and dc.storageType = ?1 and dc.name in(?2)"
    )
    List<String> findAllFailedDownloadIngestionsByStorageTypeAndNameIn(String storageType, List<String> names);

    @Query("select count(dc) from DownloadIngestion dc where dc.success = false")
    long countFailedDownloadIngestions();
}
