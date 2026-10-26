/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/

import { APIRequestContext, APIResponse, expect } from '@playwright/test';

/** An extension as the registry describes it, which is what VS Code should show. */
export interface RegistryExtension {
    id: string;
    namespace: string;
    name: string;
    displayName: string;
    publisher: string;
    description: string;
    /** The latest release, which VS Code installs unless asked for a pre-release. */
    version: string;
    preReleaseVersion?: string;
    categories: string[];
    repository: string;
    /** The ids of the extensions it depends on. */
    dependencies: string[];
    /** The ids of the extensions its extension pack installs. */
    bundledExtensions: string[];
    readme: string;
    changelog: string;
    manifest: ExtensionManifest;
}

export interface ExtensionManifest {
    contributes?: Record<string, unknown>;
}

interface ExtensionReference {
    namespace: string;
    extension: string;
}

interface ExtensionJson {
    displayName: string;
    namespaceDisplayName: string;
    description: string;
    version: string;
    preRelease?: boolean;
    allVersions: Record<string, string>;
    categories: string[];
    repository: string;
    dependencies?: ExtensionReference[];
    bundledExtensions?: ExtensionReference[];
    files: { readme?: string; changelog?: string; manifest: string };
}

/** The Open VSX API of the registry code-server installs from. */
export class Registry {
    constructor(private readonly request: APIRequestContext) {}

    async extension(id: string): Promise<RegistryExtension> {
        const [namespace, name] = id.split('.');
        // the release VS Code shows, while the API's latest version can be a newer pre-release
        const version = await this.latestRelease(id);
        const json = await this.json(namespace, name, version);
        return {
            id,
            namespace,
            name,
            displayName: json.displayName,
            publisher: json.namespaceDisplayName,
            description: json.description,
            version,
            preReleaseVersion: json.allVersions['pre-release']
                ? (await this.json(namespace, name, 'pre-release')).version
                : undefined,
            categories: json.categories,
            repository: json.repository,
            dependencies: (json.dependencies ?? []).map(toId),
            bundledExtensions: (json.bundledExtensions ?? []).map(toId),
            readme: json.files.readme ? await (await this.file(json.files.readme)).text() : '',
            changelog: json.files.changelog ? await (await this.file(json.files.changelog)).text() : '',
            manifest: await (await this.file(json.files.manifest)).json()
        };
    }

    async displayName(id: string): Promise<string> {
        const [namespace, name] = id.split('.');
        return (await this.json(namespace, name)).displayName;
    }

    /** The status a URL ends up with, after any redirects. */
    async statusOf(url: string): Promise<number> {
        return (await this.request.get(url)).status();
    }

    /** Where the registry redirects a URL to, such as an item URL to the extension's page. */
    async redirectOf(url: string): Promise<string | undefined> {
        const response = await this.request.get(url, { maxRedirects: 0 });
        return response.headers()['location'];
    }

    private async json(namespace: string, name: string, version?: string): Promise<ExtensionJson> {
        return (await this.get(`/api/${namespace}/${name}${version ? `/${version}` : ''}`)).json();
    }

    /** The newest release, which the API's latest version is not when a pre-release is newer. */
    private async latestRelease(id: string): Promise<string> {
        // newest first, and an extension can have hundreds of pre-releases after its latest release
        for (let offset = 0; ; offset += 100) {
            const query = `/api/-/query?extensionId=${id}&includeAllVersions=true&size=100&offset=${offset}`;
            const { extensions }: { extensions: ExtensionJson[] } = await (await this.get(query)).json();
            const release = extensions.find(extension => !extension.preRelease);
            if (release || !extensions.length) {
                return release?.version ?? '';
            }
        }
    }

    /**
     * A file the API links to, fetched by its path alone: without ovsx.server.url, the API caches
     * its JSON with links to whichever host asked for it first.
     */
    private file(url: string): Promise<APIResponse> {
        return this.get(new URL(url).pathname);
    }

    private async get(url: string): Promise<APIResponse> {
        const response = await this.request.get(url);
        await expect(response).toBeOK();
        return response;
    }
}

/** The text of a markdown document's first heading, as VS Code renders it. */
export function firstHeading(markdown: string): string | undefined {
    const heading = markdown.split('\n').find(line => /^#{1,6}\s/.test(line));
    return heading && plainText(heading);
}

/** The text of a markdown document's first line, as VS Code renders it. */
export function firstLine(markdown: string): string {
    return plainText(markdown.split('\n').find(line => line.trim()) ?? '');
}

function toId(reference: ExtensionReference): string {
    return `${reference.namespace}.${reference.extension}`;
}

function plainText(line: string): string {
    return line
        .replace(/^#+\s*/, '')
        .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
        .replace(/[`*]/g, '')
        .trim();
}
