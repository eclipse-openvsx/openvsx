/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

export interface InstallClient {
    /** Name shown in the Install menu. */
    name: string;
    /** URI scheme the client registers; the menu links to `<scheme>:extension/<namespace>.<name>`. */
    scheme: string;
}

// Alphabetical by name, so the order carries no ranking. To list another client that installs from
// Open VSX, add a row in its place.
export const INSTALL_CLIENTS: InstallClient[] = [
    { name: 'Antigravity', scheme: 'antigravity-ide' },
    { name: 'Cursor', scheme: 'cursor' },
    { name: 'Kiro', scheme: 'kiro' },
    { name: 'Positron', scheme: 'positron' },
    { name: 'VS Code Insiders', scheme: 'vscode-insiders' },
    { name: 'VSCodium', scheme: 'vscodium' },
    { name: 'Windsurf', scheme: 'windsurf' }
];
