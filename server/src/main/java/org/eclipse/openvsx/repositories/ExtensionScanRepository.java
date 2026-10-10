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
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ScanStatus;

/**
 * Repository for accessing ExtensionScan entities.
 */
public interface ExtensionScanRepository extends Repository<ExtensionScan, Long> {

    /** Save a new or update an existing scan */
    ExtensionScan save(ExtensionScan scan);

    /** Find a scan by its ID */
    ExtensionScan findById(long id);

    /** Find the most recent scan for a specific extension version */
    ExtensionScan findFirstByNamespaceNameAndExtensionNameAndExtensionVersionAndTargetPlatformOrderByStartedAtDesc(
            String namespaceName,
            String extensionName,
            String extensionVersion,
            String targetPlatform
    );

    /** Find all scans with a specific status */
    Streamable<ExtensionScan> findByStatus(ScanStatus status);

    /** Find the oldest N scans with a specific status (for batch processing) */
    @Query(value = """
            SELECT s.* FROM extension_scan s
            WHERE s.status = :status
            ORDER BY s.started_at ASC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<ExtensionScan> findOldestByStatus(@Param("status") String status, @Param("limit") int limit);

    /** Find oldest N scans with a specific status (convenience overload) */
    default List<ExtensionScan> findOldestByStatus(ScanStatus status, int limit) {
        return findOldestByStatus(status.name(), limit);
    }

    /** Count all scans with a specific status */
    long countByStatus(ScanStatus scanning);

    /** Delete a scan by ID */
    void deleteById(long id);

    /**
     * Count scans for statistics with all filters.
     *
     * Enforcement behavior matches the list endpoint:
     * - When checkTypes is specified: enforcement modifies that filter (AND logic)
     * - When scannerNames is specified: enforcement modifies that filter (AND logic)
     * - When neither is specified: enforcement filters on ANY validation failure or threat
     */
    @Query(
        value = """
                SELECT COUNT(*) FROM extension_scan s
                WHERE s.status = :status
                  AND (CAST(:startedFrom AS TIMESTAMP) IS NULL OR s.started_at >= :startedFrom)
                  AND (CAST(:startedTo AS TIMESTAMP) IS NULL OR s.started_at <= :startedTo)
                  AND (:applyCheckTypesFilter = false OR EXISTS (
                       SELECT 1 FROM extension_validation_failure f
                       WHERE f.scan_id = s.id
                         AND f.validation_type IN (:checkTypes)
                         AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL OR f.enforced = :enforcedOnly)))
                  AND (:applyScannerNamesFilter = false OR EXISTS (
                       SELECT 1 FROM extension_threat t
                       WHERE t.scan_id = s.id
                         AND t.scanner_type IN (:scannerNames)
                         AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL OR t.enforced = :enforcedOnly)))
                  AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL
                       OR :applyCheckTypesFilter = true
                       OR :applyScannerNamesFilter = true
                       OR EXISTS (SELECT 1 FROM extension_validation_failure f WHERE f.scan_id = s.id AND f.enforced = :enforcedOnly)
                       OR EXISTS (SELECT 1 FROM extension_threat t WHERE t.scan_id = s.id AND t.enforced = :enforcedOnly))
                """,
        nativeQuery = true
    )
    long countForStatistics(
            @Param("status") String status,
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

    /**
     * Full paginated query with ALL filters including validationType, scannerNames, enforcement, adminDecision, and check errors.
     *
     * Enforcement behavior:
     * - When validationType is specified: enforcement modifies that filter (AND logic)
     * - When threatScannerName is specified: enforcement modifies that filter (AND logic)
     * - When neither is specified: enforcement filters on ANY validation failure or threat
     */
    @Query(
        value = """
                SELECT s.* FROM extension_scan s
                WHERE (CAST(:statuses AS TEXT) IS NULL OR s.status IN (:statuses)
                       OR (:includeCheckErrors = true AND EXISTS (
                           SELECT 1 FROM scan_check_result r WHERE r.scan_id = s.id AND r.result = 'ERROR')))
                  AND (CAST(:namespace AS TEXT) IS NULL OR LOWER(s.namespace_name) LIKE LOWER('%' || :namespace || '%'))
                  AND (CAST(:publisher AS TEXT) IS NULL OR LOWER(s.publisher) LIKE LOWER('%' || :publisher || '%'))
                  AND (CAST(:name AS TEXT) IS NULL OR LOWER(s.extension_name) LIKE LOWER('%' || :name || '%')
                       OR LOWER(s.extension_display_name) LIKE LOWER('%' || :name || '%'))
                  AND (CAST(:startedFrom AS TIMESTAMP) IS NULL OR s.started_at >= :startedFrom)
                  AND (CAST(:startedTo AS TIMESTAMP) IS NULL OR s.started_at <= :startedTo)
                  AND (:applyCheckTypesFilter = false OR EXISTS (
                       SELECT 1 FROM extension_validation_failure f
                       WHERE f.scan_id = s.id
                         AND f.validation_type IN (:checkTypes)
                         AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL OR f.enforced = :enforcedOnly)))
                  AND (:applyScannerNamesFilter = false OR EXISTS (
                       SELECT 1 FROM extension_threat t
                       WHERE t.scan_id = s.id
                         AND t.scanner_type IN (:scannerNames)
                         AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL OR t.enforced = :enforcedOnly)))
                  AND (CAST(:enforcedOnly AS BOOLEAN) IS NULL
                       OR :applyCheckTypesFilter = true
                       OR :applyScannerNamesFilter = true
                       OR EXISTS (SELECT 1 FROM extension_validation_failure f WHERE f.scan_id = s.id AND f.enforced = :enforcedOnly)
                       OR EXISTS (SELECT 1 FROM extension_threat t WHERE t.scan_id = s.id AND t.enforced = :enforcedOnly))
                  AND (:applyAdminDecisionFilter = false OR (
                       (:filterAllowed = true AND EXISTS (SELECT 1 FROM admin_scan_decision d WHERE d.scan_id = s.id AND d.decision = 'ALLOWED'))
                       OR (:filterBlocked = true AND EXISTS (SELECT 1 FROM admin_scan_decision d WHERE d.scan_id = s.id AND d.decision = 'BLOCKED'))
                       OR (:filterNeedsReview = true AND s.status IN ('QUARANTINED', 'ERRORED') AND NOT EXISTS (SELECT 1 FROM admin_scan_decision d WHERE d.scan_id = s.id))))
                """,
        nativeQuery = true
    )
    Page<ExtensionScan> findScansFullyFiltered(
            @Nullable
            @Param("statuses") Collection<String> statuses,
            @Nullable
            @Param("namespace") String namespace,
            @Nullable
            @Param("publisher") String publisher,
            @Nullable
            @Param("name") String name,
            @Nullable
            @Param("startedFrom") LocalDateTime startedFrom,
            @Nullable
            @Param("startedTo") LocalDateTime startedTo,
            @Param("checkTypes") Collection<String> checkTypes,
            @Param("applyCheckTypesFilter") boolean applyCheckTypesFilter,
            @Param("scannerNames") Collection<String> scannerNames,
            @Param("applyScannerNamesFilter") boolean applyScannerNamesFilter,
            @Nullable
            @Param("enforcedOnly") Boolean enforcedOnly,
            @Param("applyAdminDecisionFilter") boolean applyAdminDecisionFilter,
            @Param("filterAllowed") boolean filterAllowed,
            @Param("filterBlocked") boolean filterBlocked,
            @Param("filterNeedsReview") boolean filterNeedsReview,
            @Param("includeCheckErrors") boolean includeCheckErrors,
            Pageable pageable
    );

}
