/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionValidationFailure;

/**
 * Repository for accessing ExtensionValidationFailure entities.
 */
public interface ExtensionValidationFailureRepository extends Repository<ExtensionValidationFailure, Long> {

    /** Save a new or update an existing validation failure */
    ExtensionValidationFailure save(ExtensionValidationFailure failure);

    /** Find all validation failures for a specific scan */
    Streamable<ExtensionValidationFailure> findByScan(ExtensionScan scan);

    /**
     * Returns a sorted list of distinct check types.
     */
    @Query("select distinct f.checkType from ExtensionValidationFailure f order by f.checkType")
    List<String> findDistinctCheckTypes();

    /**
     * Find the extensions that have at least one failure of the given check type, one entry per
     * extension rather than one per failure, for the moderation views in the admin dashboard.
     * <p>
     * Each entry is returned as {@code <namespace>/<extension>}, both lower-cased. Namespace and
     * extension names are URL path segments, so neither can contain a slash and the key is
     * unambiguous. Callers resolve the individual failures per key.
     * <p>
     * Extensions are ordered by their most recent detection (newest first unless {@code ascending}),
     * with the name as a tie-breaker so that paging stays stable.
     * <p>
     * The state filter selects extensions by what became of them after the check ran: {@code
     * filterPublished} keeps extensions that exist and are active, {@code filterDeactivated} those
     * that exist but have been deactivated, and {@code filterRejected} those that never made it
     * into the registry because publication was blocked. Pass {@code applyStateFilter = false} to
     * keep all of them.
     */
    @Query(
        value = """
                SELECT LOWER(s.namespace_name) || '/' || LOWER(s.extension_name) AS extension_key
                FROM extension_validation_failure f
                JOIN extension_scan s ON s.id = f.scan_id
                WHERE f.validation_type = :checkType
                  AND (CAST(:namespace AS TEXT) IS NULL OR LOWER(s.namespace_name) LIKE LOWER('%' || :namespace || '%'))
                  AND (CAST(:publisher AS TEXT) IS NULL OR LOWER(s.publisher) LIKE LOWER('%' || :publisher || '%'))
                  AND (CAST(:name AS TEXT) IS NULL OR LOWER(s.extension_name) LIKE LOWER('%' || :name || '%')
                       OR LOWER(s.extension_display_name) LIKE LOWER('%' || :name || '%'))
                  AND (CAST(:detectedFrom AS TIMESTAMP) IS NULL OR f.detected_at >= :detectedFrom)
                  AND (CAST(:detectedTo AS TIMESTAMP) IS NULL OR f.detected_at <= :detectedTo)
                  AND (:applyStateFilter = false
                       OR (:filterPublished = true AND EXISTS (
                           SELECT 1 FROM extension e JOIN namespace n ON n.id = e.namespace_id
                           WHERE LOWER(e.name) = LOWER(s.extension_name)
                             AND LOWER(n.name) = LOWER(s.namespace_name)
                             AND e.active = true))
                       OR (:filterDeactivated = true AND EXISTS (
                           SELECT 1 FROM extension e JOIN namespace n ON n.id = e.namespace_id
                           WHERE LOWER(e.name) = LOWER(s.extension_name)
                             AND LOWER(n.name) = LOWER(s.namespace_name)
                             AND e.active = false))
                       OR (:filterRejected = true AND NOT EXISTS (
                           SELECT 1 FROM extension e JOIN namespace n ON n.id = e.namespace_id
                           WHERE LOWER(e.name) = LOWER(s.extension_name)
                             AND LOWER(n.name) = LOWER(s.namespace_name))))
                GROUP BY LOWER(s.namespace_name), LOWER(s.extension_name)
                ORDER BY CASE WHEN CAST(:ascending AS BOOLEAN) = true THEN MAX(f.detected_at) END ASC,
                         CASE WHEN CAST(:ascending AS BOOLEAN) = false THEN MAX(f.detected_at) END DESC,
                         LOWER(s.namespace_name), LOWER(s.extension_name)
                LIMIT :limit OFFSET :offset
                """,
        nativeQuery = true
    )
    List<String> findFlaggedExtensionKeys(
            @Param("checkType") String checkType,
            @Nullable
            @Param("namespace") String namespace,
            @Nullable
            @Param("publisher") String publisher,
            @Nullable
            @Param("name") String name,
            @Nullable
            @Param("detectedFrom") LocalDateTime detectedFrom,
            @Nullable
            @Param("detectedTo") LocalDateTime detectedTo,
            @Param("applyStateFilter") boolean applyStateFilter,
            @Param("filterPublished") boolean filterPublished,
            @Param("filterDeactivated") boolean filterDeactivated,
            @Param("filterRejected") boolean filterRejected,
            @Param("ascending") boolean ascending,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Count the extensions matched by {@link #findFlaggedExtensionKeys}, so the admin dashboard can
     * page through them and show totals per state.
     */
    @Query(
        value = """
                SELECT COUNT(*) FROM (
                    SELECT 1
                    FROM extension_validation_failure f
                    JOIN extension_scan s ON s.id = f.scan_id
                    WHERE f.validation_type = :checkType
                      AND (CAST(:namespace AS TEXT) IS NULL OR LOWER(s.namespace_name) LIKE LOWER('%' || :namespace || '%'))
                      AND (CAST(:publisher AS TEXT) IS NULL OR LOWER(s.publisher) LIKE LOWER('%' || :publisher || '%'))
                      AND (CAST(:name AS TEXT) IS NULL OR LOWER(s.extension_name) LIKE LOWER('%' || :name || '%')
                           OR LOWER(s.extension_display_name) LIKE LOWER('%' || :name || '%'))
                      AND (CAST(:detectedFrom AS TIMESTAMP) IS NULL OR f.detected_at >= :detectedFrom)
                      AND (CAST(:detectedTo AS TIMESTAMP) IS NULL OR f.detected_at <= :detectedTo)
                      AND (:applyStateFilter = false
                           OR (:filterPublished = true AND EXISTS (
                               SELECT 1 FROM extension e JOIN namespace n ON n.id = e.namespace_id
                               WHERE LOWER(e.name) = LOWER(s.extension_name)
                                 AND LOWER(n.name) = LOWER(s.namespace_name)
                                 AND e.active = true))
                           OR (:filterDeactivated = true AND EXISTS (
                               SELECT 1 FROM extension e JOIN namespace n ON n.id = e.namespace_id
                               WHERE LOWER(e.name) = LOWER(s.extension_name)
                                 AND LOWER(n.name) = LOWER(s.namespace_name)
                                 AND e.active = false))
                           OR (:filterRejected = true AND NOT EXISTS (
                               SELECT 1 FROM extension e JOIN namespace n ON n.id = e.namespace_id
                               WHERE LOWER(e.name) = LOWER(s.extension_name)
                                 AND LOWER(n.name) = LOWER(s.namespace_name))))
                    GROUP BY LOWER(s.namespace_name), LOWER(s.extension_name)
                ) flagged
                """,
        nativeQuery = true
    )
    long countFlaggedExtensions(
            @Param("checkType") String checkType,
            @Nullable
            @Param("namespace") String namespace,
            @Nullable
            @Param("publisher") String publisher,
            @Nullable
            @Param("name") String name,
            @Nullable
            @Param("detectedFrom") LocalDateTime detectedFrom,
            @Nullable
            @Param("detectedTo") LocalDateTime detectedTo,
            @Param("applyStateFilter") boolean applyStateFilter,
            @Param("filterPublished") boolean filterPublished,
            @Param("filterDeactivated") boolean filterDeactivated,
            @Param("filterRejected") boolean filterRejected
    );

    /**
     * Find all failures of the given check type for one extension, newest first.
     * <p>
     * Namespace and extension name must be passed lower-cased. The date range narrows the failures
     * the same way {@link #findFlaggedExtensionKeys} narrows the extensions, so that what a caller
     * lists for an extension matches why the extension was listed at all.
     */
    @Query("""
            select f from ExtensionValidationFailure f join fetch f.scan s
            where f.checkType = :checkType
              and lower(s.namespaceName) = :namespace
              and lower(s.extensionName) = :extension
              and (cast(:detectedFrom as LocalDateTime) is null or f.detectedAt >= :detectedFrom)
              and (cast(:detectedTo as LocalDateTime) is null or f.detectedAt <= :detectedTo)
            order by f.detectedAt desc, f.id desc
            """)
    List<ExtensionValidationFailure> findByCheckTypeAndExtension(
            @Param("checkType") String checkType,
            @Param("namespace") String namespace,
            @Param("extension") String extension,
            @Nullable
            @Param("detectedFrom") LocalDateTime detectedFrom,
            @Nullable
            @Param("detectedTo") LocalDateTime detectedTo
    );

    /**
     * Delete all failures of the given check type recorded for one extension and return how many
     * rows were removed. Used to clear a check that an administrator judged a false positive.
     * <p>
     * Namespace and extension name must be passed lower-cased.
     */
    @Modifying
    @Transactional
    @Query("""
            delete from ExtensionValidationFailure f
            where f.checkType = :checkType
              and f.scan in (select s from ExtensionScan s
                             where lower(s.namespaceName) = :namespace
                               and lower(s.extensionName) = :extension)
            """)
    int deleteByCheckTypeAndExtension(
            @Param("checkType") String checkType,
            @Param("namespace") String namespace,
            @Param("extension") String extension
    );
}
