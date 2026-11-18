/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { CreateNamespaceOptions } from './create-namespace-options';
import { getPAT } from './pat';
import { Registry } from './registry';
import { addEnvOptions } from './util';

/**
 * Creates a namespace (corresponds to `publisher` in package.json).
 */
export async function createNamespace(options: CreateNamespaceOptions = {}): Promise<void> {
    addEnvOptions(options);
    if (!options.name) {
        throw new Error('The namespace name is mandatory.');
    }

    const { pat, save } = await getPAT(options.name, options, false);
    options.pat = pat;

    const registry = new Registry(options);
    const result = await registry.createNamespace(options.name, options.pat);
    if (result.error) {
        throw new Error(result.error);
    }
    await save();
    console.log(`\ud83d\ude80  Created namespace ${options.name}`);
}
