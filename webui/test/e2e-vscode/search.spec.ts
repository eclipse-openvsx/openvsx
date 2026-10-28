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
import { expect, test } from './support/fixtures';

for (const id of curatedExtensions) {
    test.describe(id, () => {
        test('is found by name', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            // checked on the browser's own query: VS Code silently sends one the browser cannot through
            // its backend, which would hide CORS and TLS problems
            const answer = await vscode.search(extension.displayName);
            expect(answer.status()).toBe(200);
            await expect(vscode.result(extension)).toBeVisible();
        });

        test('is found in its category', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            await vscode.search(`@category:"${extension.categories[0]}" ${extension.displayName}`);
            await expect(vscode.result(extension)).toBeVisible();
        });
    });
}
