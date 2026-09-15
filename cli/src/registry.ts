/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import * as http from 'http';
import * as fs from 'fs';
import { pipeline, Writable } from 'stream';
import * as followRedirects from 'follow-redirects';
import { RegistryOptions } from './registry-options';
import { DEFAULT_TIMEOUT, redactUrl, rejectError, statusError, withStatus } from './util';

export const DEFAULT_URL = 'https://open-vsx.org';
export const DEFAULT_NAMESPACE_SIZE = 1024;
export const DEFAULT_PUBLISH_SIZE = 512 * 1024 * 1024;
export { DEFAULT_TIMEOUT };
export const DEFAULT_TOKEN_REQUEST_SIZE = 8 * 1024;
export const DEFAULT_DELETE_SIZE = 64 * 1024;

export class Registry {

    readonly url: string;
    readonly maxNamespaceSize: number;
    readonly maxPublishSize: number;
    readonly timeout: number;
    readonly username?: string;
    readonly password?: string;

    constructor(options: RegistryOptions = {}) {
        if (options.registryUrl?.endsWith('/'))
            this.url = options.registryUrl.substring(0, options.registryUrl.length - 1);
        else if (options.registryUrl)
            this.url = options.registryUrl;
        else
            this.url = DEFAULT_URL;

        this.maxNamespaceSize = options.maxNamespaceSize ?? DEFAULT_NAMESPACE_SIZE;
        this.maxPublishSize = options.maxPublishSize ?? DEFAULT_PUBLISH_SIZE;
        this.timeout = options.timeout ?? DEFAULT_TIMEOUT;
        this.username = options.username;
        this.password = options.password;
    }

    get requiresLicense(): boolean {
        const url = new URL(this.url);
        return url.hostname === 'open-vsx.org' || url.hostname.endsWith('.open-vsx.org');
    }

    createNamespace(name: string, pat: string): Promise<Response> {
        try {
            const url = this.getUrl(['api', '-', 'namespace', 'create'], { token: pat });
            const namespace = { name };
            return this.post(JSON.stringify(namespace), url, {
                'Content-Type': 'application/json'
            }, this.maxNamespaceSize);
        } catch (err) {
            return rejectError(err);
        }
    }

    verifyPat(namespace: string, pat: string): Promise<Response> {
        try {
            return this.getJson(this.getUrl(['api', namespace, 'verify-pat'], { token: pat }));
        } catch (err) {
            return rejectError(err);
        }
    }

    getRegistryVersion(): Promise<RegistryVersion> {
        try {
            return this.getJson(this.getUrl(['api', 'version']));
        } catch (err) {
            return rejectError(err);
        }
    }

    publish(file: string, pat: string): Promise<Extension> {
        try {
            const url = this.getUrl(['api', '-', 'publish'], { token: pat });
            return this.postFile(file, url, {
                'Content-Type': 'application/octet-stream'
            }, this.maxPublishSize);
        } catch (err) {
            return rejectError(err);
        }
    }

    requestTrustedPublishingToken(namespace: string, extension: string, idToken: string): Promise<AccessToken> {
        try {
            const url = this.getUrl(['api', '-', 'trusted-publishing', 'token']);
            const request = { namespace, extension, token: idToken };
            return this.post(JSON.stringify(request), url, {
                'Content-Type': 'application/json'
            }, DEFAULT_TOKEN_REQUEST_SIZE);
        } catch (err) {
            return rejectError(err);
        }
    }

    /**
     * Deletes extension versions. Omitting `targetVersions` deletes the extension as a whole,
     * i.e. all versions the personal access token's user is allowed to delete.
     */
    deleteExtension(
        namespace: string,
        extension: string,
        targetVersions: TargetPlatformVersion[] | undefined,
        pat: string
    ): Promise<Response> {
        try {
            if (!targetVersions) {
                const url = this.getUrl(['api', namespace, extension, 'delete'], { token: pat, allVersions: 'true' });
                return this.post('', url, undefined, DEFAULT_DELETE_SIZE);
            }

            const url = this.getUrl(['api', namespace, extension, 'delete'], { token: pat });
            return this.post(JSON.stringify(targetVersions), url, {
                'Content-Type': 'application/json'
            }, DEFAULT_DELETE_SIZE);
        } catch (err) {
            return rejectError(err);
        }
    }

    getMetadata(namespace: string, extension: string, target?: string, version?: string): Promise<Extension> {
        try {
            const segments = ['api', namespace, extension];
            if (target) {
                segments.push(target);
            }
            if (version) {
                segments.push(version);
            }
            return this.getJson(this.getUrl(segments));
        } catch (err) {
            return rejectError(err);
        }
    }

    /**
     * Returns a page of an extension's published versions, newest first, one entry per version and
     * target platform. `allVersions` on the metadata response carries version numbers and links
     * only, so this is what makes the target platforms of each version available.
     */
    getVersionReferences(
        namespace: string,
        extension: string,
        target: string | undefined,
        size: number,
        offset: number
    ): Promise<VersionReferences> {
        try {
            const segments = ['api', namespace, extension];
            if (target) {
                segments.push(target);
            }
            segments.push('version-references');
            return this.getJson(this.getUrl(segments, {
                size: String(size),
                offset: String(offset)
            }));
        } catch (err) {
            return rejectError(err);
        }
    }

    /**
     * Full-text search across the registry. Returns the purpose-built summary shape rather than
     * whole extension records, so a page of results stays small.
     */
    search(options: SearchQuery): Promise<SearchResult> {
        try {
            const query: Record<string, string> = {
                size: String(options.size),
                offset: String(options.offset)
            };
            if (options.query) {
                query.query = options.query;
            }
            if (options.category) {
                query.category = options.category;
            }
            if (options.targetPlatform) {
                query.targetPlatform = options.targetPlatform;
            }
            if (options.sortBy) {
                query.sortBy = options.sortBy;
            }
            if (options.sortOrder) {
                query.sortOrder = options.sortOrder;
            }
            return this.getJson(this.getUrl(['api', '-', 'search'], query));
        } catch (err) {
            return rejectError(err);
        }
    }

    /** Returns a namespace and the extensions published in it. */
    getNamespace(namespace: string): Promise<Namespace> {
        try {
            return this.getJson(this.getUrl(['api', namespace]));
        } catch (err) {
            return rejectError(err);
        }
    }

    download(file: string, url: URL): Promise<void> {
        return new Promise((resolve, reject) => {
            // Written beside the target and renamed into place on success, so the caller's path holds
            // either what it held before or the whole download, never part of one. Deferring the open
            // until the status is known is not enough on its own: a connection dropped mid-body has
            // already truncated the file by then, and `get` is handed a path the user chose.
            const partial = `${file}.part`;
            let stream: fs.WriteStream | undefined;

            // Claimed synchronously, before the cleanup that has to happen before rejecting. A timeout
            // mid-body drives both the request's error handler and pipeline's callback, and each used
            // to wait on its own fs.rm - so whichever landed first decided what the caller was told,
            // and the partial was removed twice.
            let failed = false;
            const fail = (err: Error) => {
                if (failed) {
                    return;
                }
                failed = true;
                stream?.destroy();
                fs.rm(partial, { force: true }, () => reject(err));
            };

            const requestOptions = this.getRequestOptions();
            const request = this.getProtocol(url)
                                .request(url, requestOptions, response => {
                if (response.statusCode !== undefined && (response.statusCode < 200 || response.statusCode > 299)) {
                    response.resume();
                    reject(statusError(response));
                    return;
                }

                stream = fs.createWriteStream(partial);

                // pipeline rather than response.pipe: pipe installs its own error handler on the
                // source, so a connection dropped mid-body is swallowed - the write stream is never
                // ended, nothing settles, and the caller waits for a file that will never arrive.
                // pipeline propagates that error and tears both ends down. Its callback also waits
                // for the file to close, which matters in its own right: a write stream opens and
                // flushes asynchronously, so the last byte having arrived says nothing about the
                // file being on disk.
                pipeline(response, stream, (err: NodeJS.ErrnoException | null) => {
                    if (err) {
                        fail(err);
                    } else if (!response.complete) {
                        // A body cut short still ends the stream cleanly; `complete` is what tells
                        // that apart from having received all of it.
                        fail(new Error(`The connection closed before the whole of ${redactUrl(url)} was received.`));
                    } else {
                        fs.rename(partial, file, renameErr => renameErr ? fail(renameErr) : resolve());
                    }
                });
            });
            request.on('error', (err: Error) => fail(err));
            this.failOnTimeout(request, url, fail);
            request.end();
        });
    }

    getJson<T extends Response>(url: URL): Promise<T> {
        return new Promise((resolve, reject) => {
            const requestOptions = this.getRequestOptions();
            const request = this.getProtocol(url)
                                .request(url, requestOptions, this.getJsonResponse<T>(resolve, reject));
            request.on('error', reject);
            this.failOnTimeout(request, url, reject);
            request.end();
        });
    }

    post<T extends Response>(content: string | Buffer | Uint8Array, url: URL, headers?: http.OutgoingHttpHeaders, maxBodyLength?: number): Promise<T> {
        return new Promise((resolve, reject) => {
            const requestOptions = this.getRequestOptions('POST', headers, maxBodyLength);
            const request = this.getProtocol(url)
                                .request(url, requestOptions, this.getJsonResponse<T>(resolve, reject));
            request.on('error', reject);
            this.failOnTimeout(request, url, reject);
            request.write(content);
            request.end();
        });
    }

    postFile<T extends Response>(file: string, url: URL, headers?: http.OutgoingHttpHeaders, maxBodyLength?: number): Promise<T> {
        return new Promise((resolve, reject) => {
            const stream = fs.createReadStream(file);
            const requestOptions = this.getRequestOptions('POST', headers, maxBodyLength);
            const request = this.getProtocol(url)
                                .request(url, requestOptions, this.getJsonResponse<T>(resolve, reject));
            stream.on('error', (err: Error) => {
                request.destroy();
                reject(err);
            });
            request.on('error', (err: Error) => {
                stream.close();
                reject(err);
            });
            this.failOnTimeout(request, url, reject);
            stream.on('open', () => stream.pipe(request));
        });
    }

    private getUrl(segments: string[], query?: Record<string, string>): URL {
        const url = new URL(this.url);
        const basePath = url.pathname.replace(/\/+$/, '');
        const encodedSegments = segments.filter(s => s.length > 0).map(encodeURIComponent);
        url.pathname = `${basePath}/${encodedSegments.join('/')}`;
        if (query) {
            url.search = new URLSearchParams(query).toString();
        }
        return url;
    }

    private getProtocol(url: URL) {
        return url.protocol === 'https:' ? followRedirects.https : followRedirects.http;
    }

    /**
     * Node's `timeout` option only raises an event - the request stays open, which is why a server
     * that accepts a connection and then says nothing used to hold a command open indefinitely.
     * Destroying the request with an error routes through the error handling each caller already
     * has, so a stalled request rejects the way any other failure does. With a timeout of zero the
     * event never fires and this does nothing.
     */
    // Typed on Writable because both http.ClientRequest and follow-redirects' wrapper are ones, and
    // all this needs is the timeout event and destroy.
    private failOnTimeout(request: Writable, url: URL, fail: (err: Error) => void): void {
        request.on('timeout', () => {
            // Reported before the request is torn down, rather than by destroying it with the error.
            // Destroying raises errors of its own - ECONNRESET on the response, a premature close
            // from pipeline - and node guarantees no order between those and the request's own. In
            // practice the request's arrives first, so this is belt and braces rather than the thing
            // that stops the wrong error being reported; that is the caller claiming the failure
            // before it does any asynchronous cleanup.
            fail(new Error(`No response from ${redactUrl(url)} for ${this.timeout} ms.`));
            request.destroy();
        });
    }

    private getRequestOptions(method?: string, headers?: http.OutgoingHttpHeaders, maxBodyLength?: number): http.RequestOptions {
        if (this.username && this.password) {
            headers ??= {};
            const credentials = Buffer.from(this.username + ':' + this.password).toString('base64');
            headers['Authorization'] = 'Basic ' + credentials;
        }
        return {
            method,
            headers,
            maxBodyLength,
            timeout: this.timeout
        } as http.RequestOptions;
    }

    private getJsonResponse<T extends Response>(resolve: (value: T) => void, reject: (reason: any) => void): (res: http.IncomingMessage) => void {
        return response => {
            response.setEncoding('utf-8');
            let json = '';
            // A connection lost after the headers have arrived ends the response without 'end' ever
            // firing, so without this the promise is never settled and the command waits on a body
            // that is not coming. The configured timeout would eventually rescue it, but rejecting
            // here reports what actually happened instead of thirty seconds of silence.
            response.on('error', reject);
            response.on('data', chunk => json += chunk);
            response.on('end', () => {
                if (response.statusCode !== undefined && (response.statusCode < 200 || response.statusCode > 299)) {
                    if (json.startsWith('{')) {
                        try {
                            const parsed = JSON.parse(json) as ErrorResponse;
                            const message = parsed.message || parsed.error;
                            if (message) {
                                // keep the status: the message alone cannot say whether retrying is worth it
                                reject(withStatus(new Error(message), response.statusCode));
                                return;
                            }
                        } catch (err) {
                            // Ignore the error and reject with response status
                        }
                    }
                    reject(statusError(response));
                } else if (json.startsWith('<!DOCTYPE html>')) {
                    reject(json);
                } else {
                    try {
                        resolve(JSON.parse(json));
                    } catch (err) {
                        reject(err);
                    }
                }
            });
        };
    }

}

export interface Response {
    success?: string;
    warning?: string;
    error?: string;
}

export interface Extension extends Response {
    namespaceUrl: string;
    reviewsUrl: string;
    // key: file type, value: url
    files: { [type: string]: string };

    name: string;
    namespace: string;
    version: string;
    targetPlatform: string;
    publishedBy: UserData;
    verified: boolean;
    // key: version, value: url
    allVersions: { [version: string]: string };

    averageRating?: number;
    downloadCount: number;
    reviewCount: number;

    versionAlias: string[];
    timestamp: string;
    preview?: boolean;
    preRelease?: boolean;
    displayName?: string;
    namespaceDisplayName?: string;
    description?: string;
    deprecated?: boolean;
    replacement?: ExtensionReplacement;
    downloadable?: boolean;
    publishedWithTrustedPublishing?: boolean;
    namespaceOwnershipConflict?: boolean;
    extensionKind?: string[];
    localizedLanguages?: string[];
    sponsorLink?: string;

    // key: engine, value: version constraint
    engines?: { [engine: string]: string };
    categories?: string[];
    tags?: string[];
    license?: string;
    homepage?: string;
    repository?: string;
    bugs?: string;
    markdown?: string;
    galleryColor?: string;
    galleryTheme?: string;
    qna?: string;
    badges?: Badge[];
    dependencies?: ExtensionReference[];
    bundledExtensions?: ExtensionReference[];
}

export interface RegistryVersion extends Response {
    version: string;
    maxExtensionSize: number;
    trustedPublishingAudience?: string;
}

export interface AccessToken extends Response {
    id: number;
    value?: string;
    description: string;
    createdTimestamp: string;
    accessedTimestamp?: string;
    expiresTimestamp?: string;
}

export interface TargetPlatformVersion {
    version: string;
    targetPlatform?: string;
}

export interface UserData {
    loginName: string;
    fullName?: string;
    avatarUrl?: string;
    homepage?: string;
}

export interface Badge {
    url: string;
    href: string;
    description: string;
}

export interface ExtensionReplacement {
    url: string;
    displayName?: string;
}

export interface VersionReference {
    url: string;
    files: { [type: string]: string };
    version: string;
    targetPlatform?: string;
    engines?: { [engine: string]: string };
}

export interface VersionReferences extends Response {
    offset: number;
    totalSize: number;
    versions?: VersionReference[];
}

export interface SearchQuery {
    query?: string;
    category?: string;
    targetPlatform?: string;
    sortBy?: string;
    sortOrder?: string;
    size: number;
    offset: number;
}

export interface SearchEntry {
    url: string;
    files: { [type: string]: string };
    name: string;
    namespace: string;
    version: string;
    timestamp: string;
    verified?: boolean;
    averageRating?: number;
    reviewCount?: number;
    downloadCount: number;
    displayName?: string;
    description?: string;
    deprecated?: boolean;
}

export interface SearchResult extends Response {
    offset: number;
    totalSize: number;
    extensions?: SearchEntry[];
}

export interface Namespace extends Response {
    name: string;
    verified?: boolean;
    // key: extension name, value: url
    extensions?: { [name: string]: string };
}

export interface ExtensionReference {
    url: string;
    namespace: string;
    extension: string;
    version?: string;
}

export interface ErrorResponse {
    error: string;
    message: string;
    status: number;
    path?: string;
    timestamp?: string;
    trace?: string;
}
