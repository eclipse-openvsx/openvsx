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

import { curatedExtensions } from './curated-extensions';
import { test as setup } from './support/fixtures';

// the specs check what the registry serves, which VS Code partly replaces with the files of an
// installed extension
for (const id of curatedExtensions) {
    setup(`uninstall ${id}`, async ({ registry, vscode }) => {
        await vscode.uninstallIfInstalled(await registry.extension(id));
    });
}
