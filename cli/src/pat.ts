/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
import { password } from '@inquirer/prompts';
import { CreateNamespaceOptions } from './create-namespace-options';
import { PublishOptions } from './publish-options';
import { VerifyPatOptions } from './verify-pat-options';
import { DEFAULT_URL, Registry } from './registry';
import { RegistryOptions } from './registry-options';
import { Store, openDefaultStore } from './store';

export async function doVerifyPat(options: VerifyPatOptions) {
    const registry = new Registry(options);
    const namespace = options.namespace as string;
    const pat = options.pat as string;
    const result = await registry.verifyPat(namespace, pat);
    if (result.error) {
        throw new Error(result.error);
    }
    console.log(`\ud83d\ude80  PAT valid to publish at ${namespace}`);
}

export async function requestPAT(namespace: string, options: CreateNamespaceOptions | PublishOptions | VerifyPatOptions, verify: boolean = true): Promise<string> {
    const pat = await password({
        message: `Personal Access Token for namespace '${namespace}':`,
        mask: true,
        validate: value => value.trim().length > 0 || 'A personal access token is required.'
    });
    if (verify) {
        await doVerifyPat({ ...options, namespace, pat });
    }

    return pat;
}

/** The store key of a namespace's token, which is scoped to the registry host. */
export function patKey(namespace: string, options: RegistryOptions): string {
    // cross-keychain only accepts [a-zA-Z0-9._@-] in account names, so a port's ':' becomes '_'
    const host = new URL(options.registryUrl || DEFAULT_URL).host.replace(/[^\w.-]/g, '_');
    return `${namespace}@${host}`;
}

/** Keys stored before tokens were scoped to a registry, which are only trusted for the default registry. */
export function legacyPatKey(namespace: string, options: RegistryOptions): string | undefined {
    return new URL(options.registryUrl || DEFAULT_URL).host === new URL(DEFAULT_URL).host ? namespace : undefined;
}

export async function getStoredPAT(store: Store, namespace: string, options: RegistryOptions): Promise<string | undefined> {
    const stored = await store.get(patKey(namespace, options));
    const legacyKey = legacyPatKey(namespace, options);
    return stored ?? (legacyKey ? await store.get(legacyKey) : undefined);
}

export interface ResolvedPAT {
    pat: string;
    /** Stores a prompted token, warning instead of throwing; call it only once the registry has accepted the token. */
    save(): Promise<void>;
}

export async function getPAT(namespace: string, options: CreateNamespaceOptions | PublishOptions | VerifyPatOptions, verify: boolean = true): Promise<ResolvedPAT> {
    const nothingToSave = async () => undefined;
    if (options?.pat) {
        return { pat: options.pat, save: nothingToSave };
    }

    const store = await openDefaultStore();
    const stored = await getStoredPAT(store, namespace, options);
    if (stored) {
        return { pat: stored, save: nothingToSave };
    }

    const pat = await requestPAT(namespace, options, verify);
    // a storage failure must not turn a completed registry operation into a reported failure
    const save = async () => {
        try {
            await store.add(patKey(namespace, options), pat);
        } catch (e) {
            console.warn(`Could not store the token: ${e instanceof Error ? e.message : e}`);
        }
    };
    return { pat, save };
}
