/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.Collection;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import org.eclipse.openvsx.entities.AdminScanDecision;
import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.UserData;

/**
 * Repository for admin decisions on quarantined scans.
 * Provides methods to query and persist allow/block decisions.
 */
public interface AdminScanDecisionRepository extends Repository<AdminScanDecision, Long> {

    /** Save a new or update an existing decision */
    AdminScanDecision save(AdminScanDecision decision);

    /** Find the decision for a specific scan */
    AdminScanDecision findByScan(ExtensionScan scan);

    /** Find the decision for a scan by scan ID (eagerly fetches the admin user) */
    @Query("SELECT d FROM AdminScanDecision d JOIN FETCH d.decidedBy WHERE d.scan.id = :scanId")
    AdminScanDecision findByScanId(long scanId);

    /** Count all ALLOWED decisions */
    long countByDecision(String decision);

    /** Count decisions made by a given admin user */
    long countByDecidedBy(UserData decidedBy);

    /**
     * Counts decisions where the associated scan has matching validation failures or threats.
     * Date filtering is based on the scan's started_at timestamp (when the scan began).
     */
    @Query(
        value = """
                SELECT COUNT(*) FROM admin_scan_decision d
                JOIN extension_scan s ON s.id = d.scan_id
                WHERE d.decision = :decision
                  AND (CAST(:startedFrom AS TIMESTAMP) IS NULL OR s.started_at >= :startedFrom)
                  AND (CAST(:startedTo AS TIMESTAMP) IS NULL OR s.started_at <= :startedTo)
                  AND (:applyCheckTypesFilter = false OR EXISTS (
                       SELECT 1 FROM extension_validation_failure f
                       WHERE f.scan_id = d.scan_id
                         AND f.validation_type IN (:checkTypes)
                         AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL OR f.enforced = :enforcedOnly)))
                  AND (:applyScannerNamesFilter = false OR EXISTS (
                       SELECT 1 FROM extension_threat t
                       WHERE t.scan_id = d.scan_id
                         AND t.scanner_type IN (:scannerNames)
                         AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL OR t.enforced = :enforcedOnly)))
                  AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL
                       OR :applyCheckTypesFilter = true
                       OR :applyScannerNamesFilter = true
                       OR EXISTS (SELECT 1 FROM extension_validation_failure f WHERE f.scan_id = d.scan_id AND f.enforced = :enforcedOnly)
                       OR EXISTS (SELECT 1 FROM extension_threat t WHERE t.scan_id = d.scan_id AND t.enforced = :enforcedOnly))
                """,
        nativeQuery = true
    )
    long countForStatistics(
            @Param("decision") String decision,
            @Nullable
            @Param("startedFrom") LocalDateTime startedFrom,
            @Nullable
            @Param("startedTo") LocalDateTime startedTo,
            @Param("checkTypes") Collection<String> checkTypes,
            @Param("applyCheckTypesFilter") boolean applyCheckTypesFilter,
            @Param("scannerNames") Collection<String> scannerNames,
            @Param("applyScannerNamesFilter") boolean applyScannerNamesFilter,
            @Nullable
            @Param("enforcedOnly") Boolean enforcedOnly
    );

}
