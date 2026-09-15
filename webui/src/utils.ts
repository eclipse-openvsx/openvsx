/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ErrorResponse, ProblemDetail } from './server-request';

export function addQuery(url: string, queries: { key: string; value: string | number | undefined }[]): string {
    const nonEmpty = queries.filter(q => q.value !== undefined);
    if (nonEmpty.length === 0) {
        return url;
    }
    return url + '?' + nonEmpty.map<string>(q => q.key + '=' + encodeURIComponent(q.value!)).join('&');
}

export function createAbsoluteURL(
    arr: string[],
    queries?: { key: string; value: string | number | undefined }[]
): string {
    const url = arr.length === 0 ? '' : arr.reduce((prev, curr) => prev + '/' + curr);
    if (queries && queries.length > 0) {
        return addQuery(url, queries);
    } else {
        return url;
    }
}

export function createRoute(arr: string[], queries?: { key: string; value: string | number }[]): string {
    const url = createAbsoluteURL(arr, queries);
    return url.startsWith('/') ? url : '/' + url;
}

export function debounce(task: () => void, token: { timeout?: number }, delay: number = 150): void {
    clearTimeout(token.timeout);
    token.timeout = window.setTimeout(task, delay);
}

const compactNumberFormat = new Intl.NumberFormat(undefined, {
    notation: 'compact',
    compactDisplay: 'short'
} as Intl.NumberFormatOptions);

/** Formats large counts compactly, e.g. 12345 -> "12K". */
export function formatCompactNumber(value: number): string {
    return compactNumberFormat.format(value);
}

const ratingFormat = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
});

/** Formats a star rating to one decimal, e.g. 4.25 -> "4.3". Unlike toFixed, which always emits a
 *  '.', this follows the viewer's locale like every other number on a card does. */
export function formatRating(value: number): string {
    return ratingFormat.format(value);
}

const FILE_SIZE_UNITS = ['B', 'kB', 'MB', 'GB', 'TB'];

/** Formats a byte count for humans, e.g. 5242880 -> "5.00 MB". */
export function formatFileSize(bytes: number): string {
    let value = bytes;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < FILE_SIZE_UNITS.length - 1) {
        value /= 1024;
        unitIndex++;
    }
    const formatted =
        unitIndex === 0
            ? value.toString()
            : value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${formatted} ${FILE_SIZE_UNITS[unitIndex]}`;
}

export function toLocalTime(timestamp?: string): string | undefined {
    if (!timestamp) {
        return undefined;
    }
    const date = new Date(timestamp);
    const options: Intl.DateTimeFormatOptions = {
        year: 'numeric',
        month: 'numeric',
        day: 'numeric',
        hour: 'numeric',
        minute: 'numeric'
    };
    return new Intl.DateTimeFormat(undefined, options).format(date);
}

const msPerSecond = 1000;
const msPerMinute = msPerSecond * 60;
const msPerHour = msPerMinute * 60;
const msPerDay = msPerHour * 24;
const msPerMonth = msPerDay * 30.4;
const msPerYear = msPerDay * 365;
export function toRelativeTime(timestamp?: string, isFutureTime: boolean = false): string | undefined {
    if (!timestamp) {
        return undefined;
    }
    const date = new Date(timestamp);
    const elapsed = Date.now() - date.getTime();

    if (isFutureTime) {
        const remaining = -elapsed;
        if (remaining < 0) {
            return 'now';
        } else if (remaining < msPerMinute) {
            const value = Math.round(remaining / msPerSecond);
            return `in ${value} second${value !== 1 ? 's' : ''}`;
        } else if (remaining < msPerHour) {
            const value = Math.round(remaining / msPerMinute);
            return `in ${value} minute${value !== 1 ? 's' : ''}`;
        } else if (remaining < msPerDay) {
            const value = Math.round(remaining / msPerHour);
            return `in ${value} hour${value !== 1 ? 's' : ''}`;
        } else if (remaining < msPerMonth) {
            const value = Math.round(remaining / msPerDay);
            return `in ${value} day${value !== 1 ? 's' : ''}`;
        } else if (remaining < msPerYear) {
            const value = Math.round(remaining / msPerMonth);
            return `in ${value} month${value !== 1 ? 's' : ''}`;
        } else {
            const value = Math.round(remaining / msPerYear);
            return `in ${value} year${value !== 1 ? 's' : ''}`;
        }
    } else {
        if (elapsed < msPerMinute) {
            return 'now';
        } else if (elapsed < msPerHour) {
            const value = Math.round(elapsed / msPerMinute);
            return `${value} minute${value !== 1 ? 's' : ''} ago`;
        } else if (elapsed < msPerDay) {
            const value = Math.round(elapsed / msPerHour);
            return `${value} hour${value !== 1 ? 's' : ''} ago`;
        } else if (elapsed < msPerMonth) {
            const value = Math.round(elapsed / msPerDay);
            return `${value} day${value !== 1 ? 's' : ''} ago`;
        } else if (elapsed < msPerYear) {
            const value = Math.round(elapsed / msPerMonth);
            return `${value} month${value !== 1 ? 's' : ''} ago`;
        } else {
            const value = Math.round(elapsed / msPerYear);
            return `${value} year${value !== 1 ? 's' : ''} ago`;
        }
    }
}

/** Short form of {@link toRelativeTime} for tight layouts: "5m", "3h", "2d", "4mo", "1y". */
export function toCompactRelativeTime(timestamp?: string): string | undefined {
    if (!timestamp) {
        return undefined;
    }
    const elapsed = Date.now() - new Date(timestamp).getTime();
    if (elapsed < msPerMinute) {
        return 'now';
    } else if (elapsed < msPerHour) {
        return `${Math.round(elapsed / msPerMinute)}m`;
    } else if (elapsed < msPerDay) {
        return `${Math.round(elapsed / msPerHour)}h`;
    } else if (elapsed < msPerMonth) {
        return `${Math.round(elapsed / msPerDay)}d`;
    } else if (elapsed < msPerYear) {
        return `${Math.round(elapsed / msPerMonth)}mo`;
    } else {
        return `${Math.round(elapsed / msPerYear)}y`;
    }
}

/** Takes whatever was thrown or returned — callers rarely know the shape — and narrows it here. */
export function handleError(err?: unknown): string {
    if (err) {
        if (err instanceof Error && err.name === 'AbortError') {
            return '';
        }

        console.error(err);

        if (err instanceof Error) {
            if (err.message === 'Failed to fetch' || err.message === 'Unexpected token < in JSON at position 0') {
                return 'Something went wrong while fetching data. Please contact the site administrators.';
            }
            return `An unexpected error occurred: ${err.message}`;
        }
        // A failed request rejects with the parsed server body, not an Error.
        const body = (typeof err === 'object' ? err : {}) as Partial<ErrorResponse> & ProblemDetail;
        const { error, message } = body;
        if (error && message) {
            return `${error} (${message})`;
        } else if (error) {
            return error;
        } else if (message) {
            return message;
        }
        // No `error` means a problem+json body rather than a ResultJson one: parameter validation puts
        // the offending parameters in `errors`, other framework errors put prose in `detail`.
        if (body.errors?.length) {
            return [body.title, body.errors.join(', ')].filter(Boolean).join(': ');
        } else if (body.detail) {
            return body.title ? `${body.title}: ${body.detail}` : body.detail;
        } else if (body.title) {
            return body.title;
        }
    }
    return 'An unexpected error occurred.';
}

export interface Cookie {
    key: string;
    value: string;
    path?: string;
    domain?: string;
    maxAge?: number;
    expires?: string;
    secure?: boolean;
    samesite?: 'lax' | 'strict' | 'none';
}

export function setCookie(cookie: Cookie): void {
    let cookieString = `${cookie.key}=${encodeURIComponent(cookie.value)}`;
    if (cookie.path) {
        cookieString += `; path=${cookie.path}`;
    }
    if (cookie.domain) {
        cookieString += `; domain=${cookie.domain}`;
    }
    if (cookie.maxAge) {
        cookieString += `; max-age=${cookie.maxAge}`;
    }
    if (cookie.expires) {
        cookieString += `; expires=${cookie.expires}`;
    }
    if (cookie.secure) {
        cookieString += '; secure';
    }
    if (cookie.samesite) {
        cookieString += `; samesite=${cookie.samesite}`;
    }
    document.cookie = cookieString;
}

export function getCookieValueByKey(key: string): string | undefined {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${key}=`);
    if (parts.length === 2) {
        const value = parts[1].split(';')[0];
        return decodeURIComponent(value);
    }

    return undefined;
}

namespace TargetPlatform {
    export const WIN32_IA32 = 'win32-ia32';
    export const WIN32_X64 = 'win32-x64';
    export const WIN32_ARM64 = 'win32-arm64';
    export const LINUX_X64 = 'linux-x64';
    export const LINUX_ARM64 = 'linux-arm64';
    export const LINUX_ARMHF = 'linux-armhf';
    export const ALPINE_X64 = 'alpine-x64';
    export const ALPINE_ARM64 = 'alpine-arm64';
    export const DARWIN_X64 = 'darwin-x64';
    export const DARWIN_ARM64 = 'darwin-arm64';
    export const WEB = 'web';
    export const UNIVERSAL = 'universal';
}

export function getTargetPlatforms(): string[] {
    return [
        TargetPlatform.WIN32_IA32,
        TargetPlatform.WIN32_X64,
        TargetPlatform.WIN32_ARM64,
        TargetPlatform.LINUX_X64,
        TargetPlatform.LINUX_ARM64,
        TargetPlatform.LINUX_ARMHF,
        TargetPlatform.ALPINE_X64,
        TargetPlatform.ALPINE_ARM64,
        TargetPlatform.DARWIN_X64,
        TargetPlatform.DARWIN_ARM64,
        TargetPlatform.WEB,
        TargetPlatform.UNIVERSAL
    ];
}

export function getTargetPlatformDisplayName(targetPlatform: string): string {
    const targetPlatformDisplayNames = new Map([
        [TargetPlatform.UNIVERSAL, 'Universal'],
        [TargetPlatform.WIN32_IA32, 'Windows x86'],
        [TargetPlatform.WIN32_X64, 'Windows x64'],
        [TargetPlatform.WIN32_ARM64, 'Windows ARM'],
        [TargetPlatform.LINUX_X64, 'Linux x64'],
        [TargetPlatform.LINUX_ARM64, 'Linux ARM64'],
        [TargetPlatform.LINUX_ARMHF, 'Linux ARMhf'],
        [TargetPlatform.ALPINE_X64, 'Alpine Linux 64 bit'],
        [TargetPlatform.ALPINE_ARM64, 'Alpine Linux ARM64'],
        [TargetPlatform.DARWIN_X64, 'macOS Intel'],
        [TargetPlatform.DARWIN_ARM64, 'macOS Apple Silicon'],
        [TargetPlatform.WEB, 'Web']
    ]);

    return targetPlatformDisplayNames.get(targetPlatform) ?? '';
}

export function getEngineDisplayName(engine: string): string {
    const engineDisplayNames = new Map([
        ['vscode', 'VS Code'],
        ['node', 'Node.js'],
        ['npm', 'npm'],
        ['pnpm', 'pnpm'],
        ['yarn', 'Yarn'],
        ['vsce', 'vsce'],
        ['sqlops', 'SQL Operation Studio'],
        ['azdata', 'Azure Data Studio'],
        ['theiaPlugin', 'Theia Plugin'],
        ['opensumi', 'OpenSumi'],
        ['nadako.vshaxe', 'nadako.vshaxe'],
        ['hbenl.vscode-test-explorer', 'hbenl.vscode-test-explorer'],
        ['positron', 'Positron']
    ]);

    return engineDisplayNames.get(engine) ?? '';
}
