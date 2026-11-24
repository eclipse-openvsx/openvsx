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

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import type {
    NameSquattingCollisions,
    NameSquattingExtension,
    NameSquattingLookalike
} from '../../../../../src/extension-registry-types';
import { CollisionsTab } from '../../../../../src/pages/admin-dashboard/name-squatting/collisions-tab';
import { renderWithProviders } from '../../../support/test-providers';

const ext = (namespace: string, name: string, displayName = name): NameSquattingExtension => ({
    namespace,
    name,
    displayName,
    downloadCount: 1200,
    publishedDate: '2024-01-01T00:00:00Z',
    activeVersionCount: 2,
    active: true
});

const lookalike = (namespace: string, name: string, exact: boolean): NameSquattingLookalike => ({
    extension: ext(namespace, name, 'Python'),
    matches: [{ field: 'DISPLAY_NAME', value: 'Python', lookalikeValue: 'Python', similarity: 1, exact }],
    namespaceSimilarity: 0,
    flaggedAt: '2026-11-01T00:00:00Z'
});

const anchor = ext('ms-python', 'python', 'Python');

const page = (lookalikes: NameSquattingLookalike[], hasMore = false): NameSquattingCollisions => ({
    groups: [{ anchor, lookalikes }],
    next: { offset: 25, afterId: hasMore ? 7 : undefined },
    hasMore
});

// not named render*, so the testing-library naming rule does not treat the stub it returns as a render result
const mountTab = (admin: Record<string, unknown>) => {
    renderWithProviders(<CollisionsTab />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('CollisionsTab', () => {
    it('lists the extensions published later under the one published first', async () => {
        mountTab({
            getNameSquattingCollisions: vi.fn().mockResolvedValue(page([lookalike('pyth0n', 'python', true)]))
        });

        expect(await screen.findByText('pyth0n.python')).toBeInTheDocument();
        expect(screen.getByText('ms-python.python')).toBeInTheDocument();
        expect(screen.getByText('Exact')).toBeInTheDocument();
    });

    it('explains a lookup stopped by the statement timeout', async () => {
        mountTab({
            getNameSquattingCollisions: vi.fn().mockRejectedValue({ error: 'timed out', status: 503 })
        });

        expect(await screen.findByText(/the check took too long and was stopped/i)).toBeInTheDocument();
    });

    it('says nothing was found rather than nothing matched when the server found nothing', async () => {
        mountTab({
            getNameSquattingCollisions: vi.fn().mockResolvedValue({ groups: [], next: { offset: 0 }, hasMore: false })
        });

        expect(await screen.findByText('No similar names found')).toBeInTheDocument();
    });

    it('hides look-alikes without an exact match when only exact ones are asked for', async () => {
        const user = userEvent.setup();
        mountTab({
            getNameSquattingCollisions: vi
                .fn()
                .mockResolvedValue(page([lookalike('pyth0n', 'python', true), lookalike('labs', 'pythom', false)]))
        });
        await screen.findByText('labs.pythom');

        await user.click(screen.getByLabelText('Exact matches only'));

        expect(screen.queryByText('labs.pythom')).not.toBeInTheDocument();
        expect(screen.getByText('pyth0n.python')).toBeInTheDocument();
    });

    it('loads the next batch from the cursor the server returned', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingCollisions: vi
                .fn()
                .mockResolvedValueOnce(page([lookalike('pyth0n', 'python', true)], true))
                .mockResolvedValueOnce(page([lookalike('labs', 'pythom', false)]))
        });

        await user.click(await screen.findByRole('button', { name: 'Load more groups' }));

        expect(await screen.findByText('labs.pythom')).toBeInTheDocument();
        expect(admin.getNameSquattingCollisions).toHaveBeenLastCalledWith(
            expect.anything(),
            { offset: 25, afterId: 7 },
            25
        );
        // the anchor of both batches stays one group
        expect(screen.getAllByText('ms-python.python')).toHaveLength(1);
    });

    // The server stores the look-alike as the allowed side; swapping them would list the pair under
    // the extension published first on the Allowlist tab.
    it('allows the look-alike alongside the extension it resembles, with the reason given', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingCollisions: vi.fn().mockResolvedValue(page([lookalike('pyth0n', 'python', true)])),
            allowNameSquattingPair: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Allow' }));
        await user.type(screen.getByLabelText(/reason/i), '  same publisher  ');
        await user.click(screen.getByRole('button', { name: 'Allow pair' }));

        await waitFor(() =>
            expect(admin.allowNameSquattingPair).toHaveBeenCalledWith({
                namespace: 'pyth0n',
                extension: 'python',
                otherNamespace: 'ms-python',
                otherExtension: 'python',
                reason: 'same publisher'
            })
        );
        await waitFor(() => expect(screen.queryByText('pyth0n.python')).not.toBeInTheDocument());
        expect(admin.getNameSquattingCollisions).toHaveBeenCalledTimes(1);
    });

    // Refetching would look every loaded candidate up again on the server.
    it('takes the pairs allowed before a failure off the list without refetching it', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingCollisions: vi
                .fn()
                .mockResolvedValue(page([lookalike('pyth0n', 'python', true), lookalike('labs', 'pythom', false)])),
            allowNameSquattingPair: vi
                .fn()
                .mockResolvedValueOnce({ success: 'ok' })
                .mockRejectedValueOnce({ error: 'Server error', status: 500 })
        });

        await user.click(await screen.findByRole('button', { name: 'Allow all…' }));
        await user.click(screen.getByRole('button', { name: 'Allow 2 pairs' }));

        expect(await screen.findByText(/server error/i)).toBeInTheDocument();
        expect(screen.queryByRole('row', { name: /pyth0n\.python/, hidden: true })).not.toBeInTheDocument();
        expect(screen.getByRole('row', { name: /labs\.pythom/, hidden: true })).toBeInTheDocument();
        expect(admin.getNameSquattingCollisions).toHaveBeenCalledTimes(1);
    });

    // A batch requested before the allow would write back the pages as they were when it started.
    it('drops a batch that was loading when a pair was allowed, so the pair stays off the list', async () => {
        const user = userEvent.setup();
        let resolveNext: (page: NameSquattingCollisions) => void = () => undefined;
        const admin = mountTab({
            getNameSquattingCollisions: vi
                .fn()
                .mockResolvedValueOnce(page([lookalike('pyth0n', 'python', true)], true))
                .mockReturnValueOnce(new Promise(resolve => (resolveNext = resolve)))
                .mockResolvedValue(page([lookalike('labs', 'pythom', false)])),
            allowNameSquattingPair: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Load more groups' }));
        await user.click(screen.getByRole('button', { name: 'Allow' }));
        await user.click(screen.getByRole('button', { name: 'Allow pair' }));
        await waitFor(() => expect(admin.allowNameSquattingPair).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        resolveNext(page([lookalike('labs', 'pythom', false)]));
        await user.click(await screen.findByRole('button', { name: 'Load more groups' }));

        expect(await screen.findByRole('row', { name: /labs\.pythom/ })).toBeInTheDocument();
        expect(screen.queryByRole('row', { name: /pyth0n\.python/ })).not.toBeInTheDocument();
    });

    it('keeps every match of a look-alike that shares one field more than once', async () => {
        const twoDisplayNames: NameSquattingLookalike = {
            ...lookalike('pyth0n', 'python', false),
            matches: [
                { field: 'DISPLAY_NAME', value: 'Python', lookalikeValue: 'Pyth0n', similarity: 0.8, exact: false },
                { field: 'DISPLAY_NAME', value: 'Python', lookalikeValue: 'Pythonn', similarity: 0.9, exact: false }
            ]
        };
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        mountTab({ getNameSquattingCollisions: vi.fn().mockResolvedValue(page([twoDisplayNames])) });

        const row = await screen.findByRole('row', { name: /pyth0n\.python/ });

        expect(within(row).getAllByText(/^Display name$/)).toHaveLength(2);
        expect(consoleError.mock.calls.flat().join(' ')).not.toMatch(/same key/);
        consoleError.mockRestore();
    });

    it('allows every look-alike of a group with "Allow all"', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingCollisions: vi
                .fn()
                .mockResolvedValue(page([lookalike('pyth0n', 'python', true), lookalike('labs', 'pythom', false)])),
            allowNameSquattingPair: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Allow all…' }));
        await user.click(screen.getByRole('button', { name: 'Allow 2 pairs' }));

        await waitFor(() => expect(admin.allowNameSquattingPair).toHaveBeenCalledTimes(2));
        expect(admin.allowNameSquattingPair).toHaveBeenCalledWith(expect.objectContaining({ namespace: 'labs' }));
    });

    // The delete endpoint removes only the versions it is named; a removed one would fail the request.
    it('deletes every active version of the later extension once its id is typed', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingCollisions: vi.fn().mockResolvedValue(page([lookalike('pyth0n', 'python', true)])),
            getExtension: vi.fn().mockResolvedValue({
                namespace: 'pyth0n',
                name: 'python',
                allTargetPlatformVersions: [
                    {
                        version: '1.0.1',
                        targetPlatforms: [
                            { targetPlatform: 'linux-x64', removed: false },
                            { targetPlatform: 'win32-x64', removed: true }
                        ]
                    },
                    { version: '1.0.0', targetPlatforms: [{ targetPlatform: 'universal', removed: false }] }
                ]
            }),
            deleteExtensions: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Delete pyth0n.python' }));
        const dialog = await screen.findByRole('dialog');
        const confirm = within(dialog).getByRole('button', { name: 'Delete extension' });
        await within(dialog).findByText(/all 2 versions \(2 platforms\)/i);
        expect(confirm).toBeDisabled();
        expect(within(dialog).queryByText(/published after/i)).not.toBeInTheDocument();

        await user.type(within(dialog).getByLabelText(/to confirm/i), 'pyth0n.python');
        await user.click(confirm);

        await waitFor(() =>
            expect(admin.deleteExtensions).toHaveBeenCalledWith({
                namespace: 'pyth0n',
                extension: 'python',
                targetPlatformVersions: [
                    { version: '1.0.1', targetPlatform: 'linux-x64' },
                    { version: '1.0.0', targetPlatform: 'universal' }
                ]
            })
        );
    });

    // Someone may publish an extension here before its author does, so the first published is not trusted.
    it('deletes the extension published first, warning that the later ones regroup', async () => {
        const user = userEvent.setup();
        const admin = mountTab({
            getNameSquattingCollisions: vi
                .fn()
                .mockResolvedValue(page([lookalike('pyth0n', 'python', true), lookalike('labs', 'pythom', false)])),
            getExtension: vi.fn().mockResolvedValue({
                namespace: 'ms-python',
                name: 'python',
                allTargetPlatformVersions: [
                    { version: '2.0.0', targetPlatforms: [{ targetPlatform: 'universal', removed: false }] }
                ]
            }),
            deleteExtensions: vi.fn().mockResolvedValue({ success: 'ok' })
        });

        await user.click(await screen.findByRole('button', { name: 'Delete ms-python.python' }));
        const dialog = await screen.findByRole('dialog');
        expect(
            within(dialog).getByText(/2 extensions published after ms-python\.python resemble it/i)
        ).toBeInTheDocument();
        await within(dialog).findByText(/its only version is removed/i);

        await user.type(within(dialog).getByLabelText(/to confirm/i), 'ms-python.python');
        await user.click(within(dialog).getByRole('button', { name: 'Delete extension' }));

        await waitFor(() =>
            expect(admin.deleteExtensions).toHaveBeenCalledWith({
                namespace: 'ms-python',
                extension: 'python',
                targetPlatformVersions: [{ version: '2.0.0', targetPlatform: 'universal' }]
            })
        );
    });
});
