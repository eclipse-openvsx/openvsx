/********************************************************************************
 * Copyright (c) 2024 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
import { openDefaultStore } from "./store";
import { LoginOptions } from "./login-options";
import { getStoredPAT, legacyPatKey, patKey } from "./pat";
import { addEnvOptions } from "./util";

export default async function logout(options: LoginOptions) {
	addEnvOptions(options);
	const namespaceName = options.namespace;
	if (!namespaceName) {
        throw new Error('Missing namespace name.');
    }

	const store = await openDefaultStore();
	if (!await getStoredPAT(store, namespaceName, options)) {
		throw new Error(`Unknown namespace '${namespaceName}'.`);
	}

	// the legacy key is read as a fallback, so it goes too; keychain stores throw on deleting a missing key
	for (const key of [patKey(namespaceName, options), legacyPatKey(namespaceName, options)]) {
		if (key && await store.get(key)) {
			await store.delete(key);
		}
	}
    console.log(`\ud83d\ude80  ${namespaceName} removed from the list of known namespaces`);
}
