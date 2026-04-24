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

// ============================================================================
// Domain Types
// ============================================================================

export type ScanStatus = 'STARTED' | 'VALIDATING' | 'SCANNING' | 'PASSED' | 'QUARANTINED' | 'AUTO REJECTED' | 'ERROR';

export interface ValidationFailure {
    id: string;
    type: string;
    ruleName: string;
    reason: string;
    dateDetected: string;
    enforcedFlag: boolean;
}

export interface Threat {
    id: string;
    fileName: string | null;      // Null if scanner doesn't report file-level info
    fileHash: string | null;      // Null if scanner doesn't report file hashes
    fileExtension: string | null;
    type: string;
    ruleName: string;
    severity?: string;
    enforcedFlag: boolean;
    reason: string;
    dateDetected: string;
}

export interface AdminDecision {
    decision: string;
    decidedBy: string;
    dateDecided: string;
}

export interface CheckResult {
    checkType: string;
    category: 'PUBLISH_CHECK' | 'SCANNER_JOB';
    result: 'PASSED' | 'QUARANTINE' | 'REJECT' | 'ERROR';
    startedAt: string;
    completedAt: string | null;
    durationMs: number | null;
    filesScanned: number | null;
    findingsCount: number | null;
    summary: string | null;
    errorMessage: string | null;
    /** Whether this check was required (errors block publishing). Null for scanner jobs. */
    required: boolean | null;
    /** Deep link to the external scanner's dashboard for this job. Null if not applicable. */
    externalUrl: string | null;
}

export interface ScanResult {
    id: string;
    displayName: string;
    namespace: string;
    extensionName: string;
    publisher: string;
    publisherUrl: string | null;
    version: string;
    targetPlatform: string;
    universalTargetPlatform: boolean;
    status: ScanStatus;
    dateScanStarted: string;
    dateScanEnded: string | null;
    dateQuarantined: string | null;
    dateRejected: string | null;
    adminDecision: AdminDecision | null;
    threats: Threat[];
    validationFailures: ValidationFailure[];
    checkResults: CheckResult[];
    extensionIcon?: string;
    downloadUrl: string | null;
    errorMessage: string | null;
}

// ============================================================================
// State Types
// ============================================================================

export type DateRangeType = 'today' | 'last7days' | 'last30days' | 'last90days' | 'all';
export type EnforcementType = 'enforced' | 'notEnforced' | 'all';
export type ConfirmActionType = 'allow' | 'block' | 'delete' | null;
export type FileActionType = 'allow' | 'block' | 'delete' | null;
export type FileDecisionType = string;

export interface ScanCounts {
    STARTED: number;
    VALIDATING: number;
    SCANNING: number;
    PASSED: number;
    QUARANTINED: number;
    AUTO_REJECTED: number;
    ERROR: number;
    ALLOWED: number;
    BLOCKED: number;
    NEEDS_REVIEW: number;
}

/**
 * Unified file decision type for the /files API
 * Represents a file that has been allowed or blocked by an admin
 */
export interface FileDecision {
    id: string;
    fileName: string;
    fileHash: string;
    fileType: string;
    decision: FileDecisionType;
    decidedBy: string;
    dateDecided: string;
    displayName: string;
    namespace: string;
    extensionName: string;
    publisher: string;
    version: string;
    scanId?: string;
}

export interface FileDecisionCounts {
    allowed: number;
    blocked: number;
    total: number;
}

export interface ScanState {
    // Tab state
    selectedTab: number;

    // Search state
    publisherQuery: string;
    namespaceQuery: string;
    nameQuery: string;

    // Pagination state
    currentPage: number;
    pageSize: number;

    // Global filter state
    dateRange: DateRangeType;
    enforcement: EnforcementType;

    // File-specific filter state
    fileDateRange: DateRangeType;

    // Tab-specific filter state
    statusFilters: Set<string>;
    quarantineFilters: Set<string>;
    threatScannerFilters: Set<string>;
    validationTypeFilters: Set<string>;

    // Available filter options (from API)
    filterOptionsLoaded: boolean;
    availableValidationTypes: string[];
    availableThreatScanners: string[];

    // Menu anchor state
    filterMenuAnchor: HTMLElement | null;
    quarantineFilterMenuAnchor: HTMLElement | null;
    autoRejectedFilterMenuAnchor: HTMLElement | null;

    // Selection state
    quarantinedChecked: Record<string, boolean>;
    scanDecisions: Record<string, string>;
    filesChecked: Set<string>;

    // Dialog state
    confirmDialogOpen: boolean;
    confirmAction: ConfirmActionType;
    fileDialogOpen: boolean;
    fileActionType: FileActionType;

    // Scan data state (from /scans API)
    scans: ScanResult[];
    totalScans: number;
    isLoadingScans: boolean;
    scanCounts: ScanCounts | null;
    refreshTrigger: number;
    lastRefreshed: Date | null;
    autoRefresh: boolean;

    // File data state (from /files API) - for Allowed Files and Blocked Files tabs
    files: FileDecision[];
    totalFiles: number;
    isLoadingFiles: boolean;
    fileCounts: FileDecisionCounts | null;
}

// ============================================================================
// Action Types
// ============================================================================

export type ScanAction =
    // Tab actions
    | { type: 'SET_TAB'; payload: number }

    // Search actions
    | { type: 'SET_PUBLISHER_QUERY'; payload: string }
    | { type: 'SET_NAMESPACE_QUERY'; payload: string }
    | { type: 'SET_NAME_QUERY'; payload: string }
    | { type: 'CLEAR_SEARCH' }

    // Pagination actions
    | { type: 'SET_PAGE'; payload: number }
    | { type: 'RESET_PAGE' }

    // Global filter actions
    | { type: 'SET_DATE_RANGE'; payload: DateRangeType }
    | { type: 'SET_ENFORCEMENT'; payload: EnforcementType }
    | { type: 'SET_FILE_DATE_RANGE'; payload: DateRangeType }

    // Tab-specific filter actions
    | { type: 'TOGGLE_STATUS_FILTER'; payload: string }
    | { type: 'SET_STATUS_FILTERS'; payload: Set<string> }
    | { type: 'TOGGLE_QUARANTINE_FILTER'; payload: string }
    | { type: 'SET_QUARANTINE_FILTERS'; payload: Set<string> }
    | { type: 'TOGGLE_THREAT_SCANNER_FILTER'; payload: string }
    | { type: 'SET_THREAT_SCANNER_FILTERS'; payload: Set<string> }
    | { type: 'TOGGLE_VALIDATION_TYPE_FILTER'; payload: string }
    | { type: 'SET_VALIDATION_TYPE_FILTERS'; payload: Set<string> }

    // Filter options actions (from API)
    | { type: 'SET_FILTER_OPTIONS_LOADED'; payload: boolean }
    | { type: 'SET_AVAILABLE_VALIDATION_TYPES'; payload: string[] }
    | { type: 'SET_AVAILABLE_THREAT_SCANNERS'; payload: string[] }

    // Menu anchor actions
    | { type: 'SET_FILTER_MENU_ANCHOR'; payload: HTMLElement | null }
    | { type: 'SET_QUARANTINE_FILTER_MENU_ANCHOR'; payload: HTMLElement | null }
    | { type: 'SET_AUTO_REJECTED_FILTER_MENU_ANCHOR'; payload: HTMLElement | null }

    // Selection actions
    | { type: 'SET_QUARANTINED_CHECKED'; payload: Record<string, boolean> }
    | { type: 'TOGGLE_QUARANTINED_CHECKED'; payload: { id: string; checked: boolean } }
    | { type: 'SELECT_ALL_QUARANTINED'; payload: ScanResult[] }
    | { type: 'DESELECT_ALL_QUARANTINED' }
    | { type: 'SET_SCAN_DECISIONS'; payload: Record<string, string> }
    | { type: 'SET_FILES_CHECKED'; payload: Set<string> }

    // Dialog actions
    | { type: 'OPEN_CONFIRM_DIALOG'; payload: ConfirmActionType }
    | { type: 'CLOSE_CONFIRM_DIALOG' }
    | { type: 'OPEN_FILE_DIALOG'; payload: FileActionType }
    | { type: 'CLOSE_FILE_DIALOG' }

    // Scan data actions (from /scans API)
    | { type: 'SET_SCANS'; payload: { scans: ScanResult[]; totalScans: number } }
    | { type: 'SET_LOADING_SCANS'; payload: boolean }
    | { type: 'SET_SCAN_COUNTS'; payload: ScanCounts | null }
    | { type: 'TRIGGER_REFRESH' }
    | { type: 'SET_LAST_REFRESHED'; payload: Date }
    | { type: 'SET_AUTO_REFRESH'; payload: boolean }

    // File data actions (from /files API)
    | { type: 'SET_FILES'; payload: { files: FileDecision[]; totalFiles: number } }
    | { type: 'SET_LOADING_FILES'; payload: boolean }
    | { type: 'SET_FILE_COUNTS'; payload: FileDecisionCounts | null }

    // Confirm action execution
    | { type: 'EXECUTE_CONFIRM_ACTION' };

// ============================================================================
// Initial State
// ============================================================================

export const initialScanState: ScanState = {
    // Tab state
    selectedTab: 0,

    // Search state
    publisherQuery: '',
    namespaceQuery: '',
    nameQuery: '',

    // Pagination state
    currentPage: 0,
    pageSize: 10,

    // Global filter state
    dateRange: 'all',
    enforcement: 'all', // Default for tab 0 (scans). Tabs 1 & 2 default to 'enforced' via SET_TAB

    // File-specific filter state
    fileDateRange: 'all',

    // Tab-specific filter state
    statusFilters: new Set(),
    quarantineFilters: new Set(),
    threatScannerFilters: new Set(),
    validationTypeFilters: new Set(),

    // Available filter options
    filterOptionsLoaded: false,
    availableValidationTypes: [],
    availableThreatScanners: [],

    // Menu anchor state
    filterMenuAnchor: null,
    quarantineFilterMenuAnchor: null,
    autoRejectedFilterMenuAnchor: null,

    // Selection state
    quarantinedChecked: {},
    scanDecisions: {},
    filesChecked: new Set(),

    // Dialog state
    confirmDialogOpen: false,
    confirmAction: null,
    fileDialogOpen: false,
    fileActionType: null,

    // Scan data state
    scans: [],
    totalScans: 0,
    isLoadingScans: false,
    scanCounts: null,
    refreshTrigger: 0,
    lastRefreshed: null,
    autoRefresh: true,

    // File data state
    files: [],
    totalFiles: 0,
    isLoadingFiles: false,
    fileCounts: null,
};
