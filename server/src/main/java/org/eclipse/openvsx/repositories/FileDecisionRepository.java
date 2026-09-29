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
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import org.eclipse.openvsx.entities.FileDecision;
import org.eclipse.openvsx.entities.UserData;

/**
 * Repository for file allow/block list decisions.
 */
public interface FileDecisionRepository extends Repository<FileDecision, Long> {

    /** Save a new or update an existing file decision */
    FileDecision save(FileDecision decision);

    /** Find a decision by its ID (eagerly fetches the admin user) */
    @Query("SELECT f FROM FileDecision f JOIN FETCH f.decidedBy WHERE f.id = :id")
    FileDecision findById(@Param("id") long id);

    /** Find a decision by file hash (eagerly fetches the admin user) */
    @Query("SELECT f FROM FileDecision f JOIN FETCH f.decidedBy WHERE f.fileHash = :fileHash")
    FileDecision findByFileHash(@Param("fileHash") String fileHash);

    /**
     * Find all blocked decisions for a set of file hashes.
     * Used by BlocklistCheckService for efficient batch lookups during publishing.
     */
    @Query(
        "SELECT f FROM FileDecision f JOIN FETCH f.decidedBy WHERE f.fileHash IN :fileHashes AND f.decision = 'BLOCKED'"
    )
    List<FileDecision> findBlockedByFileHashIn(@Param("fileHashes") java.util.Set<String> fileHashes);

    /** Count all decisions with a specific decision value */
    long countByDecision(String decision);

    /** Count decisions made by a given admin user */
    long countByDecidedBy(UserData decidedBy);

    /** Delete a decision by ID */
    void deleteById(long id);

    /**
     * Paginated query with optional filters for decision, publisher, namespace, name, and date range.
     */
    @Query(
        value = """
                SELECT f.*, u.id AS user_id, u.login_name, u.email, u.full_name, u.avatar_url,
                       u.provider, u.provider_url, u.auth_id, u.role, u.eclipse_token, u.eclipse_person_id
                FROM file_decision f
                JOIN user_data u ON u.id = f.decided_by_id
                WHERE (CAST(:decision AS TEXT) IS NULL OR f.decision = :decision)
                  AND (CAST(:publisher AS TEXT) IS NULL OR LOWER(f.publisher) LIKE LOWER('%' || :publisher || '%'))
                  AND (CAST(:namespace AS TEXT) IS NULL OR LOWER(f.namespace_name) LIKE LOWER('%' || :namespace || '%'))
                  AND (CAST(:name AS TEXT) IS NULL OR LOWER(f.extension_name) LIKE LOWER('%' || :name || '%')
                       OR LOWER(f.display_name) LIKE LOWER('%' || :name || '%')
                       OR LOWER(f.file_name) LIKE LOWER('%' || :name || '%'))
                  AND (CAST(:decidedFrom AS TIMESTAMP) IS NULL OR f.decided_at >= :decidedFrom)
                  AND (CAST(:decidedTo AS TIMESTAMP) IS NULL OR f.decided_at <= :decidedTo)
                """,
        countQuery = """
                SELECT COUNT(*) FROM file_decision f
                WHERE (CAST(:decision AS TEXT) IS NULL OR f.decision = :decision)
                  AND (CAST(:publisher AS TEXT) IS NULL OR LOWER(f.publisher) LIKE LOWER('%' || :publisher || '%'))
                  AND (CAST(:namespace AS TEXT) IS NULL OR LOWER(f.namespace_name) LIKE LOWER('%' || :namespace || '%'))
                  AND (CAST(:name AS TEXT) IS NULL OR LOWER(f.extension_name) LIKE LOWER('%' || :name || '%')
                       OR LOWER(f.display_name) LIKE LOWER('%' || :name || '%')
                       OR LOWER(f.file_name) LIKE LOWER('%' || :name || '%'))
                  AND (CAST(:decidedFrom AS TIMESTAMP) IS NULL OR f.decided_at >= :decidedFrom)
                  AND (CAST(:decidedTo AS TIMESTAMP) IS NULL OR f.decided_at <= :decidedTo)
                """,
        nativeQuery = true
    )
    Page<FileDecision> findFilesFiltered(
            @Param("decision") String decision,
            @Param("publisher") String publisher,
            @Param("namespace") String namespace,
            @Param("name") String name,
            @Param("decidedFrom") LocalDateTime decidedFrom,
            @Param("decidedTo") LocalDateTime decidedTo,
            Pageable pageable
    );

    /**
     * Count file decisions within a date range.
     */
    @Query(value = """
            SELECT COUNT(*) FROM file_decision
            WHERE decision = :decision
              AND (CAST(:decidedFrom AS TIMESTAMP) IS NULL OR decided_at >= :decidedFrom)
              AND (CAST(:decidedTo AS TIMESTAMP) IS NULL OR decided_at <= :decidedTo)
            """, nativeQuery = true)
    long countByDecisionAndDateRange(
            @Param("decision") String decision,
            @Param("decidedFrom") LocalDateTime decidedFrom,
            @Param("decidedTo") LocalDateTime decidedTo
    );
}
