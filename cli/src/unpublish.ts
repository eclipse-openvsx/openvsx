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

import { confirm } from '@inquirer/prompts';
// `import * as isCI from 'is-ci'` would bind to a namespace object instead of the boolean `is-ci`
// exports whenever the importer applies ES module interop (e.g. a bundler, or vitest's own
// transform), making the truthiness check below always true. `import = require` always binds
// directly to the exported value, regardless of how the importing tool handles interop.
// eslint-disable-next-line @typescript-eslint/no-require-imports
import isCI = require('is-ci');
import * as semver from 'semver';
import { getPAT } from './pat';
import { Registry, TargetPlatformVersion } from './registry';
import { UnpublishOptions } from './unpublish-options';
import { addEnvOptions, matchExtensionId, readManifest } from './util';

/**
 * Oldest registry version that exposes the `delete` endpoint used by this command.
 */
export const MIN_UNPUBLISH_REGISTRY_VERSION = '1.2.0';

/**
 * Deletes an extension or some of its versions from the registry.
 */
export async function unpublish(options: UnpublishOptions = {}): Promise<void> {
    addEnvOptions(options);
    if (options.targets && !options.versions) {
        throw new Error("Please specify the versions to delete with '--versions' when using '--target'.");
    }

    const extensionId = options.extensionId ?? await readExtensionId();
    const match = matchExtensionId(extensionId);
    if (!match) {
        throw new Error('The extension identifier must have the form `namespace.extension` or `namespace/extension`.');
    }

    const [, namespace, extension] = match;
    const registry = new Registry(options);
    await ensureUnpublishSupported(registry);

    options.pat = await getPAT(namespace, { ...options, namespace }, false);

    const targetVersions = getTargetVersions(options);
    await confirmUnpublish(`${namespace}.${extension}`, targetVersions, registry.url, options.force);

    const result = await registry.deleteExtension(namespace, extension, targetVersions, options.pat);
    if (result.error) {
        throw new Error(result.error);
    }
    // the registry reports one line per deleted version, and nothing at all if there was nothing to delete
    console.log(`🗑  ${result.success || `Nothing to delete for ${namespace}.${extension}`}`);
}

/**
 * Reads the extension identifier from the `package.json` in the current directory,
 * so that `ovsx unpublish` can be run from an extension folder without arguments.
 */
async function readExtensionId(): Promise<string> {
    let error;
    try {
        const manifest = await readManifest();
        if (manifest.publisher && manifest.name) {
            return `${manifest.publisher}.${manifest.name}`;
        }
    } catch (e) {
        error = e;
    }

    throw new Error(
        'Unable to read the extension identifier. Please supply it as an argument or run ovsx from the extension folder.' +
        (error ? `\n\n${error}` : '')
    );
}

/**
 * Fails fast with an actionable message when the registry is known to be too old to support
 * deleting extensions, rather than letting the delete request fail with a generic 404.
 *
 * The check is best-effort: registries that don't expose `/api/version` at all, or report a
 * version that can't be parsed as semver (e.g. a build without `ovsx.registry.version` set),
 * are assumed to support the endpoint. The delete request itself will surface any real
 * incompatibility in that case.
 */
async function ensureUnpublishSupported(registry: Registry): Promise<void> {
    let reportedVersion: string | undefined;
    try {
        reportedVersion = (await registry.getRegistryVersion()).version;
    } catch {
        return;
    }

    // `coerce` drops any prerelease/build suffix (e.g. `1.2.0-dev.0` or `1.2.0-SNAPSHOT` both
    // become `1.2.0`), so development builds of a supported release compare as supported too.
    const version = semver.coerce(reportedVersion);
    if (version && semver.lt(version, MIN_UNPUBLISH_REGISTRY_VERSION)) {
        throw new Error(
            `The registry at ${registry.url} runs version ${reportedVersion}, but deleting extensions requires `
            + `version ${MIN_UNPUBLISH_REGISTRY_VERSION} or later.`
        );
    }
}

/**
 * Returns the versions to delete, or `undefined` to delete the extension as a whole.
 * A version without target platform tells the registry to delete all target platforms
 * of that version.
 */
function getTargetVersions(options: UnpublishOptions): TargetPlatformVersion[] | undefined {
    if (!options.versions) {
        return undefined;
    }
    if (!options.targets) {
        return options.versions.map(version => ({ version }));
    }

    return options.versions.flatMap(
        version => options.targets!.map(targetPlatform => ({ version, targetPlatform }))
    );
}

async function confirmUnpublish(
    extensionId: string,
    targetVersions: TargetPlatformVersion[] | undefined,
    registryUrl: string,
    force?: boolean
): Promise<void> {
    if (force) {
        return;
    }
    if (isCI || !process.stdin.isTTY) {
        throw new Error("Aborted. Use '--force' to delete without confirmation.");
    }

    const what = targetVersions
        ? targetVersions.map(target => describe(extensionId, target)).join(', ')
        : `all versions of ${extensionId}`;
    const confirmed = await confirm({
        message: `Delete ${what} from ${registryUrl}? Deleted versions can never be published again.`,
        default: false
    });

    if (!confirmed) {
        throw new Error('Aborted.');
    }
}

function describe(extensionId: string, target: TargetPlatformVersion): string {
    const version = `${extensionId} v${target.version}`;
    return target.targetPlatform ? `${version}@${target.targetPlatform}` : version;
}
