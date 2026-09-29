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
package org.eclipse.openvsx.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ExtensionThreat;

/**
 * Repository for extension threat detection results.
 */
public interface ExtensionThreatRepository extends Repository<ExtensionThreat, Long> {

    /** Save a new or update an existing threat */
    ExtensionThreat save(ExtensionThreat threat);

    /** Find all threats for a specific scan */
    Streamable<ExtensionThreat> findByScan(ExtensionScan scan);

    /** Find all threats for a scan with a specific scanner type */
    Streamable<ExtensionThreat> findByScanAndType(ExtensionScan scan, String type);

    /** Find distinct scanner types from all threats (for filter options) */
    @Query("SELECT DISTINCT t.type FROM ExtensionThreat t ORDER BY t.type")
    List<String> findDistinctScannerTypes();

    /** Find all threats for a specific scan job */
    List<ExtensionThreat> findByJobId(long jobId);
}
