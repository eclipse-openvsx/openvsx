/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/

import * as crypto from 'crypto';
import * as fs from 'fs';
import { Registry, Extension } from './registry';
import { addEnvOptions, getTempFilePath } from './util';
import { readVSIXPackage, readZip } from './zip';
import { VerifyOptions } from './verify-options';

const SIGNATURE_ENTRY = '.signature.sig';

/**
 * Verifies a downloaded .vsix package's signature against the registry's public key, the same way
 * VS Code itself verifies signed packages on install. Mirrors `vsce verify-signature`, except the
 * signature and public key are fetched from the registry instead of being passed in as separate
 * files - the registry already exposes both for any published, signed version.
 */
export async function verify(options: VerifyOptions): Promise<void> {
    addEnvOptions(options);
    const registry = new Registry(options);

    const manifest = await readVSIXPackage(options.packagePath);
    const namespace = manifest.publisher;
    const extensionName = manifest.name;

    const latest = await registry.getMetadata(namespace, extensionName, options.target);
    if (latest.error) {
        throw new Error(latest.error);
    }

    const extension = await resolveVersion(registry, latest, manifest.version);
    const description = `${namespace}.${extensionName} v${extension.version}`;

    const signatureUrl = extension.files.signature;
    const publicKeyUrl = extension.files.publicKey;
    if (!signatureUrl || !publicKeyUrl) {
        throw new Error(`${description} is not signed by the registry - there is nothing to verify.`);
    }

    const [signature, publicKeyPem] = await Promise.all([
        downloadSignature(registry, signatureUrl),
        downloadPublicKey(registry, publicKeyUrl)
    ]);

    const packageBytes = await fs.promises.readFile(options.packagePath);
    const publicKey = crypto.createPublicKey({ key: publicKeyPem, format: 'pem' });
    const verified = crypto.verify(null, packageBytes, publicKey, signature);

    if (!verified) {
        throw new Error(
            `${description} does not match the version published to ${registry.url}. `
            + 'The package may have been tampered with, or did not come from that registry.'
        );
    }
    console.log(`✅  This package is identical to ${description} as published to ${registry.url}.`);
}

/**
 * `registry.getMetadata` always returns the latest version. Follow `allVersions` to the exact
 * version recorded in the package's own manifest when it differs.
 */
async function resolveVersion(registry: Registry, extension: Extension, version: string): Promise<Extension> {
    if (extension.version === version) {
        return extension;
    }
    const versionUrl = extension.allVersions[version];
    if (!versionUrl) {
        throw new Error(`${extension.namespace}.${extension.name} has no published version '${version}'.`);
    }
    return registry.getJson(new URL(versionUrl));
}

async function downloadSignature(registry: Registry, url: string): Promise<Buffer> {
    const sigzipPath = getTempFilePath('.sigzip');
    try {
        await registry.download(sigzipPath, new URL(url));
        const entries = await readZip(sigzipPath, name => name === SIGNATURE_ENTRY);
        const signature = entries.get(SIGNATURE_ENTRY);
        if (!signature) {
            throw new Error(`The signature archive does not contain a '${SIGNATURE_ENTRY}' entry.`);
        }
        return signature;
    } finally {
        await fs.promises.rm(sigzipPath, { force: true });
    }
}

async function downloadPublicKey(registry: Registry, url: string): Promise<string> {
    const publicKeyPath = getTempFilePath('.pem');
    try {
        await registry.download(publicKeyPath, new URL(url));
        return await fs.promises.readFile(publicKeyPath, 'utf-8');
    } finally {
        await fs.promises.rm(publicKeyPath, { force: true });
    }
}
