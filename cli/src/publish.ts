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

    // Shared by every target/package below, rather than one per artifact: besides saving the extra
    // `/api/version` round trips, it's also where the registry's reported version is cached (see
    // Registry.tokenQuery), so a wide fan-out doesn't repeat that lookup once per target either.
    const registry = new Registry(options);
    const maxExtensionSize = await getMaxExtensionSize(registry);

    const internalPublishOptions: InternalPublishOptions[] = [];
    const packagePaths = options.packagePath || [undefined];
    const targets = options.targets || [undefined];
    for (const packagePath of packagePaths) {
        for (const target of targets) {
            internalPublishOptions.push({ ...options, packagePath: packagePath, target: target, maxExtensionSize });
        }
    }

    return Promise.allSettled(internalPublishOptions.map(publishOptions => doPublish(registry, publishOptions)));
}

/**
 * Looks up the registry's default extension size limit, so an oversized package can be flagged
 * before the upload instead of after transferring the whole payload.
 *
 * Best-effort: registries that don't expose `/api/version`, or don't report a limit, return
 * `undefined` here, and nothing is said about the size.
 */
async function getMaxExtensionSize(registry: Registry): Promise<number | undefined> {
    try {
        return (await registry.getRegistryVersion()).maxExtensionSize || undefined;
    } catch {
        return undefined;
    }
}

async function doPublish(registry: Registry, options: InternalPublishOptions = {}): Promise<void> {
    // if the packagePath is a link to a vsix, don't need to package it
    if (options.packagePath?.endsWith('.vsix')) {
        options.extensionFile = options.packagePath;
        delete options.packagePath;
        delete options.target;
    }
    if (!options.extensionFile) {
        await packageExtension(options, registry);
        console.log(); // new line
    } else if (options.preRelease) {
        console.warn("Ignoring option '--pre-release' for prepackaged extension.");
    }

    await warnIfAboveSizeLimit(options.extensionFile!, options.maxExtensionSize, registry.url);

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
 * Warns when the packaged extension exceeds the size limit the registry reports, so an upload that
 * is likely to be refused says so before transferring the whole file.
 *
 * Advisory only. `/api/version` has no namespace context, so the limit it reports is the registry
 * default; a namespace or extension override can allow more. Refusing here would block uploads the
 * server would have accepted, so the upload proceeds either way and the server's 413 is what
 * decides. `maxSize` is `undefined` when the limit couldn't be determined.
 */
async function warnIfAboveSizeLimit(extensionFile: string, maxSize: number | undefined, registryUrl: string): Promise<void> {
    if (!maxSize) {
        return;
    }

    const { size } = await fs.promises.stat(extensionFile);
    if (size > maxSize) {
        console.warn(
            `The extension package (${formatBytes(size)}) exceeds the default size limit of ${formatBytes(maxSize)} `
            + `reported by the registry at ${registryUrl}. Publishing anyway: the namespace may have a higher `
            + `limit configured, and the registry decides.`
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
     * The registry's default extension size limit in bytes, looked up once via `/api/version` and
     * shared across every target/package being published. Advisory: a namespace override can allow
     * more. `undefined` when it couldn't be determined.
     */
    maxExtensionSize?: number;
}
