/********************************************************************************
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
 ********************************************************************************/

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, waitFor } from '@testing-library/react';
import { renderHookWithProviders } from '../support/test-providers';
import { testUser } from '../support/trusted-publishing';
import { usePublishQueue } from '../../../src/context/publish-queue-context';
import { useUserExtensions } from '../../../src/hooks/use-user-extensions';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';
import { Extension, RegistryVersion } from '../../../src/extension-registry-types';

const vsix = (name = 'bar.vsix') => new File(['package'], name, { type: 'application/vsix' });

const published = (overrides: Partial<Extension> = {}): Extension =>
    ({
        name: 'bar',
        namespace: 'foo',
        displayName: 'Bar',
        version: '1.0.0',
        // The async publish pipeline fills this in; a package with an icon needs no further polling.
        files: { icon: 'https://registry.test/icon.png' },
        ...overrides
    }) as Extension;

function renderQueue(
    service: Partial<ExtensionRegistryService>,
    options: { loggedIn?: boolean; version?: RegistryVersion } = {}
) {
    const { loggedIn = true, version } = options;
    const handleError = vi.fn();
    return {
        handleError,
        ...renderHookWithProviders(() => usePublishQueue(), {
            mainContext: {
                service: service as ExtensionRegistryService,
                user: loggedIn ? testUser : undefined,
                version,
                handleError
            }
        })
    };
}

describe('publish queue', () => {
    it('uploads every dropped package straight away, with no confirmation step', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const { result } = renderQueue({ publishExtension });

        await act(async () => {
            await result.current.publish([vsix('one.vsix'), vsix('two.vsix')]);
        });

        await waitFor(() => expect(publishExtension).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(result.current.items.map(item => item.status)).toEqual(['published', 'published']));
    });

    it('ignores files that are not .vsix packages, and says so', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const { result, handleError } = renderQueue({ publishExtension });

        await act(async () => {
            await result.current.publish([new File([''], 'notes.txt')]);
        });

        expect(publishExtension).not.toHaveBeenCalled();
        expect(result.current.items).toHaveLength(0);
        expect(handleError).toHaveBeenCalledWith(expect.objectContaining({ error: expect.stringContaining('.vsix') }));
    });

    it('says nothing when the file picker comes back empty', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const { result, handleError } = renderQueue({ publishExtension });

        await act(async () => {
            await result.current.publish([]);
        });

        expect(handleError).not.toHaveBeenCalled();
    });

    it('publishes nothing without a logged-in user', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const { result } = renderQueue({ publishExtension }, { loggedIn: false });

        await act(async () => {
            await result.current.publish([vsix()]);
        });

        expect(publishExtension).not.toHaveBeenCalled();
    });

    // A rejected publish reaches us as a thrown error: sendRequest throws the parsed body.
    it('creates the namespace of a first-time publisher and publishes again', async () => {
        const publishExtension = vi
            .fn()
            .mockRejectedValueOnce({
                error: "Unknown publisher: foo\nUse the 'create-namespace' command to create a namespace."
            })
            .mockResolvedValueOnce(published());
        const createNamespace = vi.fn().mockResolvedValue({ success: 'ok' });
        const { result } = renderQueue({ publishExtension, createNamespace });

        await act(async () => {
            await result.current.publish([vsix()]);
        });

        await waitFor(() => expect(result.current.items[0].status).toBe('published'));
        expect(createNamespace).toHaveBeenCalledWith('foo');
        expect(publishExtension).toHaveBeenCalledTimes(2);
    });

    it('reads the namespace even when the message carries no second line', async () => {
        const publishExtension = vi
            .fn()
            .mockRejectedValueOnce({ error: 'Unknown publisher: foo' })
            .mockResolvedValueOnce(published());
        const createNamespace = vi.fn().mockResolvedValue({ success: 'ok' });
        const { result } = renderQueue({ publishExtension, createNamespace });

        await act(async () => {
            await result.current.publish([vsix()]);
        });

        await waitFor(() => expect(createNamespace).toHaveBeenCalledWith('foo'));
    });

    it('surfaces the registry error when publishing fails for another reason', async () => {
        const publishExtension = vi.fn().mockRejectedValue({ error: 'Extension too large' });
        const createNamespace = vi.fn();
        const { result, handleError } = renderQueue({ publishExtension, createNamespace });

        await act(async () => {
            await result.current.publish([vsix()]);
        });

        await waitFor(() => expect(result.current.items[0].status).toBe('failed'));
        expect(result.current.items[0].error).toBe('Extension too large');
        expect(createNamespace).not.toHaveBeenCalled();
        // Loud as well as recorded: the app's error dialog reports it like any other failed request.
        expect(handleError).toHaveBeenCalledWith(expect.objectContaining({ error: 'Extension too large' }));
    });

    it('words a failed card the way the error dialog words it', async () => {
        const publishExtension = vi.fn().mockRejectedValue({ error: 'Bad Request', message: 'Unsupported manifest' });
        const { result } = renderQueue({ publishExtension });

        await act(async () => {
            await result.current.publish([vsix()]);
        });

        await waitFor(() => expect(result.current.items[0].status).toBe('failed'));
        expect(result.current.items[0].error).toBe('Bad Request (Unsupported manifest)');
    });

    it('fails a package above the ceiling on its card instead of uploading it', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const { result } = renderQueue(
            { publishExtension },
            { version: { version: '1.0.0', maxExtensionSize: 4, maxExtensionSizeCeiling: 4 } }
        );

        await act(async () => {
            await result.current.publish([vsix('big.vsix'), new File(['x'], 'small.vsix')]);
        });

        const oversized = result.current.items.find(item => item.fileName === 'big.vsix');
        expect(oversized?.status).toBe('failed');
        expect(oversized?.error).toBe('Larger than the 4 B limit.');
        // The rest of the drop is unaffected.
        await waitFor(() => expect(publishExtension).toHaveBeenCalledOnce());
        expect(publishExtension).toHaveBeenCalledWith(expect.objectContaining({ name: 'small.vsix' }));
    });

    /**
     * The default limit says nothing about a namespace that has a size override: only the registry
     * knows which applies, and it cannot say until it has parsed the package. Blocking on the default
     * refused uploads the registry would have accepted.
     */
    /**
     * The ceiling is read once when the app starts and never refreshed, so the tab a publisher
     * already had open still holds the old one. That is exactly the tab they retry in after asking
     * an admin for the override - refusing there would block the package the override was created
     * for, and nothing on screen would suggest reloading.
     */
    it('re-reads the ceiling before refusing, so a just-granted override is honoured', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const getRegistryVersion = vi.fn().mockResolvedValue({ version: '1.3.0', maxExtensionSizeCeiling: 1024 });
        const { result } = renderQueue(
            { publishExtension, getRegistryVersion },
            { version: { version: '1.3.0', maxExtensionSize: 4, maxExtensionSizeCeiling: 4 } }
        );

        await act(async () => {
            await result.current.publish([vsix('big.vsix')]);
        });

        expect(getRegistryVersion).toHaveBeenCalled();
        await waitFor(() => expect(publishExtension).toHaveBeenCalledOnce());
        expect(result.current.items[0].status).not.toBe('failed');
    });

    it('still refuses when the ceiling has not moved', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const getRegistryVersion = vi.fn().mockResolvedValue({ version: '1.3.0', maxExtensionSizeCeiling: 4 });
        const { result } = renderQueue(
            { publishExtension, getRegistryVersion },
            { version: { version: '1.3.0', maxExtensionSize: 4, maxExtensionSizeCeiling: 4 } }
        );

        await act(async () => {
            await result.current.publish([vsix('big.vsix')]);
        });

        expect(result.current.items[0].status).toBe('failed');
        expect(publishExtension).not.toHaveBeenCalled();
    });

    it('uploads a package above the default limit but below the ceiling', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const { result } = renderQueue(
            { publishExtension },
            { version: { version: '1.0.0', maxExtensionSize: 4, maxExtensionSizeCeiling: 1024 } }
        );

        await act(async () => {
            await result.current.publish([vsix('big.vsix')]);
        });

        await waitFor(() => expect(publishExtension).toHaveBeenCalledOnce());
        expect(result.current.items[0].status).not.toBe('failed');
    });

    it('leaves the extension list holding what it read while following the package', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published());
        const getExtensions = vi.fn().mockResolvedValueOnce([]).mockResolvedValue([published()]);
        const handleError = vi.fn();
        const { result } = renderHookWithProviders(
            // Both read the same cache entry, so the settings list does not fetch again of its own.
            () => ({ queue: usePublishQueue(), extensions: useUserExtensions() }),
            {
                mainContext: {
                    service: { publishExtension, getExtensions } as unknown as ExtensionRegistryService,
                    user: testUser,
                    handleError
                }
            }
        );
        await waitFor(() => expect(result.current.extensions.data).toEqual([]));

        await act(async () => {
            await result.current.queue.publish([vsix()]);
        });

        await waitFor(() => expect(result.current.extensions.data).toEqual([published()]));
        expect(getExtensions).toHaveBeenCalledTimes(2);
    });

    it('lists the newest upload first, so a fresh one always lands in the same place', async () => {
        const publishExtension = vi.fn().mockReturnValue(new Promise(() => {}));
        const { result } = renderQueue({ publishExtension });

        await act(async () => {
            await result.current.publish([vsix('first.vsix')]);
        });
        await act(async () => {
            await result.current.publish([vsix('second.vsix')]);
        });

        expect(result.current.items.map(item => item.fileName)).toEqual(['second.vsix', 'first.vsix']);
    });

    it('drops finished entries on clear, leaving work in flight alone', async () => {
        const publishExtension = vi
            .fn()
            .mockResolvedValueOnce(published())
            .mockReturnValueOnce(new Promise(() => {}));
        const { result } = renderQueue({ publishExtension });

        await act(async () => {
            await result.current.publish([vsix('done.vsix'), vsix('busy.vsix')]);
        });
        await waitFor(() => expect(result.current.items.some(item => item.status === 'published')).toBe(true));

        act(() => result.current.clearFinished());

        expect(result.current.items.map(item => item.fileName)).toEqual(['busy.vsix']);
    });
});

describe('publish queue — review polling', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('keeps checking an extension left under review until the verdict lands', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published({ reviewStatus: 'under_review' }));
        const getExtensions = vi
            .fn()
            // the first read hydrates the card, the rest are the review poll
            .mockResolvedValueOnce([published({ reviewStatus: 'under_review' })])
            .mockResolvedValueOnce([published({ reviewStatus: 'under_review' })])
            .mockResolvedValue([published({ reviewStatus: 'published' })]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        expect(result.current.items[0].status).toBe('reviewing');

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(getExtensions).toHaveBeenCalledTimes(2);
        expect(result.current.items[0].status).toBe('reviewing');

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(result.current.items[0].status).toBe('published');
    });

    it('keeps re-reading until the async pipeline has stored the icon', async () => {
        const withoutIcon = published({ files: {} as Extension['files'] });
        const publishExtension = vi.fn().mockResolvedValue(withoutIcon);
        const getExtensions = vi
            .fn()
            .mockResolvedValueOnce([withoutIcon])
            .mockResolvedValueOnce([withoutIcon])
            .mockResolvedValue([published()]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        expect(result.current.items[0].extension?.files.icon).toBeUndefined();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(result.current.items[0].extension?.files.icon).toBeUndefined();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(result.current.items[0].extension?.files.icon).toBe('https://registry.test/icon.png');
    });

    it('gives up on a missing icon rather than polling for ever', async () => {
        const withoutIcon = published({ files: {} as Extension['files'] });
        const publishExtension = vi.fn().mockResolvedValue(withoutIcon);
        const getExtensions = vi.fn().mockResolvedValue([withoutIcon]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(90_000);
        });

        expect(result.current.items[0].status).toBe('published');
        // 60s of 5s polls, and then it stops.
        expect(getExtensions.mock.calls.length).toBeLessThanOrEqual(14);
    });

    it('keeps reading when the read-back after publish did not land', async () => {
        // The publish response carries no reviewStatus, so taking it at face value settles the card
        // as published — even for a package the registry has actually parked under review.
        const publishExtension = vi.fn().mockResolvedValue(published());
        const getExtensions = vi
            .fn()
            .mockRejectedValueOnce(new Error('Failed to fetch'))
            .mockResolvedValue([published({ reviewStatus: 'under_review' })]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });

        expect(result.current.items[0].status).toBe('reviewing');
    });

    it('waits for the icon from the end of the review, not from the upload', async () => {
        const reviewing = published({ reviewStatus: 'under_review', files: {} as Extension['files'] });
        const withoutIcon = published({ files: {} as Extension['files'] });
        const publishExtension = vi.fn().mockResolvedValue(reviewing);
        const getExtensions = vi.fn().mockResolvedValue([reviewing]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        // Longer under review than the whole icon budget, which only starts once it is through.
        await act(async () => {
            await vi.advanceTimersByTimeAsync(90_000);
        });
        expect(result.current.items[0].status).toBe('reviewing');

        getExtensions.mockResolvedValue([withoutIcon]);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(result.current.items[0].status).toBe('published');
        expect(result.current.items[0].awaitingIcon).toBe(true);

        getExtensions.mockResolvedValue([published()]);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(result.current.items[0].extension?.files.icon).toBe('https://registry.test/icon.png');
    });

    it('reads the registry once for the packages polling together', async () => {
        const one = published({ name: 'one', files: {} as Extension['files'] });
        const two = published({ name: 'two', files: {} as Extension['files'] });
        const publishExtension = vi.fn().mockResolvedValueOnce(one).mockResolvedValueOnce(two);
        const getExtensions = vi.fn().mockResolvedValue([one, two]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix('one.vsix'), vsix('two.vsix')]);
        });
        // Both cards hydrate from the same list.
        expect(getExtensions).toHaveBeenCalledOnce();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });

        expect(getExtensions).toHaveBeenCalledTimes(2);
    });

    it('holds a package whose namespace is not verified, instead of waiting on a review', async () => {
        const conflicted = published({ reviewStatus: 'under_review', namespaceOwnershipConflict: true });
        const publishExtension = vi.fn().mockResolvedValue(conflicted);
        const getExtensions = vi.fn().mockResolvedValue([conflicted]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        expect(result.current.items[0].status).toBe('blocked');

        await act(async () => {
            await vi.advanceTimersByTimeAsync(60_000);
        });

        expect(result.current.items[0].status).toBe('blocked');
        // Only the read that hydrated the card: claiming the namespace is the user's move, and no
        // amount of polling will see it happen.
        expect(getExtensions).toHaveBeenCalledTimes(1);
    });

    it('reports a package the review rejects, with the reason', async () => {
        const publishExtension = vi.fn().mockResolvedValue(published({ reviewStatus: 'under_review' }));
        const getExtensions = vi
            .fn()
            .mockResolvedValueOnce([published({ reviewStatus: 'under_review' })])
            .mockResolvedValue([published({ reviewStatus: 'rejected', reviewMessage: 'Malicious code found' })]);
        const { result } = renderQueue({ publishExtension, getExtensions });

        await act(async () => {
            await result.current.publish([vsix()]);
        });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });

        expect(result.current.items[0].status).toBe('rejected');
        expect(result.current.items[0].error).toBe('Malicious code found');
    });
});
