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

import { ScanResult } from '../../../context/scan-admin';

// ============================================================================
// Types
// ============================================================================

export interface DetailBadge {
    label: string;
    isEnforced: boolean;
    type: 'threat' | 'validation' | 'error';
}

// ============================================================================
// Constants
// ============================================================================

export const ICON_SIZE = 48;

// ============================================================================
// Status Utilities
// ============================================================================

export const isRunning = (status: ScanResult['status']): boolean => {
    return status === 'STARTED' || status === 'VALIDATING' || status === 'SCANNING';
};

/**
 * Determines whether the scan card badge/strip should show the striped effect.
 * Only shows striping when the hypothetical status would be DIFFERENT from the current status.
 *
 * - PASSED: Stripe if any threats or validationFailures exist (hypothetical would be QUARANTINED or AUTO REJECTED)
 * - QUARANTINED: Stripe only if validationFailures exist (hypothetical would be AUTO REJECTED)
 *   - Unenforced threats don't cause striping since hypothetical would still be QUARANTINED
 * - AUTO REJECTED: Never stripe (hypothetical would still be AUTO REJECTED)
 */
export const shouldShowStriped = (scan: ScanResult): boolean => {
    switch (scan.status) {
        case 'PASSED':
            // Any issues mean hypothetical status would be different (QUARANTINED or AUTO REJECTED)
            return scan.threats.length > 0 || scan.validationFailures.length > 0;

        case 'QUARANTINED':
            // Only validation failures would change status to AUTO REJECTED
            // Unenforced threats would still result in QUARANTINED (same status, no stripe)
            return scan.validationFailures.length > 0;

        case 'AUTO REJECTED':
            // Scan could only be AUTO REJECTED if there's at least one enforced validation failure
            // Unenforced validation failures would still result in AUTO REJECTED (same status, no stripe)
            return false;

        default:
            return false;
    }
};

export const getHypotheticalStatus = (scan: ScanResult): ScanResult['status'] | null => {
    if (!shouldShowStriped(scan)) return null;

    const hasUnenforcedValidationFailures = scan.validationFailures.some(v => !v.enforcedFlag);
    const hasUnenforcedThreats = scan.threats.some(t => !t.enforcedFlag);

    if (hasUnenforcedValidationFailures) {
        return 'AUTO REJECTED';
    }

    if (hasUnenforcedThreats) {
        return 'QUARANTINED';
    }

    return null;
};

export const getDetailBadges = (scan: ScanResult): DetailBadge[] => {
    const badges: DetailBadge[] = [];

    scan.threats.forEach(threat => {
        if (threat.type) {
            badges.push({
                label: threat.type,
                isEnforced: threat.enforcedFlag,
                type: 'threat'
            });
        }
    });

    scan.validationFailures.forEach(failure => {
        badges.push({
            label: failure.type,
            isEnforced: failure.enforcedFlag,
            type: 'validation'
        });
    });

    // Add badges for check results with ERROR status
    scan.checkResults?.forEach(checkResult => {
        if (checkResult.result === 'ERROR') {
            badges.push({
                label: checkResult.checkType,
                // Use "enforced" styling (solid) for required checks, striped for optional
                isEnforced: checkResult.required !== false,
                type: 'error'
            });
        }
    });

    return badges;
};

// ============================================================================
// Theme Utilities
// ============================================================================

export const getStatusColorSx = (status: ScanResult['status'], theme: any) => {
    switch (status) {
        case 'PASSED':
            return {
                backgroundColor: theme.palette.passed.dark,
                color: theme.palette.passed.light,
                '& .MuiChip-icon': {
                    color: theme.palette.passed.light,
                },
            };
        case 'QUARANTINED':
            return {
                backgroundColor: theme.palette.quarantined.dark,
                color: theme.palette.quarantined.light,
                '& .MuiChip-icon': {
                    color: theme.palette.quarantined.light,
                },
            };
        case 'AUTO REJECTED':
            return {
                backgroundColor: theme.palette.rejected.dark,
                color: theme.palette.rejected.light,
                '& .MuiChip-icon': {
                    color: theme.palette.rejected.light,
                },
            };
        case 'ERROR':
            return {
                backgroundColor: theme.palette.errorStatus.dark,
                color: theme.palette.errorStatus.light,
                '& .MuiChip-icon': {
                    color: theme.palette.errorStatus.light,
                },
            };
        default:
            return {};
    }
};

export const getStatusBarColor = (status: ScanResult['status'], theme: any) => {
    switch (status) {
        case 'PASSED':
            return theme.palette.passed.dark;
        case 'QUARANTINED':
            return theme.palette.quarantined.dark;
        case 'AUTO REJECTED':
            return theme.palette.rejected.dark;
        case 'ERROR':
            return theme.palette.errorStatus.dark;
        default:
            return 'transparent';
    }
};

// ============================================================================
// UI Utilities
// ============================================================================

export const shouldShowExpandButton = (scan: ScanResult): boolean => {
    const hasErrorMessage = scan.status === 'ERROR' && !!scan.errorMessage;
    const hasCheckResults = scan.checkResults && scan.checkResults.length > 0;
    return scan.threats.length > 0 || scan.validationFailures.length > 0 || hasCheckResults || hasErrorMessage;
};

export const hasDownload = (scan: ScanResult): boolean => {
    return scan.status === 'PASSED' || scan.status === 'QUARANTINED' || scan.status === 'ERROR';
};

export const getFileName = (url?: string): string => {
    if (!url) return 'extension.vsix';
    const parts = url.split('/');
    return parts[parts.length - 1] || 'extension.vsix';
};
