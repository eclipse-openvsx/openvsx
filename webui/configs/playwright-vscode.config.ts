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

import { defineConfig, devices } from '@playwright/test';

/**
 * Runs test/e2e-vscode against code-server from the `vscode` profile of docker-compose.yml.
 */
export default defineConfig({
    testDir: '../test/e2e-vscode',
    // every step reloads VS Code, see test/e2e-vscode/support/vscode.ts
    timeout: 180000,
    expect: { timeout: 15000 },
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    // a single code-server, whose installed extensions the tests change
    workers: 1,
    reporter: 'html',
    use: {
        baseURL: 'http://localhost:8085',
        actionTimeout: 30000,
        trace: 'on-first-retry'
    },
    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] }
        }
    ]
});
