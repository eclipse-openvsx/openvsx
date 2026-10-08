/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import * as crypto from 'crypto';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as http from 'http';
import { RegistryOptions } from './registry-options';
import { TrustedPublishingOptions } from './trusted-publishing-options';

export { promisify } from 'util';

export function addEnvOptions(options: RegistryOptions): void {
    options.registryUrl ??= process.env.OVSX_REGISTRY_URL;
    options.pat ??= process.env.OVSX_PAT;
    options.username ??= process.env.OVSX_USERNAME;
    options.password ??= process.env.OVSX_PASSWORD;
    options.timeout ??= parseIntEnv(process.env.OVSX_TIMEOUT);
}

export function addTrustedPublishingEnvOptions(options: TrustedPublishingOptions): void {
    options.trustedPublishing ??= parseBooleanEnv(process.env.OVSX_TRUSTED_PUBLISHING);
    options.idToken ??= process.env.OVSX_ID_TOKEN;
    options.oidcAudience ??= process.env.OVSX_OIDC_AUDIENCE;
}

/** How long a request may make no progress before it is abandoned; see RegistryOptions.timeout. */
export const DEFAULT_TIMEOUT = 30_000;

/**
 * The configured request timeout, for requests that have no RegistryOptions to read it from - the CI
 * token exchange deliberately shares nothing with the registry, but there is no reason for it to be
 * on a different clock.
 */
export function configuredTimeout(): number {
    return parseIntEnv(process.env.OVSX_TIMEOUT) ?? DEFAULT_TIMEOUT;
}

function parseIntEnv(value?: string): number | undefined {
    if (value === undefined || value.trim().length === 0) {
        return undefined;
    }
    const parsed = Number(value.trim());
    // Left undefined rather than throwing, so a mistyped variable falls back to the default instead
    // of stopping a command that would otherwise work.
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : undefined;
}

function parseBooleanEnv(value?: string): boolean | undefined {
    if (value === undefined || value.trim().length === 0) {
        return undefined;
    }
    return ['true', '1', 'yes'].includes(value.trim().toLowerCase());
}

/**
 * Parses a commander option that must be a non-negative whole number, so a typo is reported by the
 * CLI rather than sent to the registry as a bad request.
 */
export function parseNonNegativeInt(value: string): number {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 0) {
        throw new Error(`Expected a non-negative whole number, got '${value}'.`);
    }
    return parsed;
}

export function matchExtensionId(id: string): RegExpExecArray | null {
    return /^([\w-]+)(?:\.|\/)([\w-]+)$/.exec(id);
}

/**
 * Levenshtein distance between `a` and `b`: the minimum number of single-character insertions,
 * deletions or substitutions needed to turn one into the other. Used to suggest the closest known
 * command for a typo.
 */
export function levenshtein(a: string, b: string): number {
    if (a === b) {
        return 0;
    }
    // The distance is symmetric, so keep the shorter string in `b` to bound the row size.
    if (b.length > a.length) {
        [a, b] = [b, a];
    }
    // A single row of the edit distance matrix, seeded with the distance between the empty prefix
    // of `a` and every prefix of `b`.
    const row = Array.from({ length: b.length + 1 }, (_, j) => j);
    for (let i = 1; i <= a.length; i++) {
        let diagonal = row[0];
        row[0] = i;
        for (let j = 1; j <= b.length; j++) {
            const above = row[j];
            row[j] = Math.min(row[j - 1] + 1, above + 1, diagonal + (a[i - 1] === b[j - 1] ? 0 : 1));
            diagonal = above;
        }
    }
    return row[b.length];
}

export function optionalStat(path: fs.PathLike): Promise<fs.Stats | undefined> {
    return new Promise((resolve, reject) => {
        fs.stat(path, (err, stats) => resolve(stats));
    });
}

export function makeDirs(path: fs.PathLike): Promise<void> {
    return new Promise((resolve, reject) => {
        if (fs.existsSync(path)) {
            resolve();
        } else {
            fs.mkdir(path, { recursive: true }, (err: NodeJS.ErrnoException | null) => {
                if (err)
                    reject(err);
                else
                    resolve();
            });
        }
    });
}

/**
 * Returns a random, not-yet-created file path ending in `postfix`, directly in the OS temp directory.
 */
export function getTempFilePath(postfix = ''): string {
    return path.join(os.tmpdir(), `ovsx-${crypto.randomUUID()}${postfix}`);
}

export function rejectError(err: any) {
    const reason = err instanceof Error ? err : new Error(String(err));
    return Promise.reject(reason);
}

export function handleError(debug?: boolean, additionalMessage?: string, exit: boolean = true): (reason: any) => void {
    return reason => {
        if (reason instanceof Error && !debug) {
            console.error(`\u274c  ${reason.message}`);
            if (additionalMessage) {
                console.error(additionalMessage);
            }
        } else if (typeof reason === 'string') {
            console.error(`\u274c  ${reason}`);
        } else if (reason !== undefined) {
            console.error(reason);
        } else {
            console.error('An unknown error occurred.');
        }

        if (exit) {
            process.exit(1);
        }
    };
}

/**
 * Formats a byte count for display, e.g. `1536` -> `1.5 KB`. Mirrors the registry's own
 * `FileUtils.byteCountToDisplaySize` formatting so client- and server-side size limit messages read
 * the same way.
 */
export function formatBytes(bytes: number): string {
    const units = ['bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB'];
    let size = bytes;
    let unit = 0;
    while (size >= 1024 && unit < units.length - 1) {
        size /= 1024;
        unit++;
    }
    return `${Math.floor(size)} ${units[unit]}`;
}

/**
 * An error carrying the HTTP status that produced it, so a caller can tell "the registry refused this"
 * from "the registry could not answer right now" without matching on the message.
 */
export interface StatusError extends Error {
    status?: number;
}

export function withStatus(error: Error, status?: number): StatusError {
    const withStatusCode = error as StatusError;
    withStatusCode.status = status;
    return withStatusCode;
}

/**
 * A URL in the form it can safely be printed: no user info and no query string.
 *
 * The personal access token travels in a header now, but `createNamespace`, `verifyPat`, `publish`
 * and `delete` still carry it in the query string too when talking to a registry too old to resolve
 * it from the header (see `Registry.tokenQuery`, #1344). An error message goes straight to stderr
 * and from there into CI logs, and the query carries nothing an error needs - so it stays dropped
 * whole rather than filtered key by key, a filter having to be kept in step with every parameter
 * anyone ever adds.
 */
export function redactUrl(url: URL | string): string {
    try {
        const parsed = typeof url === 'string' ? new URL(url) : url;
        return `${parsed.protocol}//${parsed.host}${parsed.pathname}`;
    } catch {
        // Not parseable, so nothing can be assumed about what it holds.
        return '<url>';
    }
}

export function statusError(response: http.IncomingMessage): StatusError {
    const message = response.statusMessage
        ? `The server responded with status ${response.statusCode}: ${response.statusMessage}`
        : `The server responded with status ${response.statusCode}.`;
    return withStatus(new Error(message), response.statusCode);
}

export function readFile(name: string, packagePath?: string, encoding: BufferEncoding = 'utf-8'): Promise<string> {
    return new Promise((resolve, reject) => {
        fs.readFile(
            path.join(packagePath ?? process.cwd(), name),
            { encoding },
            (err: NodeJS.ErrnoException | null, content: string) => {
                if (err) {
                    reject(err);
                } else {
                    resolve(content);
                }
            }
        );
    });
}

export async function readManifest(packagePath?: string): Promise<Manifest> {
    const content = await readFile('package.json', packagePath);
    return JSON.parse(content);
}

export function validateManifest(manifest: Manifest): void {
    if (!manifest.publisher) {
        throw new Error("Missing required field 'publisher'.");
    }
    if (!manifest.name) {
        throw new Error("Missing required field 'name'.");
    }
    if (!manifest.version) {
        throw new Error("Missing required field 'version'.");
    }
}

export function writeFile(name: string, content: string, packagePath?: string, encoding: BufferEncoding = 'utf-8'): Promise<void> {
    return new Promise((resolve, reject) => {
        fs.writeFile(
            path.join(packagePath ?? process.cwd(), name),
            content,
            { encoding },
            (err: NodeJS.ErrnoException | null) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            }
        );
    });
}

export function writeManifest(manifest: Manifest, packagePath?: string): Promise<void> {
    const content = JSON.stringify(manifest, null, 4);
    return writeFile('package.json', content, packagePath);
}

export interface Manifest {
    publisher: string;
    name: string;
    version: string;
    license?: string;
}
