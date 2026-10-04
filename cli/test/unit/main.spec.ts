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
import { readFileSync } from 'fs';
import { join } from 'path';
import { afterEach, describe, expect, it, vi } from 'vitest';

class ExitError extends Error {
    constructor(readonly code?: number) {
        super(`process.exit(${code})`);
    }
}

describe('main', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('prints the version from package.json for --version', async () => {
        const { version } = JSON.parse(readFileSync(join(__dirname, '..', '..', 'package.json'), 'utf8'));
        const main: (argv: string[]) => void = (await import('../../src/main')).default;
        const argv = ['node', 'ovsx', '--version'];
        vi.spyOn(process, 'argv', 'get').mockReturnValue(argv);
        vi.spyOn(process, 'exit').mockImplementation(code => {
            throw new ExitError(code as number | undefined);
        });
        const output: string[] = [];
        vi.spyOn(process.stdout, 'write').mockImplementation(chunk => {
            output.push(String(chunk));
            return true;
        });

        expect(() => main(argv)).toThrow(ExitError);
        expect(output.join('')).toBe(`${version}\n`);
    });
});
