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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { publish } from '../../src/publish';
import { show } from '../../src/show';
import { search } from '../../src/search';
import { verifySignature } from '../../src/verify-signature';
import { handleError } from '../../src/util';

vi.mock('../../src/publish', () => ({ publish: vi.fn(async () => []) }));
vi.mock('../../src/show', () => ({ show: vi.fn(async () => undefined) }));
vi.mock('../../src/search', async importOriginal => ({
    ...await importOriginal<typeof import('../../src/search')>(),
    search: vi.fn(async () => undefined)
}));
vi.mock('../../src/verify-signature', () => ({ verifySignature: vi.fn(async () => undefined) }));
vi.mock('../../src/util', async importOriginal => ({
    ...await importOriginal<typeof import('../../src/util')>(),
    handleError: vi.fn(() => () => undefined)
}));

class ExitError extends Error {
    constructor(readonly code: number | undefined) {
        super(`process.exit(${code})`);
    }
}

let main: (argv: string[]) => void;

function run(...args: string[]): void {
    const argv = ['node', 'ovsx', ...args];
    // main checks process.argv rather than its argument for a missing command.
    vi.spyOn(process, 'argv', 'get').mockReturnValue(argv);
    main(argv);
}

describe('main', () => {
    let stderr: string;
    let stdout: string;

    beforeEach(async () => {
        main = (await import('../../src/main') as unknown as { default: typeof main }).default;
        stderr = '';
        stdout = '';
        vi.spyOn(process, 'exit').mockImplementation(code => {
            throw new ExitError(code as number | undefined);
        });
        vi.spyOn(process.stdout, 'write').mockImplementation(chunk => {
            stdout += String(chunk);
            return true;
        });
        vi.spyOn(process.stderr, 'write').mockImplementation(chunk => {
            stderr += String(chunk);
            return true;
        });
        vi.spyOn(console, 'error').mockImplementation((...data: unknown[]) => {
            stderr += data.join(' ') + '\n';
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
        // process.exitCode is real process state, not a mock, and outlives a real exit() only in tests.
        process.exitCode = 0;
    });

    it('passes global options given before or after the command', () => {
        run('-r', 'http://registry.test', 'show', 'redhat.java', '-t', 'linux-x64', '--all-versions', '-p', 'secret');
        expect(show).toHaveBeenCalledWith({
            extensionId: 'redhat.java',
            target: 'linux-x64',
            allVersions: true,
            json: undefined,
            registryUrl: 'http://registry.test'
        });
    });

    it('passes --debug to the error handler', () => {
        run('--debug', 'show', 'redhat.java');
        expect(handleError).toHaveBeenCalledWith(true);
        vi.mocked(handleError).mockClear();
        run('show', 'redhat.java');
        expect(handleError).toHaveBeenCalledWith(undefined);
    });

    it('passes variadic, negatable and camel-cased publish options', () => {
        run('publish', '-t', 'linux-x64', 'win32-x64', '--pre-release', '--no-dependencies', '--skip-duplicate', '-p', 'secret');
        expect(publish).toHaveBeenCalledWith(expect.objectContaining({
            extensionFile: undefined,
            pat: 'secret',
            targets: ['linux-x64', 'win32-x64'],
            preRelease: true,
            dependencies: false,
            skipDuplicate: true
        }));
    });

    it('passes options parsed by a custom parser', () => {
        run('search', 'java', '-s', '5', '--sort-by', 'downloadCount');
        expect(search).toHaveBeenCalledWith(expect.objectContaining({ text: 'java', size: 5, sortBy: 'downloadCount' }));
    });

    it('passes options to a command without arguments', () => {
        run('verify-signature', '-i', 'a.vsix', '-m', 'm.json', '-s', 's.p7s', '-k', 'key.pem');
        expect(verifySignature).toHaveBeenCalledWith({
            packagePath: 'a.vsix',
            manifestPath: 'm.json',
            signaturePath: 's.p7s',
            publicKeyPath: 'key.pem'
        });
    });

    it('suggests the closest command for a misspelled one', () => {
        expect(() => run('publsh')).toThrow(ExitError);
        expect(stderr).toContain("Unknown command 'publsh', did you mean 'publish'?");
        expect(stdout).toContain('Usage: ovsx <command> [options]');
        expect(publish).not.toHaveBeenCalled();
    });

    it('reports an unknown command without a close match', () => {
        expect(() => run('frobnicate')).toThrow(ExitError);
        expect(stderr).toContain("Unknown command 'frobnicate'.");
        expect(stdout).toContain('Usage: ovsx <command> [options]');
    });

    it('lists the commands in help without a catch-all entry', () => {
        expect(() => run('--help')).toThrow(ExitError);
        expect(stdout).toContain('publish [options] [extension.vsix]');
        expect(stdout).not.toMatch(/^\s+\*/m);
    });

    it('prints the version from package.json for --version', () => {
        const { version } = JSON.parse(readFileSync(join(__dirname, '..', '..', 'package.json'), 'utf8'));
        expect(() => run('--version')).toThrow(ExitError);
        expect(stdout).toBe(`${version}\n`);
    });

    it('shows help on stdout and exits 0 when run with no arguments', () => {
        expect(() => run()).toThrow(new ExitError(0));
        expect(stdout).toContain('Usage: ovsx <command> [options]');
        expect(stderr).toBe('');
    });
});
