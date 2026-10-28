/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import {
    Extension,
    UserData,
    ExtensionReviewList,
    PersonalAccessToken,
    SearchResult,
    NewReview,
    SuccessResult,
    CsrfTokenJson,
    Namespace,
    NamespaceDetails,
    MembershipRole,
    SortBy,
    SortOrder,
    UrlString,
    NamespaceMembershipList,
    PublisherInfo,
    RegistryVersion,
    DownloadSeries,
    SearchEntry,
    LoginProviders,
    ScanResultJson,
    ScanCounts,
    ScanResultsResponse,
    ScanFilterOptions,
    FilesResponse,
    FileDecisionCountsJson,
    ScanDecisionRequest,
    ScanDecisionResponse,
    FileDecisionRequest,
    FileDecisionResponse,
    FileDecisionDeleteRequest,
    FileDecisionDeleteResponse,
    Tier,
    SizeOverride,
    SizeOverrideList,
    TierList,
    Customer,
    CustomerList,
    UsageStatsList,
    LogPageableList,
    CustomerMembershipList,
    RateLimitToken,
    Settings,
    SiteSettings,
    UserSearchResult,
    TrustedPublisher,
    TrustedPublisherList,
    TrustedPublisherRequest,
    TrustedPublisherStatus,
    ConsistencyCheckList,
    ConsistencyFindingList,
    SearchExplain,
    CacheList,
    AnalyticsStatus,
    SearchIndex,
    AdminStatistics,
    UserAccess
} from './extension-registry-types';
import { createAbsoluteURL, addQuery } from './utils';
import { ErrorResponse, sendRequest } from './server-request';

export class ExtensionRegistryService {
    readonly admin: AdminService;

    constructor(
        readonly serverUrl: string = '',
        AdminConstructor: AdminServiceConstructor = AdminServiceImpl
    ) {
        this.admin = new AdminConstructor(this);
    }

    async getLoginProviders(signal: AbortSignal): Promise<Readonly<LoginProviders | SuccessResult>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'login-providers']);
        return sendRequest({ signal, endpoint });
    }

    getLogoutUrl(): string {
        return createAbsoluteURL([this.serverUrl, 'logout']);
    }

    getExtensionApiUrl(ext: { namespace: string; name: string; target?: string; version?: string }): string {
        const arr = [this.serverUrl, 'api', ext.namespace, ext.name];
        if (ext.target) {
            arr.push(ext.target);
        }
        if (ext.version) {
            arr.push(ext.version);
        }

        return createAbsoluteURL(arr);
    }

    /**
     * Fetches the download time series for an extension from the analytics endpoint, which responds
     * 404 where download analytics is not configured. Callers showing it should gate on
     * {@link SiteSettings.downloadAnalyticsVisible}. The server fixes the window: the daily
     * downloads of the last 52 weeks, ending today.
     */
    async getExtensionDownloadSeries(
        signal: AbortSignal,
        params: { namespace: string; name: string }
    ): Promise<Readonly<DownloadSeries>> {
        const endpoint = createAbsoluteURL([
            this.serverUrl,
            'api',
            params.namespace,
            params.name,
            'analytics',
            'downloads'
        ]);
        return sendRequest<DownloadSeries>({ signal, endpoint });
    }

    /** The public namespace listing, which names only the namespace's active extensions. */
    async getPublicNamespace(signal: AbortSignal, name: string): Promise<Readonly<Namespace>> {
        return sendRequest({ signal, endpoint: createAbsoluteURL([this.serverUrl, 'api', name]) });
    }

    async getNamespaceDetails(signal: AbortSignal, name: string): Promise<Readonly<NamespaceDetails>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', name, 'details']);
        return sendRequest({ signal, endpoint });
    }

    async setNamespaceDetails(endpoint: string, details: NamespaceDetails): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            payload: details,
            credentials: true,
            endpoint,
            headers
        });
    }

    async setNamespaceLogo(endpoint: string, logoFile: Blob, logoName: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;

        const form = new FormData();
        form.append('file', logoFile, logoName);
        endpoint = createAbsoluteURL([endpoint, 'logo']);
        return sendRequest({
            method: 'POST',
            payload: form,
            credentials: true,
            endpoint,
            headers
        });
    }

    async search(signal: AbortSignal, filter?: ExtensionFilter): Promise<Readonly<SearchResult>> {
        const query: { key: string; value: string | number }[] = [];
        if (filter) {
            if (filter.query) query.push({ key: 'query', value: filter.query });
            if (filter.category) query.push({ key: 'category', value: filter.category });
            if (filter.offset) query.push({ key: 'offset', value: filter.offset });
            if (filter.size) query.push({ key: 'size', value: filter.size });
            if (filter.sortBy) query.push({ key: 'sortBy', value: filter.sortBy });
            if (filter.sortOrder) query.push({ key: 'sortOrder', value: filter.sortOrder });
        }
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', '-', 'search'], query);
        return sendRequest({ signal, endpoint });
    }

    async getExtensionDetail(signal: AbortSignal, extensionUrl: UrlString): Promise<Readonly<Extension>> {
        return sendRequest({ signal, endpoint: extensionUrl });
    }

    async getExtensionReadme(signal: AbortSignal, extension: Extension): Promise<string> {
        return sendRequest({
            signal,
            endpoint: extension.files.readme,
            headers: { Accept: 'text/plain' },
            followRedirect: true
        });
    }

    async getExtensionChangelog(signal: AbortSignal, extension: Extension): Promise<string> {
        return sendRequest({
            signal,
            endpoint: extension.files.changelog,
            headers: { Accept: 'text/plain' },
            followRedirect: true
        });
    }

    async getExtensionIcon(signal: AbortSignal, extension: Extension | SearchEntry): Promise<string | undefined> {
        if (!extension.files?.icon) {
            return Promise.resolve(undefined);
        }

        return sendRequest({
            signal,
            endpoint: extension.files.icon,
            headers: { Accept: 'application/octet-stream' },
            followRedirect: true
        }).then(value => {
            const blob = value as Blob;
            return URL.createObjectURL(blob);
        });
    }

    async getExtensionReviews(signal: AbortSignal, extension: Extension): Promise<Readonly<ExtensionReviewList>> {
        return sendRequest({ signal, endpoint: extension.reviewsUrl });
    }

    async postReview(review: NewReview, postReviewUrl: UrlString): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            payload: review,
            credentials: true,
            endpoint: postReviewUrl,
            headers
        });
    }

    async deleteReview(deleteReviewUrl: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: deleteReviewUrl,
            headers
        });
    }

    async deleteUserReview(extension: Extension, user: UserData): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.serverUrl,
                'admin',
                'extension',
                extension.namespace,
                extension.name,
                'review',
                user.provider || 'github',
                user.loginName,
                'delete'
            ]),
            headers
        });
    }

    /** Rejects with a 200 error result when nobody is logged in. */
    async getUser(signal: AbortSignal): Promise<Readonly<UserData>> {
        return sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.serverUrl, 'user']),
            credentials: true
        });
    }

    /** Rejects with the error the last login failed with, which the registry keeps in the session. */
    async getUserAuthError(signal: AbortSignal): Promise<never> {
        const loginError = await sendRequest<Partial<ErrorResponse>>({
            signal,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'auth-error']),
            credentials: true
        });
        // The registry answers 200 with no `error` field; the status keeps the query client from retrying it.
        throw { ...loginError, status: 200 };
    }

    async getUserByName(signal: AbortSignal, name: string): Promise<Readonly<UserData>[]> {
        return sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'search', name]),
            credentials: true
        });
    }

    async getAccessTokens(signal: AbortSignal, user: UserData): Promise<Readonly<PersonalAccessToken>[]> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: user.tokensUrl
        });
    }

    async createAccessToken(
        user: UserData,
        description: string,
        scope?: { namespace?: string; extension?: string; publishingOnly?: boolean }
    ): Promise<Readonly<PersonalAccessToken>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;

        const endpoint = addQuery(user.createTokenUrl, [
            { key: 'description', value: description },
            { key: 'namespace', value: scope?.namespace },
            { key: 'extension', value: scope?.extension },
            { key: 'publishingOnly', value: scope?.publishingOnly ? 'true' : undefined }
        ]);
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint,
            headers
        });
    }

    async deleteAccessToken(token: PersonalAccessToken): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: token.deleteTokenUrl,
            headers
        });
    }

    async deleteAllAccessTokens(tokens: PersonalAccessToken[]): Promise<Readonly<SuccessResult>[]> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return await Promise.all(
            tokens.map(token =>
                sendRequest<SuccessResult>({
                    method: 'POST',
                    credentials: true,
                    endpoint: token.deleteTokenUrl,
                    headers
                })
            )
        );
    }

    async getCsrfToken(signal?: AbortSignal): Promise<Readonly<CsrfTokenJson>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'csrf'])
        });
    }

    async getNamespaces(signal: AbortSignal): Promise<Readonly<Namespace>[]> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'namespaces'])
        });
    }

    async getCustomers(signal: AbortSignal): Promise<Readonly<Customer>[]> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'customers'])
        });
    }

    async getUsageStats(signal: AbortSignal, customerName: string, date: Date): Promise<Readonly<UsageStatsList>> {
        const query: { key: string; value: string | number }[] = [];
        query.push({ key: 'date', value: date.toISOString() });

        return sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'customers', customerName, 'usage'], query),
            credentials: true
        });
    }

    async getNamespaceMembers(signal: AbortSignal, namespace: Namespace): Promise<Readonly<NamespaceMembershipList>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: namespace.membersUrl
        });
    }

    async setNamespaceMember(
        endpoint: UrlString,
        user: UserData,
        role: MembershipRole | 'remove'
    ): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        const query = [
            { key: 'user', value: user.loginName },
            { key: 'provider', value: user.provider },
            { key: 'role', value: role }
        ];
        return sendRequest({
            headers,
            method: 'POST',
            credentials: true,
            endpoint: addQuery(endpoint, query)
        });
    }

    // The trusted-publishing base URL for a namespace in the user context. Callers that hold a
    // Namespace use its server-provided `trustedPublishingUrl`; the extension page only has a name.
    userTrustedPublishingUrl(namespace: string): UrlString {
        return createAbsoluteURL([this.serverUrl, 'user', 'namespace', namespace, 'trusted-publishing']);
    }

    // Requires a logged-in user; the server answers 403 otherwise.
    async getTrustedPublishingStatus(signal: AbortSignal): Promise<Readonly<TrustedPublisherStatus>> {
        return sendRequest<TrustedPublisherStatus>({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', '-', 'trusted-publishing', 'status'])
        });
    }

    async getTrustedPublishers(
        signal: AbortSignal,
        trustedPublishingUrl: UrlString
    ): Promise<Readonly<TrustedPublisherList>> {
        const result = await sendRequest<TrustedPublisherList>({
            signal,
            credentials: true,
            endpoint: trustedPublishingUrl
        });
        return {
            trustedPublishers: result.trustedPublishers ?? [],
            registrableExtensions: result.registrableExtensions ?? []
        };
    }

    async registerTrustedPublisher(
        trustedPublishingUrl: UrlString,
        request: TrustedPublisherRequest
    ): Promise<Readonly<TrustedPublisher>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            payload: request,
            headers,
            endpoint: createAbsoluteURL([trustedPublishingUrl, 'create'])
        });
    }

    async deleteTrustedPublisher(trustedPublishingUrl: UrlString, id: number): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            headers,
            endpoint: createAbsoluteURL([trustedPublishingUrl, 'delete', `${id}`])
        });
    }

    async signPublisherAgreement(): Promise<Readonly<UserData>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest<UserData>({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'publisher-agreement']),
            headers
        });
    }

    async getStaticContent(signal: AbortSignal, url: string): Promise<string> {
        return sendRequest({
            signal,
            endpoint: url,
            headers: { Accept: 'text/plain' },
            followRedirect: true
        });
    }

    async publishExtension(extensionPackage: File): Promise<Readonly<Extension>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/octet-stream'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest<Extension>({
            method: 'POST',
            credentials: true,
            payload: extensionPackage,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', 'user', 'publish'])
        });
    }

    async createNamespace(name: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest<SuccessResult>({
            method: 'POST',
            credentials: true,
            payload: { name: name },
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', 'user', 'namespace', 'create'])
        });
    }

    async getExtensions(signal: AbortSignal): Promise<Readonly<Extension[]>> {
        const headers: Record<string, string> = {};

        return sendRequest<Extension[]>({
            signal,
            method: 'GET',
            credentials: true,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extensions'])
        });
    }

    async getExtension(signal: AbortSignal, namespace: string, extension: string): Promise<Readonly<Extension>> {
        const headers: Record<string, string> = {};

        return sendRequest<Extension>({
            signal,
            method: 'GET',
            credentials: true,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extension', namespace, extension])
        });
    }

    async deleteExtensions(req: {
        namespace: string;
        extension: string;
        targetPlatformVersions?: object[];
    }): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extension', req.namespace, req.extension, 'delete']),
            headers,
            payload: req.targetPlatformVersions
        });
    }

    /**
     * `revalidate` forces the response to be re-checked with the registry. It is served with
     * `max-age=300`, so a caller about to act on a value that an admin may have just changed - the
     * size ceiling - would otherwise be handed the same cached copy it is trying to get past.
     */
    async getRegistryVersion(
        signal?: AbortSignal,
        options: { revalidate?: boolean } = {}
    ): Promise<Readonly<RegistryVersion>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', 'version']);
        const headers = options.revalidate ? { 'Cache-Control': 'no-cache' } : undefined;
        return sendRequest({ signal, endpoint, headers });
    }

    async getSiteSettings(signal: AbortSignal): Promise<Readonly<SiteSettings>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', '-', 'settings']);
        return sendRequest({ signal, endpoint });
    }
}

export interface AdminService {
    getExtension(signal: AbortSignal, namespace: string, extension: string): Promise<Readonly<Extension>>;
    deleteExtensions(req: {
        namespace: string;
        extension: string;
        targetPlatformVersions?: object[];
    }): Promise<Readonly<SuccessResult>>;
    purgeExtensions(req: {
        namespace: string;
        extension: string;
        targetPlatformVersions?: object[];
    }): Promise<Readonly<SuccessResult>>;
    getNamespace(signal: AbortSignal, name: string): Promise<Readonly<Namespace>>;
    createNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>>;
    deleteNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>>;
    changeNamespace(req: {
        oldNamespace: string;
        newNamespace: string;
        removeOldNamespace: boolean;
        mergeIfNewNamespaceAlreadyExists: boolean;
    }): Promise<Readonly<SuccessResult>>;
    getPublisherInfo(signal: AbortSignal, provider: string, login: string): Promise<Readonly<PublisherInfo>>;
    getUsers(
        signal: AbortSignal,
        params?: { search?: string; role?: string; page?: number; size?: number }
    ): Promise<Readonly<UserSearchResult>>;
    updateUserAccess(provider: string, login: string, access: UserAccess): Promise<Readonly<SuccessResult>>;
    revokePublisherContributions(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    reinstatePublisherAgreement(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    revokeAccessTokens(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    forgetUser(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    getAllScans(
        signal: AbortSignal,
        params?: {
            size?: number;
            offset?: number;
            status?: string | string[];
            publisher?: string;
            namespace?: string;
            name?: string;
            validationType?: string[];
            threatScannerName?: string[];
            dateStartedFrom?: string;
            dateStartedTo?: string;
            enforcement?: 'enforced' | 'notEnforced' | 'all';
            adminDecision?: string[];
            exactStatus?: boolean;
        }
    ): Promise<Readonly<ScanResultsResponse>>;
    getScan(signal: AbortSignal, scanId: string): Promise<Readonly<ScanResultJson>>;
    retryFailedScannerJobs(scanId: string): Promise<Readonly<ScanResultJson>>;
    getScanCounts(
        signal: AbortSignal,
        params?: {
            dateStartedFrom?: string;
            dateStartedTo?: string;
            enforcement?: 'enforced' | 'notEnforced' | 'all';
            threatScannerName?: string[];
            validationType?: string[];
        }
    ): Promise<Readonly<ScanCounts>>;
    getScanFilterOptions(signal: AbortSignal): Promise<Readonly<ScanFilterOptions>>;
    // Files API
    getFiles(
        signal: AbortSignal,
        params?: {
            size?: number;
            offset?: number;
            decision?: string;
            publisher?: string;
            namespace?: string;
            name?: string;
            dateDecidedFrom?: string;
            dateDecidedTo?: string;
            sortBy?: string;
            sortOrder?: 'asc' | 'desc';
        }
    ): Promise<Readonly<FilesResponse>>;
    getFileCounts(
        signal: AbortSignal,
        params?: { dateDecidedFrom?: string; dateDecidedTo?: string }
    ): Promise<Readonly<FileDecisionCountsJson>>;
    // Decision APIs
    makeScanDecision(request: ScanDecisionRequest): Promise<Readonly<ScanDecisionResponse>>;
    makeFileDecision(request: FileDecisionRequest): Promise<Readonly<FileDecisionResponse>>;
    deleteFileDecisions(request: FileDecisionDeleteRequest): Promise<Readonly<FileDecisionDeleteResponse>>;
    getTiers(signal: AbortSignal): Promise<Readonly<TierList>>;
    createTier(tier: Tier): Promise<Readonly<Tier>>;
    updateTier(name: string, tier: Tier): Promise<Readonly<Tier>>;
    deleteTier(name: string): Promise<Readonly<SuccessResult>>;
    getSizeOverrides(signal: AbortSignal): Promise<Readonly<SizeOverrideList>>;
    createSizeOverride(override: SizeOverride): Promise<Readonly<SizeOverride>>;
    updateSizeOverride(id: number, override: SizeOverride): Promise<Readonly<SizeOverride>>;
    deleteSizeOverride(id: number): Promise<Readonly<SuccessResult>>;
    getCustomers(signal: AbortSignal): Promise<Readonly<CustomerList>>;
    getCustomer(signal: AbortSignal, name: string): Promise<Readonly<Customer>>;
    createCustomer(customer: Customer): Promise<Readonly<Customer>>;
    updateCustomer(name: string, customer: Customer): Promise<Readonly<Customer>>;
    deleteCustomer(name: string): Promise<Readonly<SuccessResult>>;
    getCustomerMembers(signal: AbortSignal, name: string): Promise<Readonly<CustomerMembershipList>>;
    addCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>>;
    removeCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>>;
    getUsageStats(signal: AbortSignal, customerName: string, date: Date): Promise<Readonly<UsageStatsList>>;
    getLogs(signal: AbortSignal, page?: number, size?: number, period?: string): Promise<Readonly<LogPageableList>>;
    getCustomerRateLimitTokens(signal: AbortSignal, customerName: string): Promise<Readonly<RateLimitToken[]>>;
    createCustomerRateLimitToken(customerName: string, description: string): Promise<Readonly<RateLimitToken>>;
    deleteCustomerRateLimitToken(customerName: string, tokenId: number): Promise<Readonly<SuccessResult>>;
    getSettings(signal: AbortSignal): Promise<Readonly<Settings>>;
    updateSettings(settings: Partial<Settings>): Promise<Readonly<Settings>>;
    getSearchIndex(signal: AbortSignal): Promise<Readonly<SearchIndex>>;

    /** Runs a search and reports how each result's score was arrived at. */
    explainSearch(signal: AbortSignal, query: string, size: number, offset: number): Promise<Readonly<SearchExplain>>;
    getAdminStatistics(signal: AbortSignal, year: number, month: number): Promise<Readonly<AdminStatistics>>;
    getAdminStatisticsCsvUrl(year: number, month: number): string;
    updateSearchIndex(): Promise<Readonly<SuccessResult>>;
    /** Every cache of every cache manager, with whatever each implementation can report. */
    getCaches(signal: AbortSignal): Promise<Readonly<CacheList>>;
    /** Clears one cache, or every cache when no cache is named. */
    clearCaches(cache?: { manager: string; name: string }): Promise<Readonly<SuccessResult>>;
    getAnalyticsStatus(signal: AbortSignal): Promise<Readonly<AnalyticsStatus>>;
    getConsistencyChecks(signal: AbortSignal): Promise<Readonly<ConsistencyCheckList>>;
    getConsistencyFindings(signal: AbortSignal, checkId: string): Promise<Readonly<ConsistencyFindingList>>;
    fixConsistencyFindings(checkId: string): Promise<Readonly<SuccessResult>>;
    fixConsistencyFinding(checkId: string, entityId: number): Promise<Readonly<SuccessResult>>;
}

export interface AdminServiceConstructor {
    new (registry: ExtensionRegistryService): AdminService;
}

export class AdminServiceImpl implements AdminService {
    constructor(readonly registry: ExtensionRegistryService) {}

    async getExtension(signal: AbortSignal, namespace: string, extension: string): Promise<Readonly<Extension>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'extension', namespace, extension])
        });
    }

    async deleteExtensions(req: {
        namespace: string;
        extension: string;
        targetPlatformVersions?: object[];
    }): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'extension',
                req.namespace,
                req.extension,
                'delete'
            ]),
            headers,
            payload: req.targetPlatformVersions
        });
    }

    async purgeExtensions(req: {
        namespace: string;
        extension: string;
        targetPlatformVersions?: object[];
    }): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'extension',
                req.namespace,
                req.extension,
                'purge'
            ]),
            headers,
            payload: req.targetPlatformVersions
        });
    }

    async getNamespace(signal: AbortSignal, name: string): Promise<Readonly<Namespace>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'namespace', name])
        });
    }

    async createNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'create-namespace']),
            method: 'POST',
            payload: namespace,
            headers
        });
    }

    async deleteNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'namespace', namespace.name]),
            method: 'DELETE',
            headers
        });
    }
    async changeNamespace(req: {
        oldNamespace: string;
        newNamespace: string;
        removeOldNamespace: boolean;
        mergeIfNewNamespaceAlreadyExists: boolean;
    }): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'change-namespace']),
            method: 'POST',
            payload: req,
            headers
        });
    }

    async getPublisherInfo(signal: AbortSignal, provider: string, login: string): Promise<Readonly<PublisherInfo>> {
        return sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login]),
            credentials: true
        });
    }

    async getUsers(
        signal: AbortSignal,
        params?: { search?: string; role?: string; page?: number; size?: number }
    ): Promise<Readonly<UserSearchResult>> {
        const query: { key: string; value: string | number }[] = [];
        if (params) {
            if (params.search) {
                query.push({ key: 'query', value: params.search });
            }
            if (params.role) {
                query.push({ key: 'role', value: params.role });
            }
            if (params.page !== undefined) {
                query.push({ key: 'page', value: params.page });
            }
            if (params.size !== undefined) {
                query.push({ key: 'size', value: params.size });
            }
        }
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'user', 'search'], query)
        });
    }

    async updateUserAccess(provider: string, login: string, access: UserAccess): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'PUT',
            payload: access,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'user', provider, login, 'access']),
            headers
        });
    }

    async revokePublisherContributions(provider: string, login: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login, 'revoke']),
            headers
        });
    }

    async reinstatePublisherAgreement(provider: string, login: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'publisher',
                provider,
                login,
                'reinstate-agreement'
            ]),
            headers
        });
    }

    async revokeAccessTokens(provider: string, login: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'publisher',
                provider,
                login,
                'tokens',
                'revoke'
            ]),
            headers
        });
    }

    async forgetUser(provider: string, login: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login, 'delete']),
            headers
        });
    }

    async getAllScans(
        signal: AbortSignal,
        params?: {
            size?: number;
            offset?: number;
            status?: string | string[];
            publisher?: string;
            namespace?: string;
            name?: string;
            validationType?: string[];
            threatScannerName?: string[];
            dateStartedFrom?: string;
            dateStartedTo?: string;
            enforcement?: 'enforced' | 'notEnforced' | 'all';
            adminDecision?: string[];
            exactStatus?: boolean;
        }
    ): Promise<Readonly<ScanResultsResponse>> {
        const query: { key: string; value: string | number }[] = [];
        if (params) {
            if (params.size !== undefined) query.push({ key: 'size', value: params.size });
            if (params.offset !== undefined) query.push({ key: 'offset', value: params.offset });
            if (params.status) {
                const statusValue = Array.isArray(params.status) ? params.status.join(',') : params.status;
                query.push({ key: 'status', value: statusValue });
            }
            if (params.publisher) query.push({ key: 'publisher', value: params.publisher });
            if (params.namespace) query.push({ key: 'namespace', value: params.namespace });
            if (params.name) query.push({ key: 'name', value: params.name });
            if (params.validationType && params.validationType.length > 0)
                query.push({ key: 'validationType', value: params.validationType.join(',') });
            if (params.threatScannerName && params.threatScannerName.length > 0)
                query.push({ key: 'threatScannerName', value: params.threatScannerName.join(',') });
            if (params.dateStartedFrom) query.push({ key: 'dateStartedFrom', value: params.dateStartedFrom });
            if (params.dateStartedTo) query.push({ key: 'dateStartedTo', value: params.dateStartedTo });
            if (params.enforcement) query.push({ key: 'enforcement', value: params.enforcement });
            if (params.adminDecision && params.adminDecision.length > 0)
                query.push({ key: 'adminDecision', value: params.adminDecision.join(',') });
            if (params.exactStatus) query.push({ key: 'exactStatus', value: 'true' });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans'], query);
        return sendRequest({
            signal,
            credentials: true,
            endpoint
        });
    }

    async getScan(signal: AbortSignal, scanId: string): Promise<Readonly<ScanResultJson>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', scanId])
        });
    }

    async retryFailedScannerJobs(scanId: string): Promise<Readonly<ScanResultJson>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', scanId, 'jobs', 'retry']),
            headers
        });
    }

    async getScanCounts(
        signal: AbortSignal,
        params?: {
            dateStartedFrom?: string;
            dateStartedTo?: string;
            enforcement?: 'enforced' | 'notEnforced' | 'all';
            threatScannerName?: string[];
            validationType?: string[];
        }
    ): Promise<Readonly<ScanCounts>> {
        const query: { key: string; value: string | number }[] = [];
        if (params) {
            if (params.dateStartedFrom) query.push({ key: 'dateStartedFrom', value: params.dateStartedFrom });
            if (params.dateStartedTo) query.push({ key: 'dateStartedTo', value: params.dateStartedTo });
            if (params.enforcement) query.push({ key: 'enforcement', value: params.enforcement });
            if (params.threatScannerName && params.threatScannerName.length > 0) {
                query.push({ key: 'threatScannerName', value: params.threatScannerName.join(',') });
            }
            if (params.validationType && params.validationType.length > 0) {
                query.push({ key: 'validationType', value: params.validationType.join(',') });
            }
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'counts'], query);
        return sendRequest({
            signal,
            credentials: true,
            endpoint
        });
    }

    async getScanFilterOptions(signal: AbortSignal): Promise<Readonly<ScanFilterOptions>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'filterOptions'])
        });
    }

    async getFiles(
        signal: AbortSignal,
        params?: {
            size?: number;
            offset?: number;
            decision?: string;
            publisher?: string;
            namespace?: string;
            name?: string;
            dateDecidedFrom?: string;
            dateDecidedTo?: string;
            sortBy?: string;
            sortOrder?: 'asc' | 'desc';
        }
    ): Promise<Readonly<FilesResponse>> {
        const query: { key: string; value: string | number }[] = [];
        if (params) {
            if (params.size !== undefined) query.push({ key: 'size', value: params.size });
            if (params.offset !== undefined) query.push({ key: 'offset', value: params.offset });
            if (params.decision) query.push({ key: 'decision', value: params.decision });
            if (params.publisher) query.push({ key: 'publisher', value: params.publisher });
            if (params.namespace) query.push({ key: 'namespace', value: params.namespace });
            if (params.name) query.push({ key: 'name', value: params.name });
            if (params.dateDecidedFrom) query.push({ key: 'dateDecidedFrom', value: params.dateDecidedFrom });
            if (params.dateDecidedTo) query.push({ key: 'dateDecidedTo', value: params.dateDecidedTo });
            if (params.sortBy) query.push({ key: 'sortBy', value: params.sortBy });
            if (params.sortOrder) query.push({ key: 'sortOrder', value: params.sortOrder });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files'], query);
        return sendRequest({
            signal,
            credentials: true,
            endpoint
        });
    }

    async getFileCounts(
        signal: AbortSignal,
        params?: { dateDecidedFrom?: string; dateDecidedTo?: string }
    ): Promise<Readonly<FileDecisionCountsJson>> {
        const query: { key: string; value: string | number }[] = [];
        if (params) {
            if (params.dateDecidedFrom) query.push({ key: 'dateDecidedFrom', value: params.dateDecidedFrom });
            if (params.dateDecidedTo) query.push({ key: 'dateDecidedTo', value: params.dateDecidedTo });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'counts'], query);
        return sendRequest({
            signal,
            credentials: true,
            endpoint
        });
    }

    async makeScanDecision(request: ScanDecisionRequest): Promise<Readonly<ScanDecisionResponse>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'decisions']),
            headers,
            payload: request
        });
    }

    async makeFileDecision(request: FileDecisionRequest): Promise<Readonly<FileDecisionResponse>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'decisions']),
            headers,
            payload: request
        });
    }

    async deleteFileDecisions(request: FileDecisionDeleteRequest): Promise<Readonly<FileDecisionDeleteResponse>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'decisions']),
            headers,
            payload: request
        });
    }

    async getTiers(signal: AbortSignal): Promise<Readonly<TierList>> {
        return sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers']),
            credentials: true
        });
    }

    async createTier(tier: Tier): Promise<Readonly<Tier>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            payload: tier,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', 'create']),
            headers
        });
    }

    async updateTier(name: string, tier: Tier): Promise<Readonly<Tier>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'PUT',
            payload: tier,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', name]),
            headers
        });
    }

    async deleteTier(name: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', name]),
            headers
        });
    }

    async getSizeOverrides(signal: AbortSignal): Promise<Readonly<SizeOverrideList>> {
        return sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides']),
            credentials: true
        });
    }

    async createSizeOverride(override: SizeOverride): Promise<Readonly<SizeOverride>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            payload: override,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides', 'create']),
            headers
        });
    }

    async updateSizeOverride(id: number, override: SizeOverride): Promise<Readonly<SizeOverride>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'PUT',
            payload: override,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides', String(id)]),
            headers
        });
    }

    async deleteSizeOverride(id: number): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides', String(id)]),
            headers
        });
    }

    async getCustomers(signal: AbortSignal): Promise<Readonly<CustomerList>> {
        return await sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers']),
            credentials: true
        });
    }

    async getCustomer(signal: AbortSignal, name: string): Promise<Readonly<Customer>> {
        return await sendRequest({
            signal,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            credentials: true
        });
    }

    async createCustomer(customer: Customer): Promise<Readonly<Customer>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'POST',
            payload: customer,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', 'create']),
            headers
        });
    }

    async updateCustomer(name: string, customer: Customer): Promise<Readonly<Customer>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'PUT',
            payload: customer,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            headers
        });
    }

    async deleteCustomer(name: string): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            headers
        });
    }

    async getCustomerMembers(signal: AbortSignal, name: string): Promise<Readonly<CustomerMembershipList>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name, 'members'])
        });
    }

    async addCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        const query = [
            { key: 'user', value: user.loginName },
            { key: 'provider', value: user.provider }
        ];
        return sendRequest({
            headers,
            method: 'POST',
            credentials: true,
            endpoint: addQuery(
                createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name, 'add-member']),
                query
            )
        });
    }

    async removeCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;
        const query = [
            { key: 'user', value: user.loginName },
            { key: 'provider', value: user.provider }
        ];
        return sendRequest({
            headers,
            method: 'POST',
            credentials: true,
            endpoint: addQuery(
                createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name, 'remove-member']),
                query
            )
        });
    }

    /**
     * Get usage stats for a customer within an optional date range.
     */
    async getUsageStats(signal: AbortSignal, customerName: string, date: Date): Promise<Readonly<UsageStatsList>> {
        const query: { key: string; value: string | number }[] = [];
        query.push({ key: 'date', value: date.toISOString() });

        return sendRequest({
            signal,
            endpoint: createAbsoluteURL(
                [this.registry.serverUrl, 'admin', 'ratelimit', 'customers', customerName, 'usage'],
                query
            ),
            credentials: true
        });
    }

    async getLogs(
        signal: AbortSignal,
        page: number = 0,
        size: number = 20,
        period?: string
    ): Promise<Readonly<LogPageableList>> {
        const query: { key: string; value: string | number }[] = [
            { key: 'page', value: page },
            { key: 'size', value: size }
        ];
        if (period) {
            query.push({ key: 'period', value: period });
        }
        const endpoint = addQuery(createAbsoluteURL([this.registry.serverUrl, 'admin', 'logs']), query);
        return sendRequest({
            signal,
            endpoint,
            credentials: true
        });
    }

    async getCustomerRateLimitTokens(signal: AbortSignal, customerName: string): Promise<Readonly<RateLimitToken[]>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'ratelimit',
                'customers',
                customerName,
                'tokens'
            ])
        });
    }

    async createCustomerRateLimitToken(customerName: string, description?: string): Promise<Readonly<RateLimitToken>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        headers[csrfToken.header] = csrfToken.value;

        const url = createAbsoluteURL([
            this.registry.serverUrl,
            'admin',
            'ratelimit',
            'customers',
            customerName,
            'tokens'
        ]);
        const endpoint = description !== undefined ? addQuery(url, [{ key: 'description', value: description }]) : url;
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint,
            headers
        });
    }

    async deleteCustomerRateLimitToken(customerName: string, tokenId: number): Promise<Readonly<SuccessResult>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;
        return sendRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'ratelimit',
                'customers',
                customerName,
                'tokens',
                `${tokenId}`
            ]),
            headers
        });
    }

    async getSettings(signal: AbortSignal): Promise<Readonly<Settings>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'settings'])
        });
    }

    async updateSettings(settings: Partial<Settings>): Promise<Readonly<Settings>> {
        const csrfToken = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        headers[csrfToken.header] = csrfToken.value;

        return sendRequest({
            method: 'PUT',
            payload: settings,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'settings']),
            headers
        });
    }

    async getAdminStatistics(signal: AbortSignal, year: number, month: number): Promise<Readonly<AdminStatistics>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL(
                [this.registry.serverUrl, 'admin', 'statistics'],
                [
                    { key: 'year', value: year },
                    { key: 'month', value: month }
                ]
            )
        });
    }

    /**
     * The CSV export is a URL rather than a fetch: the download is a plain link, so the browser
     * saves the file under the name the server's Content-Disposition gives it instead of the page
     * having to build a blob.
     */
    getAdminStatisticsCsvUrl(year: number, month: number): string {
        return createAbsoluteURL(
            [this.registry.serverUrl, 'admin', 'statistics', 'csv'],
            [
                { key: 'year', value: year },
                { key: 'month', value: month }
            ]
        );
    }

    async getSearchIndex(signal: AbortSignal): Promise<Readonly<SearchIndex>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'search-index'])
        });
    }

    async explainSearch(
        signal: AbortSignal,
        query: string,
        size: number,
        offset: number
    ): Promise<Readonly<SearchExplain>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL(
                [this.registry.serverUrl, 'admin', 'search-explain'],
                [
                    { key: 'query', value: query },
                    { key: 'size', value: size },
                    { key: 'offset', value: offset }
                ]
            )
        });
    }

    async updateSearchIndex(): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'update-search-index']),
            headers
        });
    }

    async getCaches(signal: AbortSignal): Promise<Readonly<CacheList>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'caches'])
        });
    }

    async clearCaches(cache?: { manager: string; name: string }): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        // Manager and name travel together or not at all: a cache is only identified by both, and
        // the server clears everything when neither is given.
        const query = cache
            ? [
                  { key: 'manager', value: cache.manager },
                  { key: 'cache', value: cache.name }
              ]
            : [];
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'caches', 'clear'], query),
            headers
        });
    }

    async getAnalyticsStatus(signal: AbortSignal): Promise<Readonly<AnalyticsStatus>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'analytics', 'status'])
        });
    }

    async getConsistencyChecks(signal: AbortSignal): Promise<Readonly<ConsistencyCheckList>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'consistency'])
        });
    }

    async getConsistencyFindings(signal: AbortSignal, checkId: string): Promise<Readonly<ConsistencyFindingList>> {
        return sendRequest({
            signal,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'consistency', checkId, 'findings'])
        });
    }

    async fixConsistencyFindings(checkId: string): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'consistency', checkId, 'fix']),
            headers
        });
    }

    async fixConsistencyFinding(checkId: string, entityId: number): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        return sendRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([
                this.registry.serverUrl,
                'admin',
                'consistency',
                checkId,
                'fix',
                String(entityId)
            ]),
            headers
        });
    }

    private async csrfHeaders(): Promise<Record<string, string>> {
        const csrfToken = await this.registry.getCsrfToken();
        return { [csrfToken.header]: csrfToken.value };
    }
}

export interface ExtensionFilter {
    query: string;
    category: string;
    size: number;
    offset: number;
    sortBy: SortBy;
    sortOrder: SortOrder;
}
