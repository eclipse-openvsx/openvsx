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
import { promisify, matchExtensionId, optionalStat, makeDirs, addEnvOptions, rejectError } from './util';
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
    for (const version of Object.keys(extension.allVersions)) {
        if (!isAlias(extension, version) && semver.satisfies(version, constraint)) {
            try {
                return registry.getJson(new URL(extension.allVersions[version]));
            } catch (err) {
                return rejectError(err);
            }
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
    const stats = await optionalStat(output);
    if (stats?.isDirectory() || !stats && output.endsWith(path.sep)) {
        const fileName = `${extension.namespace}.${extension.name}-${extension.version}.json`;
        filePath = path.resolve(process.cwd(), output, fileName);
    } else {
        filePath = path.resolve(process.cwd(), output);
    }
    await makeDirs(path.dirname(filePath));
    await promisify(fs.writeFile)(filePath, metadata);
}

async function download(registry: Registry, extension: Extension, output?: string): Promise<void> {
    const downloadUrl = extension.files.download;
    if (!downloadUrl) {
        throw new Error(`Extension ${extension.namespace}.${extension.name} does not provide a download URL.`);
    }
    const fileNameIndex = downloadUrl.lastIndexOf('/');
    const fileName = decodeURIComponent(downloadUrl.substring(fileNameIndex + 1));
    let filePath: string | undefined;
    if (output) {
        const stats = await optionalStat(output);
        if (stats?.isDirectory() || !stats && output.endsWith(path.sep)) {
            filePath = path.resolve(process.cwd(), output, fileName);
        } else {
            filePath = path.resolve(process.cwd(), output);
        }
    } else {
        filePath = path.resolve(process.cwd(), fileName);
    }
    await makeDirs(path.dirname(filePath));
    const target = extension.targetPlatform !== 'universal' ? '@' + extension.targetPlatform : '';
    console.log(`Downloading ${extension.namespace}.${extension.name}-${extension.version}${target} to ${filePath}`);
    await registry.download(filePath, new URL(downloadUrl));
}
