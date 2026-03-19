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
    Extension, UserData, ExtensionCategory, ExtensionReviewList, PersonalAccessToken, SearchResult, NewReview,
    SuccessResult, ErrorResult, CsrfTokenJson, isError, Namespace, NamespaceDetails, MembershipRole, SortBy,
    SortOrder, UrlString, NamespaceMembershipList, PublisherInfo, SearchEntry, RegistryVersion,
    LoginProviders, ScanResultJson, ScanCounts, ScanResultsResponse, ScanFilterOptions,
    FilesResponse, FileDecisionCountsJson, ScanDecisionRequest, ScanDecisionResponse,
    FileDecisionRequest, FileDecisionResponse, FileDecisionDeleteRequest, FileDecisionDeleteResponse,
    Tier, TierList, Customer, CustomerList, UsageStatsList, LogPageableList,
} from './extension-registry-types';
import { createAbsoluteURL, addQuery } from './utils';
import { sendRequest, ErrorResponse } from './server-request';

export class ExtensionRegistryService {

    readonly admin: AdminService;

    constructor(readonly serverUrl: string = '', AdminConstructor: AdminServiceConstructor = AdminServiceImpl) {
        this.admin = new AdminConstructor(this);
    }

    getLoginProviders(abortController: AbortController): Promise<Readonly<LoginProviders|SuccessResult>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'login-providers']);
        return sendRequest({ abortController, endpoint });
    }

    getLogoutUrl(): string {
        return createAbsoluteURL([this.serverUrl, 'logout']);
    }

    getExtensionApiUrl(ext: { namespace: string, name: string, target?: string, version?: string }): string {
        const arr = [this.serverUrl, 'api', ext.namespace, ext.name];
        if (ext.target) {
            arr.push(ext.target);
        }
        if (ext.version) {
            arr.push(ext.version);
        }

        return createAbsoluteURL(arr);
    }

    getNamespaceDetails(abortController: AbortController, name: string): Promise<Readonly<NamespaceDetails>> {
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', name, 'details']);
        return sendRequest({ abortController, endpoint });
    }

    async setNamespaceDetails(abortController: AbortController, endpoint: string, details: NamespaceDetails): Promise<Readonly<SuccessResult | ErrorResult>> {
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
            payload: details,
            credentials: true,
            endpoint,
            headers
        });
    }

    async setNamespaceLogo(abortController: AbortController, endpoint: string, logoFile: Blob, logoName: string): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        const form = new FormData();
        form.append('file', logoFile, logoName);
        endpoint = createAbsoluteURL([endpoint, 'logo']);
        return sendRequest({
            abortController,
            method: 'POST',
            payload: form,
            credentials: true,
            endpoint,
            headers
        });
    }

    search(abortController: AbortController, filter?: ExtensionFilter): Promise<Readonly<SearchResult | ErrorResult>> {
        const query: { key: string, value: string | number }[] = [];
        if (filter) {
            if (filter.query)
                query.push({ key: 'query', value: filter.query });
            if (filter.category)
                query.push({ key: 'category', value: filter.category });
            if (filter.offset)
                query.push({ key: 'offset', value: filter.offset });
            if (filter.size)
                query.push({ key: 'size', value: filter.size });
            if (filter.sortBy)
                query.push({ key: 'sortBy', value: filter.sortBy });
            if (filter.sortOrder)
                query.push({ key: 'sortOrder', value: filter.sortOrder });
        }
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', '-', 'search'], query);
        return sendRequest({ abortController, endpoint });
    }

    getExtensionDetail(abortController: AbortController, extensionUrl: UrlString): Promise<Readonly<Extension | ErrorResult>> {
        return sendRequest({ abortController, endpoint: extensionUrl });
    }

    getExtensionReadme(abortController: AbortController, extension: Extension): Promise<string> {
        return sendRequest({
            abortController,
            endpoint: extension.files.readme,
            headers: { 'Accept': 'text/plain' },
            followRedirect: true
        });
    }

    getExtensionChangelog(abortController: AbortController, extension: Extension): Promise<string> {
        return sendRequest({
            abortController,
            endpoint: extension.files.changelog,
            headers: { 'Accept': 'text/plain' },
            followRedirect: true
        });
    }

    getExtensionIcon(abortController: AbortController, extension: Extension | SearchEntry): Promise<string | undefined> {
        if (!extension.files.icon) {
            return Promise.resolve(undefined);
        }

        return sendRequest({
            abortController,
            endpoint: extension.files.icon,
            headers: { 'Accept': 'application/octet-stream' },
            followRedirect: true
        }).then(value => {
            const blob = value as Blob;
            return URL.createObjectURL(blob);
        });
    }

    getCategories(): ExtensionCategory[] {
        return [
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
        ];
    }

    getExtensionReviews(abortController: AbortController, extension: Extension): Promise<Readonly<ExtensionReviewList>> {
        return sendRequest({ abortController, endpoint: extension.reviewsUrl });
    }

    async postReview(abortController: AbortController, review: NewReview, postReviewUrl: UrlString): Promise<Readonly<SuccessResult | ErrorResult>> {
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

    async deleteReview(abortController: AbortController, deleteReviewUrl: string): Promise<Readonly<SuccessResult | ErrorResult>> {
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

    async deleteUserReview(abortController: AbortController, extension: Extension, user: UserData): Promise<Readonly<SuccessResult | ErrorResult>> {
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
            endpoint: createAbsoluteURL([this.serverUrl, 'admin', 'extension', extension.namespace, extension.name, 'review', user.provider || 'github', user.loginName, 'delete']),
            headers
        });
    }

    getUser(abortController: AbortController): Promise<Readonly<UserData | ErrorResult>> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.serverUrl, 'user']),
            credentials: true
        });
    }

    getUserAuthError(abortController: AbortController): Promise<Readonly<ErrorResponse>> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'auth-error']),
            credentials: true
        });
    }

    getUserByName(abortController: AbortController, name: string): Promise<Readonly<UserData>[]> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'search', name]),
            credentials: true
        });
    }

    getAccessTokens(abortController: AbortController, user: UserData): Promise<Readonly<PersonalAccessToken>[]> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: user.tokensUrl
        });
    }

    async createAccessToken(abortController: AbortController, user: UserData, description: string): Promise<Readonly<PersonalAccessToken>> {
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

    async deleteAccessToken(abortController: AbortController, token: PersonalAccessToken): Promise<Readonly<SuccessResult | ErrorResult>> {
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

    async deleteAllAccessTokens(abortController: AbortController, tokens: PersonalAccessToken[]): Promise<Readonly<SuccessResult | ErrorResult>[]> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return await Promise.all(tokens.map(token => sendRequest<SuccessResult | ErrorResult>({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint: token.deleteTokenUrl,
            headers
        })));
    }

    getCsrfToken(abortController: AbortController): Promise<Readonly<CsrfTokenJson | ErrorResult>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'csrf'])
        });
    }

    getNamespaces(abortController: AbortController): Promise<Readonly<Namespace>[]> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'namespaces'])
        });
    }

    getNamespaceMembers(abortController: AbortController, namespace: Namespace): Promise<Readonly<NamespaceMembershipList>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: namespace.membersUrl
        });
    }

    async setNamespaceMember(abortController: AbortController, endpoint: UrlString, user: UserData, role: MembershipRole | 'remove'): Promise<Readonly<SuccessResult | ErrorResult>[]> {
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

    getStaticContent(abortController: AbortController, url: string): Promise<string> {
        return sendRequest({
            abortController,
            endpoint: url,
            headers: { 'Accept': 'text/plain' },
            followRedirect: true
        });
    }

    async publishExtension(abortController: AbortController, extensionPackage: File): Promise<Readonly<Extension | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/octet-stream'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendRequest<Extension | ErrorResult>({
            abortController,
            method: 'POST',
            credentials: true,
            payload: extensionPackage,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', 'user', 'publish'])
        }, false); // do not retry publishing an extension but show the explicit error received
    }

    async createNamespace(abortController: AbortController, name: string): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendRequest<SuccessResult | ErrorResult>({
            abortController,
            method: 'POST',
            credentials: true,
            payload: { name: name },
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'api', 'user', 'namespace', 'create'])
        });
    }

    async getExtensions(abortController: AbortController): Promise<Readonly<Extension[] | ErrorResult>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendRequest<Extension[] | ErrorResult>({
            abortController,
            method: 'GET',
            credentials: true,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extensions'])
        });
    }

    async getExtension(abortController: AbortController, namespace: string, extension: string): Promise<Readonly<Extension>> {
        const csrfResponse = await this.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendRequest<Extension>({
            abortController,
            method: 'GET',
            credentials: true,
            headers: headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'extension', namespace, extension])
        });
    }

    async deleteExtensions(abortController: AbortController, req: { namespace: string, extension: string, targetPlatformVersions?: object[] }): Promise<Readonly<SuccessResult | ErrorResult>> {
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
    getExtension(abortController: AbortController, namespace: string, extension: string): Promise<Readonly<Extension>>
    deleteExtensions(abortController: AbortController, req: { namespace: string, extension: string, targetPlatformVersions?: object[] }): Promise<Readonly<SuccessResult | ErrorResult>>
    getNamespace(abortController: AbortController, name: string): Promise<Readonly<Namespace>>
    createNamespace(abortController: AbortController, namespace: { name: string }): Promise<Readonly<SuccessResult | ErrorResult>>
    changeNamespace(abortController: AbortController, req: {oldNamespace: string, newNamespace: string, removeOldNamespace: boolean, mergeIfNewNamespaceAlreadyExists: boolean}): Promise<Readonly<SuccessResult | ErrorResult>>
    getPublisherInfo(abortController: AbortController, provider: string, login: string): Promise<Readonly<PublisherInfo>>
    revokePublisherContributions(abortController: AbortController, provider: string, login: string): Promise<Readonly<SuccessResult | ErrorResult>>
    revokeAccessTokens(abortController: AbortController, provider: string, login: string): Promise<Readonly<SuccessResult | ErrorResult>>
    getAllScans(abortController: AbortController, params?: { size?: number; offset?: number; status?: string | string[]; publisher?: string; namespace?: string; name?: string; validationType?: string[]; threatScannerName?: string[]; dateStartedFrom?: string; dateStartedTo?: string; enforcement?: 'enforced' | 'notEnforced' | 'all' }): Promise<Readonly<ScanResultsResponse>>
    getScan(abortController: AbortController, scanId: string): Promise<Readonly<ScanResultJson>>
    getScanCounts(abortController: AbortController, params?: { dateStartedFrom?: string; dateStartedTo?: string; enforcement?: 'enforced' | 'notEnforced' | 'all'; threatScannerName?: string[]; validationType?: string[] }): Promise<Readonly<ScanCounts>>
    getScanFilterOptions(abortController: AbortController): Promise<Readonly<ScanFilterOptions>>
    // Files API
    getFiles(abortController: AbortController, params?: { size?: number; offset?: number; decision?: string; publisher?: string; namespace?: string; name?: string; dateDecidedFrom?: string; dateDecidedTo?: string; sortBy?: string; sortOrder?: 'asc' | 'desc' }): Promise<Readonly<FilesResponse>>;
    getFileCounts(abortController: AbortController, params?: { dateDecidedFrom?: string; dateDecidedTo?: string }): Promise<Readonly<FileDecisionCountsJson>>
    // Decision APIs
    makeScanDecision(abortController: AbortController, request: ScanDecisionRequest): Promise<Readonly<ScanDecisionResponse>>
    makeFileDecision(abortController: AbortController, request: FileDecisionRequest): Promise<Readonly<FileDecisionResponse>>
    deleteFileDecisions(abortController: AbortController, request: FileDecisionDeleteRequest): Promise<Readonly<FileDecisionDeleteResponse>>
    getTiers(abortController: AbortController): Promise<Readonly<TierList>>;
    createTier(abortController: AbortController, tier: Tier): Promise<Readonly<Tier>>;
    updateTier(abortController: AbortController, name: string, tier: Tier): Promise<Readonly<Tier>>;
    deleteTier(abortController: AbortController, name: string): Promise<Readonly<SuccessResult | ErrorResult>>;
    getCustomers(abortController: AbortController): Promise<Readonly<CustomerList>>;
    getCustomer(abortController: AbortController, name: string): Promise<Readonly<Customer>>;
    createCustomer(abortController: AbortController, customer: Customer): Promise<Readonly<Customer>>;
    updateCustomer(abortController: AbortController, name: string, customer: Customer): Promise<Readonly<Customer>>;
    deleteCustomer(abortController: AbortController, name: string): Promise<Readonly<SuccessResult | ErrorResult>>;
    getUsageStats(abortController: AbortController, customerName: string, date: Date): Promise<Readonly<UsageStatsList>>;
    getLogs(abortController: AbortController, page?: number, size?: number, period?: string): Promise<Readonly<LogPageableList>>;
}

export interface AdminServiceConstructor {
    new (registry: ExtensionRegistryService): AdminService
}

export class AdminServiceImpl implements AdminService {

    constructor(readonly registry: ExtensionRegistryService) {}

    getExtension(abortController: AbortController, namespace: string, extension: string): Promise<Readonly<Extension>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'extension', namespace, extension])
        });
    }

    async deleteExtensions(abortController: AbortController, req: { namespace: string, extension: string, targetPlatformVersions?: object[] }): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
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
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'extension', req.namespace, req.extension, 'delete']),
            headers,
            payload: req.targetPlatformVersions
        });
    }

    getNamespace(abortController: AbortController, name: string): Promise<Readonly<Namespace>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'namespace', name])
        });
    }

    async createNamespace(abortController: AbortController, namespace: { name: string }): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'create-namespace']),
            method: 'POST',
            payload: namespace,
            headers
        });
    }

    async changeNamespace(abortController: AbortController, req: {oldNamespace: string, newNamespace: string, removeOldNamespace: boolean, mergeIfNewNamespaceAlreadyExists: boolean}): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'change-namespace']),
            method: 'POST',
            payload: req,
            headers
        });
    }

    async getPublisherInfo(abortController: AbortController, provider: string, login: string): Promise<Readonly<PublisherInfo>> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login]),
            credentials: true
        });
    }

    async revokePublisherContributions(abortController: AbortController, provider: string, login: string): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login, 'revoke']),
            headers
        });
    }

    async revokeAccessTokens(abortController: AbortController, provider: string, login: string): Promise<Readonly<SuccessResult | ErrorResult>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {};
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'POST',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', provider, login, 'tokens', 'revoke']),
            headers
        });
    }

    getAllScans(abortController: AbortController, params?: { size?: number; offset?: number; status?: string | string[]; publisher?: string; namespace?: string; name?: string; validationType?: string[]; threatScannerName?: string[]; dateStartedFrom?: string; dateStartedTo?: string; enforcement?: 'enforced' | 'notEnforced' | 'all'; adminDecision?: string[] }): Promise<Readonly<ScanResultsResponse>> {
        const query: { key: string, value: string | number }[] = [];
        if (params) {
            if (params.size !== undefined)
                query.push({ key: 'size', value: params.size });
            if (params.offset !== undefined)
                query.push({ key: 'offset', value: params.offset });
            if (params.status) {
                const statusValue = Array.isArray(params.status) ? params.status.join(',') : params.status;
                query.push({ key: 'status', value: statusValue });
            }
            if (params.publisher)
                query.push({ key: 'publisher', value: params.publisher });
            if (params.namespace)
                query.push({ key: 'namespace', value: params.namespace });
            if (params.name)
                query.push({ key: 'name', value: params.name });
            if (params.validationType && params.validationType.length > 0)
                query.push({ key: 'validationType', value: params.validationType.join(',') });
            if (params.threatScannerName && params.threatScannerName.length > 0)
                query.push({ key: 'threatScannerName', value: params.threatScannerName.join(',') });
            if (params.dateStartedFrom)
                query.push({ key: 'dateStartedFrom', value: params.dateStartedFrom });
            if (params.dateStartedTo)
                query.push({ key: 'dateStartedTo', value: params.dateStartedTo });
            if (params.enforcement)
                query.push({ key: 'enforcement', value: params.enforcement });
            if (params.adminDecision && params.adminDecision.length > 0)
                query.push({ key: 'adminDecision', value: params.adminDecision.join(',') });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans'], query);
        return sendRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    getScan(abortController: AbortController, scanId: string): Promise<Readonly<ScanResultJson>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', scanId])
        });
    }

    getScanCounts(abortController: AbortController, params?: { dateStartedFrom?: string; dateStartedTo?: string; enforcement?: 'enforced' | 'notEnforced' | 'all'; threatScannerName?: string[]; validationType?: string[] }): Promise<Readonly<ScanCounts>> {
        const query: { key: string, value: string | number }[] = [];
        if (params) {
            if (params.dateStartedFrom)
                query.push({ key: 'dateStartedFrom', value: params.dateStartedFrom });
            if (params.dateStartedTo)
                query.push({ key: 'dateStartedTo', value: params.dateStartedTo });
            if (params.enforcement)
                query.push({ key: 'enforcement', value: params.enforcement });
            if (params.threatScannerName && params.threatScannerName.length > 0) {
                query.push({ key: 'threatScannerName', value: params.threatScannerName.join(',') });
            }
            if (params.validationType && params.validationType.length > 0) {
                query.push({ key: 'validationType', value: params.validationType.join(',') });
            }
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'counts'], query);
        return sendRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    getScanFilterOptions(abortController: AbortController): Promise<Readonly<ScanFilterOptions>> {
        return sendRequest({
            abortController,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'filterOptions'])
        });
    }

    getFiles(abortController: AbortController, params?: { size?: number; offset?: number; decision?: string; publisher?: string; namespace?: string; name?: string; dateDecidedFrom?: string; dateDecidedTo?: string; sortBy?: string; sortOrder?: 'asc' | 'desc' }): Promise<Readonly<FilesResponse>> {
        const query: { key: string, value: string | number }[] = [];
        if (params) {
            if (params.size !== undefined)
                query.push({ key: 'size', value: params.size });
            if (params.offset !== undefined)
                query.push({ key: 'offset', value: params.offset });
            if (params.decision)
                query.push({ key: 'decision', value: params.decision });
            if (params.publisher)
                query.push({ key: 'publisher', value: params.publisher });
            if (params.namespace)
                query.push({ key: 'namespace', value: params.namespace });
            if (params.name)
                query.push({ key: 'name', value: params.name });
            if (params.dateDecidedFrom)
                query.push({ key: 'dateDecidedFrom', value: params.dateDecidedFrom });
            if (params.dateDecidedTo)
                query.push({ key: 'dateDecidedTo', value: params.dateDecidedTo });
            if (params.sortBy)
                query.push({ key: 'sortBy', value: params.sortBy });
            if (params.sortOrder)
                query.push({ key: 'sortOrder', value: params.sortOrder });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files'], query);
        return sendRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    getFileCounts(abortController: AbortController, params?: { dateDecidedFrom?: string; dateDecidedTo?: string }): Promise<Readonly<FileDecisionCountsJson>> {
        const query: { key: string, value: string | number }[] = [];
        if (params) {
            if (params.dateDecidedFrom)
                query.push({ key: 'dateDecidedFrom', value: params.dateDecidedFrom });
            if (params.dateDecidedTo)
                query.push({ key: 'dateDecidedTo', value: params.dateDecidedTo });
        }
        const endpoint = createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'counts'], query);
        return sendRequest({
            abortController,
            credentials: true,
            endpoint
        });
    }

    async makeScanDecision(abortController: AbortController, request: ScanDecisionRequest): Promise<Readonly<ScanDecisionResponse>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
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
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'decisions']),
            headers,
            payload: request
        });
    }

    async makeFileDecision(abortController: AbortController, request: FileDecisionRequest): Promise<Readonly<FileDecisionResponse>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
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
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'decisions']),
            headers,
            payload: request
        });
    }

    async deleteFileDecisions(abortController: AbortController, request: FileDecisionDeleteRequest): Promise<Readonly<FileDecisionDeleteResponse>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }

        return sendRequest({
            abortController,
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'scans', 'files', 'decisions']),
            headers,
            payload: request
        });
    }

    async getTiers(abortController: AbortController): Promise<Readonly<TierList>> {
        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers']),
            credentials: true
        }, false);
    }

    async createTier(abortController: AbortController, tier: Tier): Promise<Readonly<Tier>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
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
            payload: tier,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', 'create']),
            headers
        }, false);
    }

    async updateTier(abortController: AbortController, name: string, tier: Tier): Promise<Readonly<Tier>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'PUT',
            payload: tier,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', name]),
            headers
        }, false);
    }

    async deleteTier(abortController: AbortController, name: string): Promise<SuccessResult | ErrorResult> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'tiers', name]),
            headers
        }, false);
    }

    // TODO: Remove mock users when backend returns real user data
    private static readonly MOCK_USERS: UserData[] = [
        {
            loginName: 'rhdevelopers-ci',
            fullName: 'Red Hat Developers CI',
            avatarUrl: 'https://avatars.githubusercontent.com/u/18214726?v=4',
            homepage: 'https://github.com/rhdevelopers-ci',
            provider: 'github',
            tokensUrl: '',
            createTokenUrl: ''
        },
        {
            loginName: 'midudev',
            fullName: 'Miguel Ángel Durán',
            avatarUrl: 'https://avatars.githubusercontent.com/u/1561955?v=4',
            homepage: 'https://github.com/midudev',
            provider: 'github',
            tokensUrl: '',
            createTokenUrl: ''
        },
        {
            loginName: 'jakubmisek',
            fullName: 'Jakub Míšek',
            avatarUrl: 'https://avatars.githubusercontent.com/u/842150?v=4',
            homepage: 'https://github.com/jakubmisek',
            provider: 'github',
            tokensUrl: '',
            createTokenUrl: ''
        },
        {
            loginName: 'test',
            fullName: 'Miguel Ángel Durán',
            avatarUrl: 'https://avatars.githubusercontent.com/u/1561955?v=4',
            homepage: 'https://github.com/midudev',
            provider: 'github',
            tokensUrl: '',
            createTokenUrl: ''
        }
    ];

    private injectMockUsers(customer: Customer): Customer {
        return { ...customer, users: AdminServiceImpl.MOCK_USERS };
    }

    async getCustomers(abortController: AbortController): Promise<Readonly<CustomerList>> {
        const data: CustomerList = await sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers']),
            credentials: true
        }, false);
        return { customers: data.customers.map(this.injectMockUsers) };
    }

    async getCustomer(abortController: AbortController, name: string): Promise<Readonly<Customer>> {
        const data: Customer = await sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            credentials: true
        }, false);
        return this.injectMockUsers(data);
    }

    async createCustomer(abortController: AbortController, customer: Customer): Promise<Readonly<Customer>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
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
            payload: customer,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', 'create']),
            headers
        }, false);
    }

    async updateCustomer(abortController: AbortController, name: string, customer: Customer): Promise<Readonly<Customer>> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'PUT',
            payload: customer,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            headers
        }, false);
    }

    async deleteCustomer(abortController: AbortController, name: string): Promise<SuccessResult | ErrorResult> {
        const csrfResponse = await this.registry.getCsrfToken(abortController);
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendRequest({
            abortController,
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', name]),
            headers
        }, false);
    }

    /**
     * Get usage stats for a customer within an optional date range.
     */
    async getUsageStats(
        abortController: AbortController,
        customerName: string,
        date: Date,
    ): Promise<Readonly<UsageStatsList>> {
        const query: { key: string, value: string | number }[] = [];
        query.push({ key: 'date', value: date.toISOString() });

        return sendRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'ratelimit', 'customers', customerName, 'usage'], query),
            credentials: true
        }, false);
    }

    async getLogs(
        abortController: AbortController,
        page: number = 0,
        size: number = 20,
        period?: string
    ): Promise<Readonly<LogPageableList>> {
        const query: { key: string, value: string | number }[] = [
            { key: 'page', value: page },
            { key: 'size', value: size }
        ];
        if (period) {
            query.push({ key: 'period', value: period });
        }
        const endpoint = addQuery(createAbsoluteURL([this.registry.serverUrl, 'admin', 'logs']), query);
        return sendRequest({
            abortController,
            endpoint,
            credentials: true
        }, false);
    }
}

export interface ExtensionFilter {
    query: string;
    category: ExtensionCategory | '';
    size: number;
    offset: number;
    sortBy: SortBy;
    sortOrder: SortOrder;
}
