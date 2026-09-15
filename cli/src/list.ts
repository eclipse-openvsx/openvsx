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

import { ListOptions } from './list-options';
import { Registry } from './registry';
import { formatCount } from './table';
import { addEnvOptions } from './util';

/**
 * Lists the extensions published in a namespace.
 */
export async function list(options: ListOptions): Promise<void> {
    addEnvOptions(options);
    const registry = new Registry(options);
    const namespace = await registry.getNamespace(options.namespace);
    if (namespace.error) {
        throw new Error(namespace.error);
    }

    if (options.json) {
        console.log(JSON.stringify(namespace, null, 4));
        return;
    }

    // Sorted here rather than relying on the response's key order, so the output is stable and
    // scriptable regardless of how the registry happens to serialise the map.
    const names = Object.keys(namespace.extensions ?? {}).sort((a, b) => a.localeCompare(b));
    const verified = namespace.verified ? ' (verified)' : '';
    console.log(`${namespace.name}${verified} - ${formatCount(names.length, 'extension')}`);
    if (names.length === 0) {
        return;
    }

    console.log();
    for (const name of names) {
        console.log(`  ${namespace.name}.${name}`);
    }
}
