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

import { test as base } from '@playwright/test';
import { Registry } from './registry';
import { VSCode } from './vscode';

export { expect } from '@playwright/test';

export const test = base.extend<{ registryURL: string; registry: Registry }, { vscode: VSCode }>({
    // read from code-server itself, so the expectations always come from the registry it uses
    registryURL: async ({ playwright, baseURL }, use) => {
        const request = await playwright.request.newContext({ baseURL });
        const workbench = await request.get('/').catch(() => undefined);
        if (!workbench?.ok()) {
            throw new Error(`code-server is not running on ${baseURL}: start it with \`make code-server\``);
        }
        const configuration = (await workbench.text()).replace(/&quot;/g, '"');
        await request.dispose();
        const registryURL = configuration.match(/"serviceUrl":"([^"]+)\/vscode\/gallery"/)?.[1];
        if (!registryURL) {
            throw new Error(`code-server on ${baseURL} has no extension gallery configured`);
        }
        await use(registryURL);
    },
    registry: async ({ playwright, registryURL }, use) => {
        const request = await playwright.request.newContext({ baseURL: registryURL });
        await use(new Registry(request));
        await request.dispose();
    },
    // one window across tests, since loading VS Code takes most of a test: Playwright starts a new
    // worker, and with it a new window, after a failure
    vscode: [
        async ({ browser }, use, workerInfo) => {
            const vscode = new VSCode(browser, workerInfo.project.use.baseURL!);
            await use(vscode);
            await vscode.close();
        },
        { scope: 'worker' }
    ]
});
