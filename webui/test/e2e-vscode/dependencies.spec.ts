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

import { curatedExtensionsWithDependencies } from './curated-extensions';
import { expect, test } from './support/fixtures';

for (const id of curatedExtensionsWithDependencies) {
    test.describe(id, () => {
        test('lists the extensions its pack installs', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);
            test.skip(!extension.bundledExtensions.length, 'it has no extension pack');

            // a pack in the Extension Packs category lists them on its Details tab
            const editor = await vscode.openExtension(extension);
            await expect(editor.root.getByText(`Extension Pack (${extension.bundledExtensions.length})`)).toBeVisible();
            for (const bundled of extension.bundledExtensions) {
                await expect(editor.root.getByText(await registry.displayName(bundled), { exact: true })).toBeVisible();
            }
        });

        test('lists its dependencies', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);
            test.skip(!extension.dependencies.length, 'it has no dependencies');

            const editor = await vscode.openExtension(extension);
            await editor.show('Dependencies');
            for (const dependency of extension.dependencies) {
                await expect(editor.dependency(await registry.displayName(dependency))).toBeVisible();
            }
        });
    });
}
