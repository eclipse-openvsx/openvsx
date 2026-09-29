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

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.eclipse.openvsx.entities.ScanCheckResult;

/**
 * Repository for ScanCheckResult entities.
 * Provides query methods for retrieving check execution history.
 */
@Repository
public interface ScanCheckResultRepository extends JpaRepository<ScanCheckResult, Long> {

    /**
     * Find all check results for a scan ID, ordered by start time.
     */
    List<ScanCheckResult> findByScanIdOrderByStartedAtAsc(long scanId);

    /**
     * Check if a check result exists for a scan and check type.
     */
    boolean existsByScanIdAndCheckType(long scanId, String checkType);

    /**
     * Delete the check result recorded for a specific scanner job.
     */
    void deleteByScannerJobId(Long scannerJobId);
}
