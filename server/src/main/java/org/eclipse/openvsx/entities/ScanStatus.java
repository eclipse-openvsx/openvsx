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
package org.eclipse.openvsx.entities;

/**
 * Enum representing the status of an extension scan.
 * Tracks the complete lifecycle from upload through validation and scanning.
 */
public enum ScanStatus {
    /** Scan has been initiated */
    STARTED,

    /** Extension is undergoing pre-validation checks (name squatting, secrets, etc.) */
    VALIDATING,

    /** Extension is being scanned for malware/threats */
    SCANNING,

    /** Extension failed validation or scanning and was rejected */
    REJECTED,

    /** Extension flagged for admin review due to potential issues */
    QUARANTINED,

    /** Extension passed all checks and is ready for publication */
    PASSED,

    /** An error occurred during the scan process */
    ERRORED;

    /**
     * Returns true if this status is a completed state (no further transitions).
     */
    public boolean isCompleted() {
        return this == REJECTED || this == QUARANTINED || this == PASSED || this == ERRORED;
    }
}
