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

import { curatedExtensions, curatedPreReleaseExtensions } from './curated-extensions';
import { expect, test } from './support/fixtures';

// installs go through code-server's backend, and VS Code only shows them after a restart
const installTimeout = { timeout: 60000 };

for (const id of curatedExtensions) {
    test.describe(id, () => {
        test('installs the latest version', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);
            await vscode.uninstallIfInstalled(extension);

            await (await vscode.openExtension(extension)).install();
            await expect.poll(() => vscode.installedVersion(extension), installTimeout).toBe(extension.version);
        });

        test('installs an older release, then updates to the latest', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);
            // VS Code in the browser only installs a specific version of an extension already installed
            await vscode.uninstallIfInstalled(extension);
            await (await vscode.openExtension(extension)).install();

            const olderRelease = await vscode.installOlderRelease(extension, extension.version);
            await expect.poll(() => vscode.installedVersion(extension), installTimeout).toBe(olderRelease);

            await (await vscode.openExtension(extension)).updateTo(extension.version);
            await expect.poll(() => vscode.installedVersion(extension), installTimeout).toBe(extension.version);
        });
    });
}

for (const id of curatedPreReleaseExtensions) {
    test.describe(id, () => {
        // GitLens opens its own pages in every new window, which would take the other tests' clicks
        test.afterEach(async ({ registry, vscode }) => {
            await vscode.uninstallIfInstalled(await registry.extension(id));
        });

        test('installs the pre-release, then switches back to the latest release', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);
            test.skip(!extension.preReleaseVersion, 'the registry has no pre-release of it');
            await vscode.uninstallIfInstalled(extension);

            await (await vscode.openExtension(extension)).installPreRelease();
            await expect
                .poll(() => vscode.installedVersion(extension), installTimeout)
                .toBe(extension.preReleaseVersion);
            const editor = await vscode.openExtension(extension);
            await expect(editor.preReleaseBadge).toBeVisible();

            await editor.switchToRelease();
            await expect.poll(() => vscode.installedVersion(extension), installTimeout).toBe(extension.version);
        });
    });
}
