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
import { afterEach, describe, expect, it } from 'vitest';
import { createTempFile } from '../../src/util';

describe('createTempFile', () => {

    const created: string[] = [];

    afterEach(() => {
        created.splice(0).forEach(file => fs.rmSync(file, { force: true }));
    });

    async function givenTempFile(postfix?: string): Promise<string> {
        const file = await createTempFile(postfix === undefined ? undefined : { postfix });
        created.push(file);
        return file;
    }

    it('returns an unused path with the given postfix directly in the OS temp dir', async () => {
        const file = await givenTempFile('.vsix');

        expect(path.dirname(file)).toBe(os.tmpdir());
        expect(path.basename(file)).toMatch(/^ovsx-[0-9a-f-]{36}\.vsix$/);
        expect(fs.existsSync(file)).toBe(false);
    });

    it('returns a path without an extension when no postfix is given', async () => {
        const file = await givenTempFile();

        expect(path.basename(file)).toMatch(/^ovsx-[0-9a-f-]{36}$/);
    });

    it('returns a different path on each call', async () => {
        const first = await givenTempFile('.pem');
        const second = await givenTempFile('.pem');

        expect(first).not.toBe(second);
    });
});
