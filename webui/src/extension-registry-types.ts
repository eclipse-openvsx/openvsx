/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

export type UrlString = string;
export type TimestampString = string;

export interface SuccessResult {
    success: string;
}
export function isSuccess(obj: unknown): obj is SuccessResult {
    return typeof obj === 'object' && typeof (obj as any).success === 'string';
}

export interface ErrorResult {
    error: string;
}
export function isError(obj: unknown): obj is ErrorResult {
    return typeof obj === 'object' && typeof (obj as any).error === 'string';
}

export interface ReportedError {
    message: string;
    code?: number | string;
}

export interface SearchResult {
    offset: number;
    totalSize: number;
    extensions: SearchEntry[];
}

export interface SearchEntry {
    url: UrlString;
    // key: file type, value: url
    files: { [id: string]: UrlString };
    name: string;
    namespace: string;
    version: string;
    timestamp?: TimestampString;
    allVersions: {
        url: UrlString;
        // key: file type, value: url
        files: { [id: string]: UrlString };
        version: string;
        // key: engine, value: version constraint
        engines?: { [engine: string]: string };
    }[];
    averageRating?: number;
    reviewCount?: number;
    downloadCount?: number;
    displayName?: string;
    description?: string;
    deprecated: boolean;
}

export const VERSION_ALIASES = ['latest', 'pre-release'];

export interface Extension {
    namespaceUrl: UrlString;
    reviewsUrl: UrlString;
    // key: file type, value: url
    files: { [id: string]: UrlString };

    name: string;
    namespace: string;
    version: string;
    targetPlatform: string;
    preRelease?: boolean;
    publishedBy: UserData;
    verified: boolean;
    // key: version, value: url
    allVersions: { [version: string]: UrlString };
    active?: boolean;
    reviewStatus?: 'published' | 'under_review' | 'rejected';
    reviewMessage?: string;

    averageRating?: number;
    downloadCount: number;
    reviewCount: number;

    versionAlias: string[];
    timestamp: TimestampString;
    preview?: boolean;
    displayName?: string;
    namespaceDisplayName: string;
    description?: string;

    // key: engine, value: version constraint
    engines?: Record<string, string>;
    categories?: string[];
    tags?: string[];
    license?: string;
    homepage?: string;
    repository?: string;
    bugs?: string;
    markdown?: 'github' | 'standard';
    galleryColor: string;
    galleryTheme: 'light' | 'dark' | '';
    qna?: UrlString | 'marketplace' | 'false';
    badges?: Badge[];
    dependencies?: ExtensionReference[];
    bundledExtensions?: ExtensionReference[];

    // key: target platform, value: download link
    downloads: { [targetPlatform: string]: UrlString };
    allTargetPlatformVersions?: VersionTargetPlatforms[];

    deprecated: boolean
    replacement?: {
        url: string
        displayName: string
    }
    downloadable: boolean
}

export interface Badge {
    url: UrlString;
    href: UrlString;
    description: string;
}

export interface ExtensionReference {
    namespace: string;
    extension: string;
    version?: string;
}

export interface VersionTargetPlatforms {
    version: string;
    targetPlatforms: string[];
}

export type StarRating = 1 | 2 | 3 | 4 | 5;
export interface NewReview {
    rating: StarRating;
    title?: string;
    comment: string;
}

export interface ExtensionReview extends NewReview {
    user: UserData;
    timestamp: TimestampString;
}

export interface ExtensionReviewList {
    postUrl: UrlString;
    deleteUrl: UrlString;
    reviews: ExtensionReview[];
}

export interface UserData {
    loginName: string;
    tokensUrl: UrlString;
    createTokenUrl: UrlString;
    fullName?: string;
    avatarUrl?: UrlString;
    homepage?: string;
    provider?: string;
    role?: string;
    publisherAgreement?: {
        status: 'none' | 'signed' | 'outdated';
        timestamp?: TimestampString;
    },
    additionalLogins?: UserData[];
}

export function isEqualUser(u1: UserData, u2: UserData): boolean {
    return u1.loginName === u2.loginName;
}

export interface PersonalAccessToken {
    id: number;
    value?: string;
    createdTimestamp: TimestampString;
    accessedTimestamp?: TimestampString;
    expiresTimestamp?: TimestampString;
    notified?: boolean;
    description: string;
    deleteTokenUrl: UrlString;
}

export type ExtensionCategory =
    'Programming Languages' |
    'Snippets' |
    'Linters' |
    'Themes' |
    'Debuggers' |
    'Formatters' |
    'Keymaps' |
    'SCM Providers' |
    'Other' |
    'Extension Packs' |
    'Language Packs' |
    'Data Science' |
    'Machine Learning' |
    'Visualization' |
    'Notebooks';

export interface CsrfTokenJson {
    value: string;
    header: string;
}

export interface NamespaceMembership {
    namespace: string;
    role: MembershipRole;
    user: UserData;
}

export interface NamespaceMembershipList {
    namespaceMemberships: NamespaceMembership[];
}

export interface Namespace {
    name: string;
    extensions: { [key: string]: string };
    verified: boolean;
    membersUrl: UrlString;
    roleUrl: UrlString;
    detailsUrl: UrlString;
}

export interface NamespaceDetails {
    name: string;
    displayName: string;
    description?: string;
    logo?: UrlString;
    logoBytes?: string;
    website?: UrlString;
    supportLink?: UrlString;
    socialLinks: { [key: string]: UrlString | undefined };
    extensions?: SearchEntry[];
}

export interface PublisherInfo {
    user: UserData;
    extensions: Extension[];
    activeAccessTokenNum: number;
}

export interface TargetPlatformVersion {
    targetPlatform: string;
    version: string;
    checked: boolean;
}

export interface RegistryVersion {
    version: string
}

export interface LoginProviders {
    loginProviders: Record<string, string>
}

export type MembershipRole = 'contributor' | 'owner';
export type SortBy = 'relevance' | 'timestamp' | 'rating' | 'downloadCount';
export type SortOrder = 'asc' | 'desc';

// Scan and file decision types (used by admin scan UI)
export interface ScanResultJson {
    id: string;
    namespace: string;
    extensionName: string;
    version: string;
    displayName: string;
    publisher: string;
    extensionIcon?: string;
    downloadUrl?: string;
    publisherUrl?: string;
    status: string;
    dateScanStarted: string;
    dateScanEnded?: string;
    errorMessage?: string;
    dateQuarantined?: string;
    dateRejected?: string;
    threats?: Array<{
        id: string;
        fileName: string;
        fileHash: string;
        type: string;
        severity?: string;
        reason: string;
        fileExtension: string;
        dateDetected: string;
        ruleName: string;
        enforcedFlag?: boolean;
    }>;
    validationFailures?: Array<{
        id: string;
        type: string;
        ruleName: string;
        reason: string;
        dateDetected: string;
        enforcedFlag: boolean;
    }>;
    adminDecision?: {
        decision: string;
        decidedBy: string;
        dateDecided: string;
    };
}

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

export interface ScanResultsResponse {
    success?: string;
    warning?: string;
    error?: string;
    offset: number;
    totalSize: number;
    scans: ScanResultJson[];
}

export interface ScanFilterOptions {
    validationTypes: string[];
    threatScannerNames: string[];
}

export interface FileDecisionJson {
    id: string;
    fileName: string;
    fileHash: string;
    fileType: string;
    decision: string;
    decidedBy: string;
    dateDecided: string;
    displayName: string;
    namespace: string;
    extensionName: string;
    publisher: string;
    version: string;
    scanId?: string;
}

export interface FilesResponse {
    success?: string;
    warning?: string;
    error?: string;
    offset: number;
    totalSize: number;
    files: FileDecisionJson[];
}

export interface FileDecisionCountsJson {
    allowed: number;
    blocked: number;
    total: number;
}

export interface ScanDecisionRequest {
    scanIds: string[];
    decision: string;
}

export interface ScanDecisionResult {
    scanId: string;
    success: boolean;
    error?: string;
}

export interface ScanDecisionResponse {
    processed: number;
    successful: number;
    failed: number;
    results: ScanDecisionResult[];
}

export interface FileDecisionRequest {
    fileHashes: string[];
    decision: string;
}

export interface FileDecisionResult {
    fileHash: string;
    success: boolean;
    error?: string;
}

export interface FileDecisionResponse {
    processed: number;
    successful: number;
    failed: number;
    results: FileDecisionResult[];
}

export interface FileDecisionDeleteRequest {
    fileIds: string[];
}

export interface FileDecisionDeleteResult {
    fileId: string;
    success: boolean;
    error?: string;
}

export interface FileDecisionDeleteResponse {
    processed: number;
    successful: number;
    failed: number;
    results: FileDecisionDeleteResult[];
}

export enum TierType {
    FREE = 'FREE',
    SAFETY = 'SAFETY',
    NON_FREE = 'NON_FREE'
}

export enum RefillStrategy {
    GREEDY = 'GREEDY',
    INTERVAL = 'INTERVAL'
}

export interface Tier {
    name: string;
    description?: string;
    tierType: TierType;
    capacity: number;
    duration: number;
    refillStrategy: RefillStrategy;
}

export interface TierList {
    tiers: Tier[];
}

export enum EnforcementState {
    EVALUATION = 'EVALUATION',
    ENFORCEMENT = 'ENFORCEMENT'
}

export interface Customer {
    name: string;
    tier?: Tier;
    state: EnforcementState;
    cidrBlocks: string[];
    users?: UserData[];
}

export interface CustomerList {
    customers: Customer[];
}

export interface UsageStats {
    windowStart: number; // epoch seconds in UTC
    duration: number; // in seconds
    count: number;
}

export interface UsageStatsList {
    stats: UsageStats[];
}

export interface Log {
    timestamp: string;
    user: string;
    message: string;
}

export interface LogPageableList {
    content: Log[];
    page: {
        size: number;
        number: number;
        totalElements: number;
        totalPages: number;
    };
}
