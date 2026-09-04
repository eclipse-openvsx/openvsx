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
    const success = (obj as SuccessResult | null | undefined)?.success;
    return typeof success === 'string';
}

export interface ErrorResult {
    error: string;
}
export function isError(obj: unknown): obj is ErrorResult {
    const error = (obj as ErrorResult | null | undefined)?.error;
    return typeof error === 'string' && Boolean(error);
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
    verified?: boolean;
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

/**
 * Registry-wide statistics for one month, as archived by the monthly job or computed on the fly for
 * the month in progress. Mirrors the server's `AdminStatisticsJson`.
 *
 * Every figure except `downloads` is a point-in-time snapshot rather than a total over the month;
 * `downloads` is the growth in `downloadsTotal` since the previous month.
 */
export interface AdminStatistics {
    year: number;
    month: number;
    extensions: number;
    downloads: number;
    downloadsTotal: number;
    publishers: number;
    averageReviewsPerExtension: number;
    namespaceOwners: number;
    extensionsByRating: { rating: number; extensions: number }[];
    publishersByExtensionsPublished: { extensionsPublished: number; publishers: number }[];
    topMostActivePublishingUsers: { userLoginName: string; publishedExtensionVersions: number }[];
    topNamespaceExtensions: { namespace: string; extensions: number }[];
    topNamespaceExtensionVersions: { namespace: string; extensionVersions: number }[];
    topMostDownloadedExtensions: { extensionIdentifier: string; downloads: number }[];
    error?: string;
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
    /** True when this version was published through a trusted publishing workflow. */
    publishedWithTrustedPublishing?: boolean;
    verified: boolean;
    // key: version, value: url
    allVersions: { [version: string]: UrlString };
    active?: boolean;
    removed?: boolean;
    reviewStatus?: 'published' | 'under_review' | 'rejected';
    reviewMessage?: string;
    // True when this version's latest scan found that its namespace already exists in a referenced
    // external gallery and is not verified - the namespace needs to be verified/claimed to activate it.
    namespaceOwnershipConflict?: boolean;

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

    deprecated: boolean;
    replacement?: {
        url: string;
        displayName: string;
    };
    downloadable: boolean;
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

export interface TargetPlatformActive {
    targetPlatform: string;
    active: boolean;
    // Whether this target platform version has been removed (soft-deleted). A removed version is a
    // permanent tombstone: hidden and non-republishable. Only an admin purge frees it.
    removed: boolean;
}

export interface VersionTargetPlatforms {
    version: string;
    targetPlatforms: TargetPlatformActive[];
    // Whether the current user may delete this version. Omitted (undefined) in contexts where deletion
    // is unrestricted (admin) or not applicable (public); explicitly false for namespace members who
    // did not publish this version and therefore may only delete their own versions.
    canDelete?: boolean;
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
    };
    additionalLogins?: UserData[];
}

export interface UserRelationships {
    user: UserData;
    namespaces: NamespaceDetails[];
}

export interface UserSearchResult {
    content: UserRelationships[];
    page: {
        size: number;
        number: number;
        totalElements: number;
        totalPages: number;
    };
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

export const CATEGORIES = [
    'AI',
    'Programming Languages',
    'Snippets',
    'Linters',
    'Themes',
    'Debuggers',
    'Formatters',
    'Keymaps',
    'SCM Providers',
    'Other',
    'Extension Packs',
    'Language Packs',
    'Data Science',
    'Machine Learning',
    'Visualization',
    'Notebooks'
] as const;

export type ExtensionCategory = (typeof CATEGORIES)[number];

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
    // present only when the current user may manage trusted publishers for this namespace
    trustedPublishingUrl?: UrlString;
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

export interface TrustedPublisherInput {
    key: string;
    // form label / help text for the field
    description: string;
    optional: boolean;
}

export interface TrustedPublisherProvider {
    id: string;
    name: string;
    url: UrlString;
    registrationInputs: TrustedPublisherInput[];
}

export interface TrustedPublisherStatus {
    enabled: boolean;
    allowed: boolean;
    // present only when the feature is enabled and the current user is allowed to use it
    trustedPublisherProviders?: TrustedPublisherProvider[];
}

export interface TrustedPublisherRequest {
    provider: string;
    namespace: string;
    extension: string;
    registration: { [key: string]: string };
}

export interface TrustedPublisher {
    id: number;
    provider: string;
    namespace: string;
    extension: string;
    registration: { [key: string]: string };
    createdTimestamp?: TimestampString;
}

export interface TrustedPublisherList {
    trustedPublishers: TrustedPublisher[];
    // extensions a trusted publisher can still be registered for: active, and not registered yet
    // (the server allows at most one registration per extension)
    registrableExtensions: string[];
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
    version: string;
    maxExtensionSize?: number;
}

export interface LoginProviders {
    loginProviders: Record<string, string>;
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
}

export interface CustomerList {
    customers: Customer[];
}

export interface CustomerMembership {
    customer: string;
    user: UserData;
}

export interface CustomerMembershipList {
    customerMemberships: CustomerMembership[];
}

export interface RateLimitToken {
    id: number;
    value?: string;
    description?: string;
    createdTimestamp: TimestampString;
}

export interface UsageStats {
    windowStart: number; // epoch seconds in UTC
    duration: number; // in seconds
    count: number;
}

export interface UsageStatsList {
    stats: UsageStats[];
    dailyP95?: number;
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

export interface Settings {
    readOnly: boolean;
}

export interface SearchIndex {
    enabled: boolean;
    implementation: 'elasticsearch' | 'database' | 'none';
    indexExists: boolean;
    /** Absent when there is no index to count - the database engine searches the tables directly. */
    indexedDocuments?: number;
    activeExtensions: number;
    maxResultWindow?: number;
}

export interface ConsistencyCheck {
    id: string;
    name: string;
    description: string;
    currentFindingsCount: number;
}

export interface ConsistencyCheckList {
    checks: ConsistencyCheck[];
}

export interface ConsistencyFinding {
    entityId: number;
    label: string;
    detail: string;
}

export interface ConsistencyFindingList {
    findings: ConsistencyFinding[];
}
