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
import { Registry } from './registry';
import { openDefaultStore } from './store';

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

export interface ResolvedPAT {
    pat: string;
    /** Stores a prompted token; call it only once the registry has accepted the token. */
    save(): Promise<void>;
}

export async function getPAT(namespace: string, options: CreateNamespaceOptions | PublishOptions | VerifyPatOptions, verify: boolean = true): Promise<ResolvedPAT> {
    const nothingToSave = async () => undefined;
    if (options?.pat) {
        return { pat: options.pat, save: nothingToSave };
    }

    const store = await openDefaultStore();
    const stored = await store.get(namespace);
    if (stored) {
        return { pat: stored, save: nothingToSave };
    }

    const pat = await requestPAT(namespace, options, verify);
    return { pat, save: () => store.add(namespace, pat) };
}
