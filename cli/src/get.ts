/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import * as fs from 'fs';
import * as path from 'path';
import * as semver from 'semver';
import { Registry, Extension, VersionReferences } from "./registry";
import { matchExtensionId, addEnvOptions, rejectError, StatusError } from './util';
import { GetOptions } from './get-options';

const VERSION_PAGE_SIZE = 100;

/**
 * Downloads an extension or its metadata.
 */
export async function getExtension(options: GetOptions): Promise<void> {
    addEnvOptions(options);
    const registry = new Registry(options);
    const match = matchExtensionId(options.extensionId);
    if (!match) {
        throw new Error('The extension identifier must have the form `namespace.extension` or `namespace/extension`.');
    }

    const extension = await registry.getMetadata(match[1], match[2], options.target);
    if (extension.error) {
        throw new Error(extension.error);
    }

    const matchingVersion = await findMatchingVersion(registry, extension, options.version, options.target);
    if (matchingVersion.error) {
        throw new Error(matchingVersion.error);
    }

    if (options.metadata) {
        await printMetadata(registry, matchingVersion, options.output);
    } else {
        await download(registry, matchingVersion, options.output);
    }
}

async function findMatchingVersion(registry: Registry, extension: Extension, constraint?: string, target?: string): Promise<Extension> {
    if (!constraint || semver.satisfies(extension.version, constraint)) {
        return extension;
    }
    const versions = Object.keys(extension.allVersions).filter(version => !isAlias(extension, version));
    const version = semver.maxSatisfying(versions, constraint);
    if (version) {
        try {
            return await registry.getJson(new URL(extension.allVersions[version]));
        } catch (err) {
            return rejectError(err);
        }
    }
    // allVersions only lists the highest versions the server returns, so look further back.
    const olderVersion = semver.maxSatisfying(await getAllVersions(registry, extension, target), constraint);
    if (olderVersion) {
        return registry.getMetadata(extension.namespace, extension.name, target, olderVersion);
    }
    throw new Error(`Extension ${extension.namespace}.${extension.name} has no published version matching '${constraint}'`);
}

/** A registry that answers the first page with 404 has no version-references endpoint and yields no versions. */
async function getAllVersions(registry: Registry, extension: Extension, target?: string): Promise<string[]> {
    const versions = new Set<string>();
    let offset = 0;
    for (;;) {
        let page: VersionReferences;
        try {
            page = await registry.getVersionReferences(extension.namespace, extension.name, target, VERSION_PAGE_SIZE, offset);
        } catch (err) {
            if (offset === 0 && (err as StatusError).status === 404) {
                return [];
            }
            throw err;
        }
        if (page.error) {
            throw new Error(page.error);
        }
        const references = page.versions ?? [];
        references.forEach(reference => versions.add(reference.version));
        offset += references.length;
        if (references.length === 0 || offset >= page.totalSize) {
            return [...versions];
        }
    }
}

function isAlias(extension: Extension, version: string): boolean {
    return extension.versionAlias.includes(version);
}

async function printMetadata(registry: Registry, extension: Extension, output?: string): Promise<void> {
    const metadata = JSON.stringify(extension, null, 4);
    if (!output) {
        console.log(metadata);
        return;
    }
    let filePath: string | undefined;
    const stats = fs.statSync(output, { throwIfNoEntry: false });
    if (stats?.isDirectory() || !stats && output.endsWith(path.sep)) {
        const fileName = checkFileName(`${extension.namespace}.${extension.name}-${extension.version}.json`);
        filePath = path.resolve(process.cwd(), output, fileName);
    } else {
        filePath = path.resolve(process.cwd(), output);
    }
    await fs.promises.mkdir(path.dirname(filePath), { recursive: true });
    await fs.promises.writeFile(filePath, metadata);
}

async function download(registry: Registry, extension: Extension, output?: string): Promise<void> {
    const downloadUrl = extension.files.download;
    if (!downloadUrl) {
        throw new Error(`Extension ${extension.namespace}.${extension.name} does not provide a download URL.`);
    }
    let filePath: string | undefined;
    if (output) {
        const stats = fs.statSync(output, { throwIfNoEntry: false });
        if (stats?.isDirectory() || !stats && output.endsWith(path.sep)) {
            filePath = path.resolve(process.cwd(), output, downloadFileName(downloadUrl));
        } else {
            filePath = path.resolve(process.cwd(), output);
        }
    } else {
        filePath = path.resolve(process.cwd(), downloadFileName(downloadUrl));
    }
    await fs.promises.mkdir(path.dirname(filePath), { recursive: true });
    const target = extension.targetPlatform !== 'universal' ? '@' + extension.targetPlatform : '';
    console.log(`Downloading ${extension.namespace}.${extension.name}-${extension.version}${target} to ${filePath}`);
    await registry.download(filePath, new URL(downloadUrl));
}

function downloadFileName(downloadUrl: string): string {
    const encoded = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
    let fileName: string;
    try {
        fileName = decodeURIComponent(encoded);
    } catch {
        throw new Error(`The registry returned an invalid file name: ${JSON.stringify(encoded)}`);
    }
    return checkFileName(fileName);
}

/**
 * Rejects a file name from the registry that is not a plain name, so the file cannot be written
 * outside the target directory. Windows trims trailing dots and spaces and maps reserved names to devices.
 */
function checkFileName(fileName: string): string {
    // eslint-disable-next-line no-control-regex -- matching control characters is the point
    if (fileName === '' || /[/\\:\x00-\x1f\x7f]|[. ]$/.test(fileName) || /^(con|prn|aux|nul|com\d|lpt\d)(\..*)?$/i.test(fileName)) {
        throw new Error(`The registry returned an invalid file name: ${JSON.stringify(fileName)}`);
    }
    return fileName;
}
