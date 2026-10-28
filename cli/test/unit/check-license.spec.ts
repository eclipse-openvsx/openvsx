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

import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const prompts = vi.hoisted(() => ({ select: vi.fn(), input: vi.fn() }));
vi.mock('@inquirer/prompts', () => prompts);

// Fixes `isCI` per test, independent of the environment running the tests.
async function loadCheckLicense(ci: boolean) {
    vi.resetModules();
    vi.doMock('ci-info', () => ({ isCI: ci }));
    return (await import('../../src/check-license')).checkLicense;
}

describe('checkLicense', () => {
    let dir: string;

    beforeEach(async () => {
        dir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'ovsx-check-license-'));
        vi.spyOn(console, 'log').mockImplementation(() => undefined);
    });

    afterEach(async () => {
        vi.restoreAllMocks();
        prompts.select.mockReset();
        prompts.input.mockReset();
        vi.doUnmock('ci-info');
        await fs.promises.rm(dir, { recursive: true, force: true });
    });

    async function writePackage(manifest: object): Promise<void> {
        await fs.promises.writeFile(path.join(dir, 'package.json'), JSON.stringify(manifest), 'utf-8');
    }

    const manifest = { publisher: 'pub', name: 'ext', version: '1.0.0' };

    it('offers the MIT license outside CI and writes it when accepted', async () => {
        await writePackage(manifest);
        prompts.select.mockResolvedValue('yes');
        prompts.input.mockResolvedValue('2026 Jane Doe');

        await (await loadCheckLicense(false))(dir);

        expect(prompts.select).toHaveBeenCalledOnce();
        const written = JSON.parse(await fs.promises.readFile(path.join(dir, 'package.json'), 'utf-8'));
        expect(written.license).toBe('MIT');
        const license = await fs.promises.readFile(path.join(dir, 'LICENSE'), 'utf-8');
        expect(license.startsWith('Copyright 2026 Jane Doe\n')).toBe(true);
    });

    it('rejects an unlicensed extension when the MIT license is declined', async () => {
        await writePackage(manifest);
        prompts.select.mockResolvedValue('no');

        await expect((await loadCheckLicense(false))(dir))
            .rejects.toThrow('This extension cannot be accepted because it has no license.');
    });

    it('does not prompt in CI', async () => {
        await writePackage(manifest);

        await (await loadCheckLicense(true))(dir);

        expect(prompts.select).not.toHaveBeenCalled();
        expect(fs.existsSync(path.join(dir, 'LICENSE'))).toBe(false);
    });

    it('does not prompt when the manifest declares a license', async () => {
        await writePackage({ ...manifest, license: 'Apache-2.0' });

        await (await loadCheckLicense(false))(dir);

        expect(prompts.select).not.toHaveBeenCalled();
    });
});
