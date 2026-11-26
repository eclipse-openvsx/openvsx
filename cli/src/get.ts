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
import { Registry, Extension } from "./registry";
import { matchExtensionId, addEnvOptions, rejectError } from './util';
import { GetOptions } from './get-options';

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

    const matchingVersion = await findMatchingVersion(registry, extension, options.version);
    if (matchingVersion.error) {
        throw new Error(matchingVersion.error);
    }

    if (options.metadata) {
        await printMetadata(registry, matchingVersion, options.output);
    } else {
        await download(registry, matchingVersion, options.output);
    }
}

function findMatchingVersion(registry: Registry, extension: Extension, constraint?: string): Promise<Extension> {
    if (!constraint || semver.satisfies(extension.version, constraint)) {
        return Promise.resolve(extension);
    }
    const versions = Object.keys(extension.allVersions).filter(version => !isAlias(extension, version));
    const version = semver.maxSatisfying(versions, constraint);
    if (version) {
        try {
            return registry.getJson(new URL(extension.allVersions[version]));
        } catch (err) {
            return rejectError(err);
        }
    }
    return Promise.reject(new Error(`Extension ${extension.namespace}.${extension.name} has no published version matching '${constraint}'`));
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
    if (fileName === '' || /[/\\:\0]|[. ]$/.test(fileName) || /^(con|prn|aux|nul|com\d|lpt\d)(\..*)?$/i.test(fileName)) {
        throw new Error(`The registry returned an invalid file name: ${JSON.stringify(fileName)}`);
    }
    return fileName;
}
