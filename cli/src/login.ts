/********************************************************************************
 * Copyright (c) 2024 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
import { confirm } from '@inquirer/prompts';
import { addEnvOptions } from './util';
import { openDefaultStore } from './store';
import { LoginOptions } from './login-options';
import { getStoredPAT, patKey, requestPAT } from './pat';

export default async function login(options: LoginOptions) {
	addEnvOptions(options);
	if (!options.namespace) {
        throw new Error('Missing namespace name.');
    }

	const store = await openDefaultStore();
	let pat = await getStoredPAT(store, options.namespace, options);
	if (pat) {
		console.log(`Namespace '${options.namespace}' is already known.`);
		const overwrite = await confirm({
			message: 'Do you want to overwrite its PAT?',
			default: false
		});

		if (!overwrite) {
			throw new Error('Aborted.');
		}
	}

	pat = await requestPAT(options.namespace, options);
	await store.add(patKey(options.namespace, options), pat);
}
