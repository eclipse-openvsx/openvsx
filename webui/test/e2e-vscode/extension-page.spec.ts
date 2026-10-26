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
import { ExtensionManifest, firstHeading, firstLine } from './support/registry';

// how the Features tab names the contribution points it lists
const featureNames: Record<string, string> = {
    commands: 'Commands',
    configuration: 'Settings',
    languages: 'Programming Languages'
};

for (const id of curatedExtensions) {
    test.describe(id, () => {
        test('shows its name, publisher, description and README', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            const editor = await vscode.openExtension(extension);
            await expect(editor.header.getByRole('heading')).toHaveText(extension.displayName);
            await expect(editor.header.getByRole('button', { name: extension.publisher, exact: true })).toBeVisible();
            await expect(editor.header.getByText(extension.description, { exact: true })).toBeVisible();
            await expect(editor.document.getByRole('heading', { name: firstHeading(extension.readme) })).toBeVisible();
        });

        test('shows its changelog', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            const editor = await vscode.openExtension(extension);
            await editor.show('Changelog');
            await expect(editor.document.locator('body')).toContainText(firstLine(extension.changelog));
        });

        test('lists the features its manifest contributes', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            const editor = await vscode.openExtension(extension);
            await editor.show('Features');
            for (const feature of contributedFeatures(extension.manifest)) {
                await expect(editor.features.getByRole('listitem', { name: feature }).first()).toBeVisible();
            }
        });

        test('shows its icon from the registry', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);
            // once installed, VS Code shows the icon from the installed files instead
            await vscode.uninstallIfInstalled(extension);

            const editor = await vscode.openExtension(extension);
            await expect(editor.icon).toBeVisible();
            await expect
                .poll(() => editor.icon.evaluate(icon => (icon as HTMLImageElement).naturalWidth))
                .toBeGreaterThan(0);
        });

        test('shows its marketplace details and categories', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            const editor = await vscode.openExtension(extension);
            const marketplace = editor.sidebarSection('Marketplace');
            await expect(marketplace).toContainText(extension.id, { ignoreCase: true });
            await expect(marketplace).toContainText(extension.version);
            for (const category of extension.categories) {
                await expect(editor.sidebarSection('Categories')).toContainText(category);
            }
        });

        test('links its repository, license and marketplace page', async ({ registry, vscode }) => {
            const extension = await registry.extension(id);

            const editor = await vscode.openExtension(extension);
            expect(await editor.openResource('Repository')).toBe(extension.repository);
            // where the license is served from varies between registries, so it only has to open
            expect(await registry.statusOf(await editor.openResource('License'))).toBe(200);
            // the item URL, when code-server asks first, or else the window that already followed its redirect
            const marketplace = await editor.openResource('Marketplace');
            const extensionPage = marketplace.includes('/vscode/item?')
                ? await registry.redirectOf(marketplace)
                : marketplace;
            expect(extensionPage).toMatch(new RegExp(`/extension/${extension.namespace}/${extension.name}$`));
        });
    });
}

function contributedFeatures(manifest: ExtensionManifest): string[] {
    return Object.keys(manifest.contributes ?? {}).flatMap(point => featureNames[point] ?? []);
}
