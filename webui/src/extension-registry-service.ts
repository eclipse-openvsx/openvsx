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
    ErrorResult,
    CsrfTokenJson,
    isError,
    Namespace,
    NamespaceDetails,
    MembershipRole,
    SortBy,
    SortOrder,
    UrlString,
    NamespaceMembershipList,
    PublisherInfo,
    RegistryVersion,
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
    TierList,
    Customer,
    CustomerList,
    UsageStatsList,
    LogPageableList,
    CustomerMembershipList,
    RateLimitToken,
    Settings,
    UserSearchResult,
    TrustedPublisher,
    TrustedPublisherList,
    TrustedPublisherRequest,
    TrustedPublisherStatus,
    ConsistencyCheckList,
    ConsistencyFindingList,
    SearchIndex,
    AdminStatistics
} from './extension-registry-types';
import { createAbsoluteURL, addQuery } from './utils';
import { sendRequest, ErrorResponse, sendNonRetriableRequest, sendStrictRequest } from './server-request';

export class ExtensionRegistryService {
    readonly admin: AdminService;

    constructor(
        readonly serverUrl: string = '',
        AdminConstructor: AdminServiceConstructor = AdminServiceImpl
    ) {
        this.admin = new AdminConstructor(this);
    }

    async getLoginProviders(abortController: AbortController): Promise<Readonly<LoginProviders | SuccessResult>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'login-providers']);
        return sendRequest({ abortController, endpoint });
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

    async getNamespaceDetails(abortController: AbortController, name: string): Promise<Readonly<NamespaceDetails>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', name, 'details']);
        return sendStrictRequest({ abortController, endpoint });
    }

    async setNamespaceDetails(
        abortController: AbortController,
        endpoint: string,
        details: NamespaceDetails
    ): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendStrictRequest({
            abortController,
            method: 'POST',
            payload: details,
            credentials: true,
            endpoint,
            headers
        });
    }

    async setNamespaceLogo(
        abortController: AbortController,
        endpoint: string,
        logoFile: Blob,
        logoName: string
    ): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        const form = new FormData();
        form.append('file', logoFile, logoName);
        endpoint = createAbsoluteURL([endpoint, 'logo']);
        return sendStrictRequest({
            abortController,
            method: 'POST',
            payload: form,
            credentials: true,
            endpoint,
            headers
        });
    }

    async search(abortController: AbortController, filter?: ExtensionFilter): Promise<Readonly<SearchResult>> {
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
        // Retries are owned by the TanStack query that calls this.
        return sendStrictRequest({ abortController, endpoint });
    }

    async getExtensionDetail(
        abortController: AbortController,
        extensionUrl: UrlString
    ): Promise<Readonly<Extension | ErrorResult>> {
        return sendRequest({ abortController, endpoint: extensionUrl });
    }

    async getExtensionReadme(abortController: AbortController, extension: Extension): Promise<string> {
        return sendRequest({
            abortController,
            endpoint: extension.files.readme,
            headers: { Accept: 'text/plain' },
            followRedirect: true
        });
    }

    async getExtensionChangelog(abortController: AbortController, extension: Extension): Promise<string> {
        return sendRequest({
            abortController,
            endpoint: extension.files.changelog,
            headers: { Accept: 'text/plain' },
            followRedirect: true
        });
    }

    async getExtensionIcon(
        abortController: AbortController,
        extension: Extension | SearchEntry
    ): Promise<string | undefined> {
        if (!extension.files?.icon) {
            return Promise.resolve(undefined);
        }

        return sendRequest({
            abortController,
            endpoint: extension.files.icon,
            headers: { Accept: 'application/octet-stream' },
            followRedirect: true
        }).then(value => {
            const blob = value as Blob;
            return URL.createObjectURL(blob);
        });
    }

    async getExtensionReviews(
        abortController: AbortController,
        extension: Extension
    ): Promise<Readonly<ExtensionReviewList>> {
        return sendRequest({ abortController, endpoint: extension.reviewsUrl });
    }

    async postReview(
        abortController: AbortController,
        review: NewReview,
        postReviewUrl: UrlString
    ): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'POST',
            payload: review,
            credentials: true,
            endpoint: postReviewUrl,
            headers
        });
    }

    async deleteReview(
        abortController: AbortController,
        deleteReviewUrl: string
    ): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint: deleteReviewUrl,
            headers
        });
    }

    async deleteUserReview(
        abortController: AbortController,
        extension: Extension,
        user: UserData
    ): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
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

    async getUser(abortController: AbortController): Promise<Readonly<UserData | ErrorResult>> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.serverUrl, 'user']),
            credentials: true
        });
    }

    async getUserAuthError(abortController: AbortController): Promise<Readonly<ErrorResponse>> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'auth-error']),
            credentials: true
        });
    }

    async getUserByName(abortController: AbortController, name: string): Promise<Readonly<UserData>[]> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'search', name]),
            credentials: true
        });
    }

    async getAccessTokens(abortController: AbortController, user: UserData): Promise<Readonly<PersonalAccessToken>[]> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: user.tokensUrl
        });
    }

    async createAccessToken(
        abortController: AbortController,
        user: UserData,
        description: string
    ): Promise<Readonly<PersonalAccessToken>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        const endpoint = addQuery(user.createTokenUrl, [{ key: 'description', value: description }]);
        return sendRequest({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint,
            headers
        });
    }

    async deleteAccessToken(
        abortController: AbortController,
        token: PersonalAccessToken
    ): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint: token.deleteTokenUrl,
            headers
        });
    }

    async deleteAllAccessTokens(
        abortController: AbortController,
        tokens: PersonalAccessToken[]
    ): Promise<Readonly<SuccessResult | ErrorResult>[]> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return await Promise.all(
            tokens.map(token =>
                sendRequest<SuccessResult | ErrorResult>({
                    abortController,
                    method: 'POST',
                    credentials: true,
                    endpoint: token.deleteTokenUrl,
                    headers
                })
            )
        );
    }

    async getCsrfToken(abortController?: AbortController): Promise<Readonly<CsrfTokenJson | ErrorResult>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'csrf'])
        });
    }

    async getNamespaces(abortController: AbortController): Promise<Readonly<Namespace>[]> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'namespaces'])
        });
    }

    async getCustomers(abortController: AbortController): Promise<Readonly<Customer>[]> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'customers'])
        });
    }

    async getUsageStats(
        abortController: AbortController,
        customerName: string,
        date: Date
    ): Promise<Readonly<UsageStatsList>> {
        const query: { key: string; value: string | number }[] = [];
        query.push({ key: 'date', value: date.toISOString() });

        return sendRequest(
            {
                abortController,
                endpoint: createAbsoluteURL([this.serverUrl, 'user', 'customers', customerName, 'usage'], query),
                credentials: true
            },
            false
        );
    }

    async getNamespaceMembers(
        abortController: AbortController,
        namespace: Namespace
    ): Promise<Readonly<NamespaceMembershipList>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: namespace.membersUrl
        });
    }

    async setNamespaceMember(
        abortController: AbortController,
        endpoint: UrlString,
        user: UserData,
        role: MembershipRole | 'remove'
    ): Promise<Readonly<SuccessResult | ErrorResult>[]> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        const query = [
            { key: 'user', value: user.loginName },
            { key: 'provider', value: user.provider },
            { key: 'role', value: role }
        ];
        return sendRequest({
            abortController,
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
    async getTrustedPublishingStatus(abortController: AbortController): Promise<Readonly<TrustedPublisherStatus>> {
        return sendStrictRequest<TrustedPublisherStatus>({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', '-', 'trusted-publishing', 'status'])
        });
    }

    async getTrustedPublishers(
        abortController: AbortController,
        trustedPublishingUrl: UrlString
    ): Promise<Readonly<TrustedPublisherList>> {
        const result = await sendStrictRequest<TrustedPublisherList>({
            abortController,
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
        const csrfResponse = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            payload: request,
            headers,
            endpoint: createAbsoluteURL([trustedPublishingUrl, 'create'])
        });
    }

    async deleteTrustedPublisher(trustedPublishingUrl: UrlString, id: number): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            headers,
            endpoint: createAbsoluteURL([trustedPublishingUrl, 'delete', `${id}`])
        });
    }

    async signPublisherAgreement(abortController: AbortController): Promise<Readonly<UserData | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest<UserData | ErrorResult>({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'publisher-agreement']),
            headers
        });
    }

    async getStaticContent(abortController: AbortController, url: string): Promise<string> {
        return sendRequest({
            abortController,
            endpoint: url,
            headers: { Accept: 'text/plain' },
            followRedirect: true
        });
    }

    async publishExtension(extensionPackage: File): Promise<Readonly<Extension>> {
        const csrfResponse = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/octet-stream'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        // Never retried: an upload the registry turned down has an explicit reason to show.
        return sendStrictRequest<Extension>({
            method: 'POST',
            credentials: true,
            payload: extensionPackage,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', 'user', 'publish'])
        });
    }

    async createNamespace(name: string): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendStrictRequest<SuccessResult>({
            method: 'POST',
            credentials: true,
            payload: { name: name },
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', 'user', 'namespace', 'create'])
        });
    }

    async getExtensions(abortController: AbortController): Promise<Readonly<Extension[]>> {
        const headers: Record<string, string> = {};

        return sendStrictRequest<Extension[]>({
            abortController,
            method: 'GET',
            credentials: true,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extensions'])
        });
    }

    async getExtension(
        abortController: AbortController,
        namespace: string,
        extension: string
    ): Promise<Readonly<Extension>> {
        const headers: Record<string, string> = {};

        return sendStrictRequest<Extension>({
            abortController,
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
        const csrfResponse = await this.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extension', req.namespace, req.extension, 'delete']),
            headers,
            payload: req.targetPlatformVersions
        });
    }

    async getRegistryVersion(abortController: AbortController): Promise<Readonly<RegistryVersion>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', 'version']);
        return sendRequest({ abortController, endpoint });
    }
}

export interface AdminService {
    getExtension(abortController: AbortController, namespace: string, extension: string): Promise<Readonly<Extension>>;
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
    getNamespace(abortController: AbortController, name: string): Promise<Readonly<Namespace>>;
    createNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>>;
    deleteNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>>;
    changeNamespace(req: {
        oldNamespace: string;
        newNamespace: string;
        removeOldNamespace: boolean;
        mergeIfNewNamespaceAlreadyExists: boolean;
    }): Promise<Readonly<SuccessResult>>;
    getPublisherInfo(
        abortController: AbortController,
        provider: string,
        login: string
    ): Promise<Readonly<PublisherInfo>>;
    getUsers(
        abortController: AbortController,
        params?: { search?: string; role?: string; page?: number; size?: number }
    ): Promise<Readonly<UserSearchResult>>;
    updateUserRole(
        provider: string,
        login: string,
        role: 'admin' | 'privileged' | 'none'
    ): Promise<Readonly<SuccessResult>>;
    revokePublisherContributions(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    revokeAccessTokens(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    forgetUser(provider: string, login: string): Promise<Readonly<SuccessResult>>;
    getAllScans(
        abortController: AbortController,
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
        }
    ): Promise<Readonly<ScanResultsResponse>>;
    getScan(abortController: AbortController, scanId: string): Promise<Readonly<ScanResultJson>>;
    retryFailedScannerJobs(scanId: string): Promise<Readonly<ScanResultJson>>;
    getScanCounts(
        abortController: AbortController,
        params?: {
            dateStartedFrom?: string;
            dateStartedTo?: string;
            enforcement?: 'enforced' | 'notEnforced' | 'all';
            threatScannerName?: string[];
            validationType?: string[];
        }
    ): Promise<Readonly<ScanCounts>>;
    getScanFilterOptions(abortController: AbortController): Promise<Readonly<ScanFilterOptions>>;
    // Files API
    getFiles(
        abortController: AbortController,
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
        abortController: AbortController,
        params?: { dateDecidedFrom?: string; dateDecidedTo?: string }
    ): Promise<Readonly<FileDecisionCountsJson>>;
    // Decision APIs
    makeScanDecision(request: ScanDecisionRequest): Promise<Readonly<ScanDecisionResponse>>;
    makeFileDecision(request: FileDecisionRequest): Promise<Readonly<FileDecisionResponse>>;
    deleteFileDecisions(request: FileDecisionDeleteRequest): Promise<Readonly<FileDecisionDeleteResponse>>;
    getTiers(abortController: AbortController): Promise<Readonly<TierList>>;
    createTier(tier: Tier): Promise<Readonly<Tier>>;
    updateTier(name: string, tier: Tier): Promise<Readonly<Tier>>;
    deleteTier(name: string): Promise<Readonly<SuccessResult>>;
    getCustomers(abortController: AbortController): Promise<Readonly<CustomerList>>;
    getCustomer(abortController: AbortController, name: string): Promise<Readonly<Customer>>;
    createCustomer(customer: Customer): Promise<Readonly<Customer>>;
    updateCustomer(name: string, customer: Customer): Promise<Readonly<Customer>>;
    deleteCustomer(name: string): Promise<Readonly<SuccessResult>>;
    getCustomerMembers(abortController: AbortController, name: string): Promise<Readonly<CustomerMembershipList>>;
    addCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>>;
    removeCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>>;
    getUsageStats(
        abortController: AbortController,
        customerName: string,
        date: Date
    ): Promise<Readonly<UsageStatsList>>;
    getLogs(
        abortController: AbortController,
        page?: number,
        size?: number,
        period?: string
    ): Promise<Readonly<LogPageableList>>;
    getCustomerRateLimitTokens(
        abortController: AbortController,
        customerName: string
    ): Promise<Readonly<RateLimitToken[]>>;
    createCustomerRateLimitToken(customerName: string, description: string): Promise<Readonly<RateLimitToken>>;
    deleteCustomerRateLimitToken(customerName: string, tokenId: number): Promise<Readonly<SuccessResult>>;
    getSettings(abortController: AbortController): Promise<Readonly<Settings>>;
    updateSettings(settings: Settings): Promise<Readonly<Settings>>;
    getSearchIndex(abortController: AbortController): Promise<Readonly<SearchIndex>>;
    getAdminStatistics(
        abortController: AbortController,
        year: number,
        month: number
    ): Promise<Readonly<AdminStatistics>>;
    getAdminStatisticsCsvUrl(year: number, month: number): string;
    updateSearchIndex(): Promise<Readonly<SuccessResult>>;
    getConsistencyChecks(abortController: AbortController): Promise<Readonly<ConsistencyCheckList>>;
    getConsistencyFindings(
        abortController: AbortController,
        checkId: string
    ): Promise<Readonly<ConsistencyFindingList>>;
    fixConsistencyFindings(checkId: string): Promise<Readonly<SuccessResult>>;
    fixConsistencyFinding(checkId: string, entityId: number): Promise<Readonly<SuccessResult>>;
}

export interface AdminServiceConstructor {
    new (registry: ExtensionRegistryService): AdminService;
}

export class AdminServiceImpl implements AdminService {
    constructor(readonly registry: ExtensionRegistryService) {}

    async getExtension(
        abortController: AbortController,
        namespace: string,
        extension: string
    ): Promise<Readonly<Extension>> {
        return sendStrictRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'extension', namespace, extension])
        });
    }

    async deleteExtensions(req: {
        namespace: string;
        extension: string;
        targetPlatformVersions?: object[];
    }): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendStrictRequest({
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
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendStrictRequest({
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

    async getNamespace(abortController: AbortController, name: string): Promise<Readonly<Namespace>> {
        return sendStrictRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'namespace', name])
        });
    }

    async createNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'create-namespace']),
            method: 'POST',
            payload: namespace,
            headers
        });
    }

    async deleteNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
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
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'change-namespace']),
            method: 'POST',
            payload: req,
            headers
        });
    }

    async getPublisherInfo(
        abortController: AbortController,
        provider: string,
        login: string
    ): Promise<Readonly<PublisherInfo>> {
        return sendNonRetriableRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login]),
            credentials: true
        });
    }

    async getUsers(
        abortController: AbortController,
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
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'user', 'search'], query)
        });
    }

    async updateUserRole(
        provider: string,
        login: string,
        role: 'admin' | 'privileged' | 'none'
    ): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        const query = [{ key: 'role', value: role }];
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'user', provider, login, 'role'], query),
            headers
        });
    }

    async revokePublisherContributions(provider: string, login: string): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login, 'revoke']),
            headers
        });
    }

    async revokeAccessTokens(provider: string, login: string): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
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
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login, 'delete']),
            headers
        });
    }

    async getAllScans(
        abortController: AbortController,
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
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans'], query);
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    async getScan(abortController: AbortController, scanId: string): Promise<Readonly<ScanResultJson>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', scanId])
        });
    }

    async retryFailedScannerJobs(scanId: string): Promise<Readonly<ScanResultJson>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendNonRetriableRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', scanId, 'jobs', 'retry']),
            headers
        });
    }

    async getScanCounts(
        abortController: AbortController,
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
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    async getScanFilterOptions(abortController: AbortController): Promise<Readonly<ScanFilterOptions>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'filterOptions'])
        });
    }

    async getFiles(
        abortController: AbortController,
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
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    async getFileCounts(
        abortController: AbortController,
        params?: { dateDecidedFrom?: string; dateDecidedTo?: string }
    ): Promise<Readonly<FileDecisionCountsJson>> {
        const query: { key: string; value: string | number }[] = [];
        if (params) {
            if (params.dateDecidedFrom) query.push({ key: 'dateDecidedFrom', value: params.dateDecidedFrom });
            if (params.dateDecidedTo) query.push({ key: 'dateDecidedTo', value: params.dateDecidedTo });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'counts'], query);
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    async makeScanDecision(request: ScanDecisionRequest): Promise<Readonly<ScanDecisionResponse>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendNonRetriableRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'decisions']),
            headers,
            payload: request
        });
    }

    async makeFileDecision(request: FileDecisionRequest): Promise<Readonly<FileDecisionResponse>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendNonRetriableRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'decisions']),
            headers,
            payload: request
        });
    }

    async deleteFileDecisions(request: FileDecisionDeleteRequest): Promise<Readonly<FileDecisionDeleteResponse>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendNonRetriableRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'decisions']),
            headers,
            payload: request
        });
    }

    async getTiers(abortController: AbortController): Promise<Readonly<TierList>> {
        return sendNonRetriableRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers']),
            credentials: true
        });
    }

    async createTier(tier: Tier): Promise<Readonly<Tier>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendNonRetriableRequest({
            method: 'POST',
            payload: tier,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', 'create']),
            headers
        });
    }

    async updateTier(name: string, tier: Tier): Promise<Readonly<Tier>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendNonRetriableRequest({
            method: 'PUT',
            payload: tier,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', name]),
            headers
        });
    }

    async deleteTier(name: string): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', name]),
            headers
        });
    }

    async getCustomers(abortController: AbortController): Promise<Readonly<CustomerList>> {
        return await sendNonRetriableRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers']),
            credentials: true
        });
    }

    async getCustomer(abortController: AbortController, name: string): Promise<Readonly<Customer>> {
        return await sendNonRetriableRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            credentials: true
        });
    }

    async createCustomer(customer: Customer): Promise<Readonly<Customer>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendNonRetriableRequest({
            method: 'POST',
            payload: customer,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', 'create']),
            headers
        });
    }

    async updateCustomer(name: string, customer: Customer): Promise<Readonly<Customer>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendNonRetriableRequest({
            method: 'PUT',
            payload: customer,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            headers
        });
    }

    async deleteCustomer(name: string): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            headers
        });
    }

    async getCustomerMembers(
        abortController: AbortController,
        name: string
    ): Promise<Readonly<CustomerMembershipList>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name, 'members'])
        });
    }

    async addCustomerMember(name: string, user: UserData): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        const query = [
            { key: 'user', value: user.loginName },
            { key: 'provider', value: user.provider }
        ];
        return sendStrictRequest({
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
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        const query = [
            { key: 'user', value: user.loginName },
            { key: 'provider', value: user.provider }
        ];
        return sendStrictRequest({
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
    async getUsageStats(
        abortController: AbortController,
        customerName: string,
        date: Date
    ): Promise<Readonly<UsageStatsList>> {
        const query: { key: string; value: string | number }[] = [];
        query.push({ key: 'date', value: date.toISOString() });

        return sendNonRetriableRequest({
            abortController,
            endpoint: createAbsoluteURL(
                [this.registry.serverUrl, 'admin', 'ratelimit', 'customers', customerName, 'usage'],
                query
            ),
            credentials: true
        });
    }

    async getLogs(
        abortController: AbortController,
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
        return sendNonRetriableRequest({
            abortController,
            endpoint,
            credentials: true
        });
    }

    async getCustomerRateLimitTokens(
        abortController: AbortController,
        customerName: string
    ): Promise<Readonly<RateLimitToken[]>> {
        return sendNonRetriableRequest({
            abortController,
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
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        const url = createAbsoluteURL([
            this.registry.serverUrl,
            'admin',
            'ratelimit',
            'customers',
            customerName,
            'tokens'
        ]);
        const endpoint = description !== undefined ? addQuery(url, [{ key: 'description', value: description }]) : url;
        return sendNonRetriableRequest({
            method: 'POST',
            credentials: true,
            endpoint,
            headers
        });
    }

    async deleteCustomerRateLimitToken(customerName: string, tokenId: number): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
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

    async getSettings(abortController: AbortController): Promise<Readonly<Settings>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'settings'])
        });
    }

    async updateSettings(settings: Settings): Promise<Readonly<Settings>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendNonRetriableRequest({
            method: 'PUT',
            payload: settings,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'settings']),
            headers
        });
    }

    async getAdminStatistics(
        abortController: AbortController,
        year: number,
        month: number
    ): Promise<Readonly<AdminStatistics>> {
        return sendNonRetriableRequest({
            abortController,
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

    async getSearchIndex(abortController: AbortController): Promise<Readonly<SearchIndex>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'search-index'])
        });
    }

    async updateSearchIndex(): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'update-search-index']),
            headers
        });
    }

    async getConsistencyChecks(abortController: AbortController): Promise<Readonly<ConsistencyCheckList>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'consistency'])
        });
    }

    async getConsistencyFindings(
        abortController: AbortController,
        checkId: string
    ): Promise<Readonly<ConsistencyFindingList>> {
        return sendNonRetriableRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'consistency', checkId, 'findings'])
        });
    }

    async fixConsistencyFindings(checkId: string): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        return sendStrictRequest({
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'consistency', checkId, 'fix']),
            headers
        });
    }

    async fixConsistencyFinding(checkId: string, entityId: number): Promise<Readonly<SuccessResult>> {
        const headers = await this.csrfHeaders();
        return sendStrictRequest({
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
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return headers;
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
