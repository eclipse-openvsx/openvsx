/********************************************************************************
 * Copyright (c) 2022 Anibal Solon and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { getPAT, doVerifyPat } from './pat';
import { readManifest, addEnvOptions } from './util';
import { VerifyPatOptions } from './verify-pat-options';

/**
 * Validates that a Personal Access Token can publish to a namespace.
 */
export async function verifyPat(options: VerifyPatOptions): Promise<void> {
    addEnvOptions(options);
    if (!options.namespace) {
        let error;
        try {
            options.namespace = (await readManifest()).publisher;
        } catch (e) {
            error = e;
        }

        if (!options.namespace) {
            throw new Error(
                `Unable to read the namespace's name. Please supply it as an argument or run ovsx from the extension folder.` +
                (error ? `\n\n${error}` : '')
            );
        }
    }

    const { pat, save } = await getPAT(options.namespace, options, false);
    options.pat = pat;
    await doVerifyPat(options);
    await save();
}
