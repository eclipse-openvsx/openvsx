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

export type ProviderKind = 'github' | 'gitlab' | 'generic';

// Groups the GitLab family ("gitlab", "eclipse-gitlab", ...) so icons and field help can be shared.
export const providerKind = (providerId: string): ProviderKind =>
    providerId === 'github' ? 'github' : providerId.includes('gitlab') ? 'gitlab' : 'generic';

// The server serves registration inputs unordered; impose a stable order.
const REGISTRATION_KEY_ORDER = [
    'owner',
    'repo',
    'repository_id',
    'owner_id',
    'namespace',
    'project',
    'project_id',
    'namespace_id',
    'workflow',
    'environment'
];

export const orderRegistrationKeys = (keys: string[]): string[] => {
    const rank = (key: string): number => {
        const index = REGISTRATION_KEY_ORDER.indexOf(key);
        return index === -1 ? Number.MAX_SAFE_INTEGER : index;
    };
    return [...keys].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
};

// Key pairs rendered as a combined "owner / repo" path when both are present.
export type PathPair = readonly [ownerKey: string, repoKey: string];

const REGISTRATION_PATH_PAIRS: readonly PathPair[] = [
    ['owner', 'repo'],
    ['namespace', 'project']
];

export const findRegistrationPathPair = (keys: string[]): PathPair | undefined => {
    const present = new Set(keys);
    return REGISTRATION_PATH_PAIRS.find(pair => pair.every(key => present.has(key)));
};
