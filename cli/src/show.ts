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

import * as semver from 'semver';
import { Extension, Registry, VersionReference } from './registry';
import { formatCount, formatNumber, printRows } from './table';
import { ShowOptions } from './show-options';
import { addEnvOptions, matchExtensionId } from './util';

/**
 * How many versions the history table prints unless `--all-versions` is given. Matches what
 * `vsce show` lists; the remainder is reported as a count rather than dropped silently.
 */
const VERSION_HISTORY_SIZE = 6;

/**
 * Tags starting with `__` are internal bookkeeping (e.g. `__web_extension`) rather than anything
 * the publisher wrote, and the web UI hides them too.
 */
const INTERNAL_TAG_PREFIX = '__';

/** One row of the version history table. */
interface VersionSummary {
    version: string;
    targetPlatforms: string[];
}

/**
 * Page size for the version-reference listing. Versions come back newest first, and each version
 * contributes one entry per target platform, so a page has to be comfortably larger than the
 * number of versions shown for the default table to be filled from a single request.
 */
const VERSION_PAGE_SIZE = 100;

/**
 * Prints an extension's metadata.
 */
export async function show(options: ShowOptions): Promise<void> {
    addEnvOptions(options);
    const { id, version } = splitVersion(options.extensionId);
    const match = matchExtensionId(id);
    if (!match) {
        throw new Error('The extension identifier must have the form `namespace.extension` or `namespace/extension`.');
    }

    const [, namespace, name] = match;
    const registry = new Registry(options);
    const extension = await registry.getMetadata(namespace, name, options.target, version);
    if (extension.error) {
        throw new Error(extension.error);
    }

    if (options.json) {
        console.log(JSON.stringify(extension, null, 4));
        return;
    }

    const allVersions = options.allVersions === true;
    const versions = await getVersions(registry, namespace, name, options.target, allVersions);
    printSummary(extension, versions, allVersions);
}

/**
 * Splits a trailing `@<version>` off the identifier, the way `vsce show` accepts it. Only the last
 * `@` is considered, so it stays out of the way of the identifier itself.
 *
 * An `@` with nothing after it is rejected rather than read as "no version given". It usually means a
 * shell variable that did not expand - `ovsx show redhat.java@$VERSION` with `VERSION` unset - and
 * reporting the latest version for that would answer a question the caller did not ask.
 */
function splitVersion(extensionId: string): { id: string; version?: string } {
    const at = extensionId.lastIndexOf('@');
    if (at <= 0) {
        return { id: extensionId };
    }
    const version = extensionId.substring(at + 1);
    if (version.length === 0) {
        throw new Error('A version must follow `@`, as in `namespace.extension@1.2.3`.');
    }
    return { id: extensionId.substring(0, at), version };
}

/**
 * Collects the version history, collapsing the one-entry-per-target-platform rows the registry
 * returns into a single row per version. Fetches one page unless every version is wanted, in which
 * case it pages to the end - `totalSize` counts version/target-platform pairs, not versions, so
 * paging has to run on what has actually been returned rather than on a version count.
 *
 * Best-effort: a registry that doesn't serve this endpoint still gets everything else, just
 * without the history table.
 */
async function getVersions(
    registry: Registry,
    namespace: string,
    name: string,
    target: string | undefined,
    allVersions: boolean
): Promise<VersionSummary[]> {
    const references: VersionReference[] = [];
    let offset = 0;
    try {
        for (;;) {
            const page = await registry.getVersionReferences(namespace, name, target, VERSION_PAGE_SIZE, offset);
            if (page.error) {
                return [];
            }
            const versions = page.versions ?? [];
            references.push(...versions);
            offset += versions.length;
            if (!allVersions || versions.length === 0 || offset >= page.totalSize) {
                break;
            }
        }
    } catch {
        return [];
    }

    const byVersion = new Map<string, VersionSummary>();
    for (const reference of references) {
        let summary = byVersion.get(reference.version);
        if (!summary) {
            summary = { version: reference.version, targetPlatforms: [] };
            byVersion.set(reference.version, summary);
        }
        if (reference.targetPlatform && !summary.targetPlatforms.includes(reference.targetPlatform)) {
            summary.targetPlatforms.push(reference.targetPlatform);
        }
    }

    return [...byVersion.values()].sort(byNewestFirst);
}

/**
 * Newest version first. Anything that isn't valid semver (the registry accepts such versions from
 * a mirror) sorts after everything that is, rather than being compared incomparably.
 *
 * Deliberately `valid` and not `coerce`: coerce drops prerelease identifiers, so `1.2.0-alpha.1`
 * and `1.2.0` would compare equal and order unstably, and it accepts inputs semver itself rejects
 * (`v1.2` becomes `1.2.0`), which would defeat the fallback below.
 */
function byNewestFirst(a: VersionSummary, b: VersionSummary): number {
    const left = semver.valid(a.version);
    const right = semver.valid(b.version);
    if (left && right) {
        return semver.rcompare(left, right);
    }
    if (left) {
        return -1;
    }
    if (right) {
        return 1;
    }
    return b.version.localeCompare(a.version);
}

function printSummary(extension: Extension, versions: VersionSummary[], allVersions: boolean): void {
    const publisher = extension.namespaceDisplayName || extension.namespace;
    console.log(extension.displayName || extension.name);
    console.log(`  ${publisher}${extension.verified ? ' (verified publisher)' : ''}`);
    console.log(`  ${formatCount(extension.downloadCount, 'download')}  ${formatRating(extension)}`);
    if (extension.description) {
        console.log();
        console.log(`  ${extension.description}`);
    }

    printNotices(extension);
    printVersionHistory(versions, allVersions);
    printList('Categories', extension.categories);
    printList('Tags', extension.tags?.filter(tag => !tag.startsWith(INTERNAL_TAG_PREFIX)));
    printTable('More Info', moreInfo(extension));
    printTable('Registry', registryInfo(extension));
    printTable('Statistics', statistics(extension));
}

/**
 * Anything a consumer of this extension should see before its metadata: deprecation first, since
 * it changes whether they should install it at all.
 */
function printNotices(extension: Extension): void {
    const notices: string[] = [];
    if (extension.deprecated) {
        const replacement = extension.replacement;
        notices.push(
            replacement
                ? `Deprecated - superseded by ${replacement.displayName ?? replacement.url}`
                : 'Deprecated'
        );
    }
    if (extension.downloadable === false) {
        notices.push('Not downloadable from this registry');
    }
    if (extension.preview) {
        notices.push('Preview');
    }
    if (extension.namespaceOwnershipConflict) {
        notices.push('The namespace ownership of this extension is disputed');
    }

    if (notices.length > 0) {
        console.log();
        for (const notice of notices) {
            console.log(`  ! ${notice}`);
        }
    }
}

function printVersionHistory(versions: VersionSummary[], allVersions: boolean): void {
    if (versions.length === 0) {
        return;
    }

    const shown = allVersions ? versions : versions.slice(0, VERSION_HISTORY_SIZE);
    const rows = shown.map(v => [v.version, v.targetPlatforms.join(', ')]);

    console.log();
    console.log('Version History:');
    printRows([['Version', 'Target Platforms'], ...rows]);
    const remaining = versions.length - shown.length;
    if (remaining > 0) {
        console.log(`  ... and ${remaining} more (pass --all-versions to list them)`);
    }
}

function moreInfo(extension: Extension): string[][] {
    const rows: string[][] = [
        ['Unique Identifier', `${extension.namespace}.${extension.name}`],
        ['Version', extension.version]
    ];
    if (extension.versionAlias?.length > 0) {
        rows.push(['Version Aliases', extension.versionAlias.join(', ')]);
    }
    if (extension.targetPlatform) {
        rows.push(['Target Platform', extension.targetPlatform]);
    }
    rows.push(['Last Updated', extension.timestamp]);
    rows.push(['Published By', extension.publishedBy?.loginName ?? '']);
    addIfPresent(rows, 'License', extension.license);
    addIfPresent(rows, 'Homepage', extension.homepage);
    addIfPresent(rows, 'Repository', extension.repository);
    addIfPresent(rows, 'Bugs', extension.bugs);
    addIfPresent(rows, 'Q&A', extension.qna);
    addIfPresent(rows, 'Sponsor', extension.sponsorLink);
    if (extension.engines) {
        rows.push(['Engines', Object.entries(extension.engines).map(([e, v]) => `${e} ${v}`).join(', ')]);
    }
    return rows;
}

/**
 * The parts an Open VSX compatible registry reports that the Marketplace has no equivalent for.
 * Kept in its own block rather than mixed into "More Info" so it's obvious what is registry
 * specific, and omitted entirely when none of it applies.
 */
function registryInfo(extension: Extension): string[][] {
    const rows: string[][] = [];
    if (extension.verified !== undefined) {
        rows.push(['Verified Publisher', yesNo(extension.verified)]);
    }
    if (extension.publishedWithTrustedPublishing) {
        rows.push(['Trusted Publishing', 'yes']);
    }
    if (extension.preRelease !== undefined) {
        rows.push(['Pre-Release', yesNo(extension.preRelease)]);
    }
    addIfPresent(rows, 'Extension Kind', extension.extensionKind?.join(', '));
    addIfPresent(rows, 'Localized', extension.localizedLanguages?.join(', '));
    addIfPresent(rows, 'Dependencies', extension.dependencies?.map(referenceId).join(', '));
    addIfPresent(rows, 'Bundled Extensions', extension.bundledExtensions?.map(referenceId).join(', '));
    return rows;
}

function statistics(extension: Extension): string[][] {
    const rows: string[][] = [['Downloads', formatNumber(extension.downloadCount)]];
    if (extension.averageRating !== undefined) {
        rows.push(['Average Rating', `${extension.averageRating.toFixed(1)}/5`]);
    }
    rows.push(['Reviews', formatNumber(Number(extension.reviewCount ?? 0))]);
    return rows;
}

function referenceId(reference: { namespace: string; extension: string }): string {
    return `${reference.namespace}.${reference.extension}`;
}

function addIfPresent(rows: string[][], label: string, value?: string): void {
    if (value) {
        rows.push([label, value]);
    }
}

function printList(title: string, values?: string[]): void {
    if (!values || values.length === 0) {
        return;
    }
    console.log();
    console.log(`${title}:`);
    console.log(`  ${values.join(', ')}`);
}

function printTable(title: string, rows: string[][]): void {
    if (rows.length === 0) {
        return;
    }
    console.log();
    console.log(`${title}:`);
    printRows(rows);
}

function formatRating(extension: Extension): string {
    if (extension.averageRating === undefined) {
        return 'no ratings';
    }
    return `${extension.averageRating.toFixed(1)}/5 from ${formatCount(extension.reviewCount, 'review')}`;
}

function yesNo(value: boolean): string {
    return value ? 'yes' : 'no';
}
