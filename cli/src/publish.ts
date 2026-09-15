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
import { createVSIX, IPackageOptions } from '@vscode/vsce';
import { getPAT } from './pat';
import { createTempFile, addEnvOptions, addTrustedPublishingEnvOptions, formatBytes, StatusError } from './util';
import { Extension, Registry } from './registry';
import { checkLicense } from './check-license';
import { readVSIXPackage } from './zip';
import { PublishOptions, PublishCommonOptions } from './publish-options';
import { getTrustedPublishingToken, refreshTrustedPublishingToken, useTrustedPublishing } from './trusted-publishing';

/**
 * Publishes an extension.
 */
export async function publish(options: PublishOptions = {}): Promise<PromiseSettledResult<void>[]> {
    addEnvOptions(options);
    addTrustedPublishingEnvOptions(options);

    // Looked up once and shared by every target/package below, rather than once per artifact.
    const maxExtensionSize = await getMaxExtensionSize(new Registry(options));

    const internalPublishOptions: InternalPublishOptions[] = [];
    const packagePaths = options.packagePath || [undefined];
    const targets = options.targets || [undefined];
    for (const packagePath of packagePaths) {
        for (const target of targets) {
            internalPublishOptions.push({ ...options, packagePath: packagePath, target: target, maxExtensionSize });
        }
    }

    return Promise.allSettled(internalPublishOptions.map(publishOptions => doPublish(publishOptions)));
}

/**
 * Looks up the registry's configured extension size limit, so an oversized package can be rejected
 * locally before the upload instead of after transferring the whole payload.
 *
 * Best-effort: registries that don't expose `/api/version`, or don't report a limit, return
 * `undefined` here, and the upload proceeds to let the server enforce its own limit as before.
 */
async function getMaxExtensionSize(registry: Registry): Promise<number | undefined> {
    try {
        return (await registry.getRegistryVersion()).maxExtensionSize || undefined;
    } catch {
        return undefined;
    }
}

async function doPublish(options: InternalPublishOptions = {}): Promise<void> {
    // if the packagePath is a link to a vsix, don't need to package it
    if (options.packagePath?.endsWith('.vsix')) {
        options.extensionFile = options.packagePath;
        delete options.packagePath;
        delete options.target;
    }
    const registry = new Registry(options);
    if (!options.extensionFile) {
        await packageExtension(options, registry);
        console.log(); // new line
    } else if (options.preRelease) {
        console.warn("Ignoring option '--pre-release' for prepackaged extension.");
    }

    await ensureWithinSizeLimit(options.extensionFile!, options.maxExtensionSize, registry.url);

    // Set only when this publish obtained the token itself through trusted publishing, which is the one
    // case where a refusal can be answered by asking for a new token.
    let exchanged: { namespace: string; extension: string } | undefined;
    if (!options.pat) {
        const manifest = await readVSIXPackage(options.extensionFile!);
        if (useTrustedPublishing(options)) {
            exchanged = { namespace: manifest.publisher, extension: manifest.name };
            options.pat = await getTrustedPublishingToken(registry, manifest.publisher, manifest.name, options);
        } else {
            options.pat = await getPAT(manifest.publisher, options);
        }
    }

    let extension: Extension | undefined;
    try {
        extension = await doRegistryPublish(registry, options, exchanged);
    } catch (err) {
        if (options.skipDuplicate && err.message.endsWith('is already published.')) {
            console.log(err.message + ' Skipping publish.');
            return;
        } else {
            throw err;
        }
    }
    if (extension.error) {
        throw new Error(extension.error);
    }

    const name = `${extension.namespace}.${extension.name}`;
    let description = `${name} v${extension.version}`;
    if (extension.targetPlatform !== 'universal') {
        description += `@${extension.targetPlatform}`;
    }

    console.log(`\ud83d\ude80  Published ${description}`);
    if (extension.warning) {
        console.log(`\n!!  ${extension.warning}`);
    }
}

/**
 * Publishes the package, exchanging the ID token again if the registry refuses the trusted publishing
 * token this was using. The issued token is short-lived and shared by every target platform of a
 * release, so publishing a wide fan-out of large packages can outlive it and be refused partway
 * through - having authorised the release and then failing it over an expiry would be the wrong call.
 *
 * Only ever retried once, and only for a token obtained here: nothing this can do makes a token the
 * user supplied valid, and a second refusal means the token is not the problem.
 */
async function doRegistryPublish(
    registry: Registry,
    options: InternalPublishOptions,
    exchanged: { namespace: string; extension: string } | undefined
): Promise<Extension> {
    try {
        return await registry.publish(options.extensionFile!, options.pat!);
    } catch (err) {
        if (!exchanged || (err as StatusError)?.status !== 401) {
            throw err;
        }

        console.log('The registry refused the publishing token, requesting a new one');
        options.pat = await refreshTrustedPublishingToken(
            registry,
            exchanged.namespace,
            exchanged.extension,
            options,
            options.pat!
        );
        return registry.publish(options.extensionFile!, options.pat);
    }
}

/**
 * Fails fast with an actionable message when the packaged extension is already known to exceed the
 * registry's configured size limit, rather than uploading the whole file only to have the server
 * reject it. `maxSize` is `undefined` when the limit couldn't be determined, in which case the check
 * is skipped and the upload is left to the server to accept or reject.
 */
async function ensureWithinSizeLimit(extensionFile: string, maxSize: number | undefined, registryUrl: string): Promise<void> {
    if (!maxSize) {
        return;
    }

    const { size } = await fs.promises.stat(extensionFile);
    if (size > maxSize) {
        throw new Error(
            `The extension package (${formatBytes(size)}) exceeds the size limit of ${formatBytes(maxSize)} `
            + `accepted by the registry at ${registryUrl}.`
        );
    }
}

async function packageExtension(options: InternalPublishOptions, registry: Registry): Promise<void> {
    if (registry.requiresLicense) {
        await checkLicense(options.packagePath!);
    }

    options.extensionFile = await createTempFile({ postfix: '.vsix' });
    const packageOptions: IPackageOptions = {
        packagePath: options.extensionFile,
        target: options.target,
        cwd: options.packagePath,
        baseContentUrl: options.baseContentUrl,
        baseImagesUrl: options.baseImagesUrl,
        useYarn: options.yarn,
        followSymlinks: options.followSymlinks,
        dependencies: options.dependencies,
        preRelease: options.preRelease,
        allowMissingRepository: options.allowMissingRepository,
        version: options.packageVersion
    };
    await createVSIX(packageOptions);
}

// Interface used internally by the doPublish method
interface InternalPublishOptions extends PublishCommonOptions {

    /**
     * Only one target for our internal command.
     * Target architecture.
     */
    target?: string;

    /**
     * Only one path for our internal command.
     * Path to the extension to be packaged and published. Cannot be used together
     * with `extensionFile`.
     */
    packagePath?: string;

    /**
     * Whether to do dependency detection via npm or yarn
     */
    dependencies?: boolean;

    /**
     * The registry's configured extension size limit in bytes, looked up once via `/api/version` and
     * shared across every target/package being published. `undefined` when it couldn't be determined.
     */
    maxExtensionSize?: number;
}
