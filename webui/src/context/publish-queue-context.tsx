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

import {
    createContext,
    FunctionComponent,
    ReactNode,
    useCallback,
    useContext,
    useEffect,
    useRef,
    useState
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../context';
import { Extension, isError } from '../extension-registry-types';
import { useRegistryValue } from '../hooks/use-registry-value';
import { userExtensionsQuery } from '../hooks/use-user-extensions';
import { usePublishExtension } from '../components/publish/use-publish-extension';
import { useCreateNamespace } from '../pages/user/namespaces/use-user-namespaces';
import { formatFileSize, handleError as formatError } from '../utils';

/** How often a freshly published package is re-read, and for how long. */
const POLL_INTERVAL_MS = 5000;
// Every package in the queue reads the same list, so a poll takes whatever another package read a
// moment ago: a verdict arriving up to half a poll late costs nothing, a request per package does.
const READ_SHARE_MS = POLL_INTERVAL_MS / 2;
const REVIEW_POLL_TIMEOUT_MS = 5 * 60 * 1000;
// Publishing hands the package to an async pipeline, so the extracted files (the icon among
// them) appear after the response. Worth a short wait, but plenty of extensions have no icon.
const ASSET_POLL_TIMEOUT_MS = 60 * 1000;

/** `blocked` is accepted-but-held: the registry has the package, and clearing what holds it back is the user's to do. */
export type PublishStatus = 'uploading' | 'reviewing' | 'published' | 'blocked' | 'rejected' | 'failed';

export interface PublishItem {
    id: number;
    fileName: string;
    size: number;
    status: PublishStatus;
    /** Set once the registry has accepted the package. */
    extension?: Extension;
    /** Set when the upload or the review failed. */
    error?: string;
    /** The queue is still re-reading the extension because its icon has not been stored yet. */
    awaitingIcon?: boolean;
}

export interface PublishQueue {
    items: PublishItem[];
    /** Uploads every package straight away — there is no confirmation step. */
    publish: (files: File[]) => void | Promise<void>;
    /** Drops everything that has finished, leaving work in flight alone. */
    clearFinished: () => void;
}

const PublishQueueContext = createContext<PublishQueue>({
    items: [],
    publish: () => {},
    clearFinished: () => {}
});

// eslint-disable-next-line react-refresh/only-export-components
export const usePublishQueue = (): PublishQueue => useContext(PublishQueueContext);

/** Only `.vsix` packages are publishable; a drop may carry anything. */
// eslint-disable-next-line react-refresh/only-export-components
export const isVsixFile = (file: File): boolean => file.name.toLowerCase().endsWith('.vsix');

// eslint-disable-next-line react-refresh/only-export-components
export const isFinished = (item: PublishItem): boolean => item.status !== 'uploading' && item.status !== 'reviewing';

const statusOf = (extension: Readonly<Extension>): PublishStatus => {
    // The registry parks a conflicting namespace under review, but only the user claiming it clears
    // that, so calling it "reviewing" names neither what is happening nor what to do about it.
    if (extension.namespaceOwnershipConflict) {
        return 'blocked';
    }
    switch (extension.reviewStatus) {
        case 'under_review':
            return 'reviewing';
        case 'rejected':
            return 'rejected';
        default:
            return 'published';
    }
};

/**
 * The name of the namespace the registry does not know yet, or undefined when the
 * failure was something else. The server answers `Unknown publisher: <name>\n…`.
 */
const unknownNamespace = (err: unknown): string | undefined => {
    const prefix = 'Unknown publisher: ';
    if (!isError(err) || !err.error.startsWith(prefix)) {
        return undefined;
    }
    // A missing newline would make substring() swap its bounds and yield the prefix itself.
    const end = err.error.indexOf('\n', prefix.length);
    const name = (end === -1 ? err.error.substring(prefix.length) : err.error.substring(prefix.length, end)).trim();
    return name && name !== 'undefined' ? name : undefined;
};

/**
 * App-wide queue of extension uploads. Packages dropped anywhere in the app land
 * here and start uploading immediately; a package the registry puts under review
 * is polled until the verdict arrives, so the queue reflects the real outcome
 * rather than just "uploaded".
 */
export const PublishQueueProvider: FunctionComponent<{ children: ReactNode }> = ({ children }) => {
    const { service, user, handleError } = useContext(MainContext);
    const { mutateAsync: publishPackage } = usePublishExtension();
    const { mutateAsync: createNamespace } = useCreateNamespace();
    const queryClient = useQueryClient();
    const [items, setItems] = useState<PublishItem[]>([]);
    const nextId = useRef(0);
    const maxSize = useRegistryValue(version => version.maxExtensionSizeCeiling);
    // A poll outlives the provider only if the app is being torn down; stop it rather than
    // carrying on against a queue nobody can see.
    const stopped = useRef(false);

    useEffect(() => {
        stopped.current = false;
        return () => {
            stopped.current = true;
        };
    }, []);

    const update = useCallback((id: number, patch: Partial<PublishItem>) => {
        setItems(current => current.map(item => (item.id === id ? { ...item, ...patch } : item)));
    }, []);

    /**
     * Reads a just-published extension back. It has to be the list endpoint: only that one reports
     * `reviewStatus` (the single-extension endpoint leaves it unset, so a package awaiting a scan
     * would look merely inactive), and it carries the extracted files too. Reading it through the
     * query cache also keeps the settings list in step, since it reads the same entry.
     *
     * `maxAgeMs` is how old a read may be to be taken as it is — zero right after publishing, where
     * the list has to include the package that was just uploaded.
     */
    const readPublished = useCallback(
        async (namespace: string, name: string, maxAgeMs: number): Promise<Readonly<Extension> | undefined> => {
            const extensions = await queryClient.fetchQuery({
                ...userExtensionsQuery(service),
                staleTime: maxAgeMs
            });
            return extensions.find(entry => entry.namespace === namespace && entry.name === name);
        },
        [queryClient, service]
    );

    /**
     * The verdict of the review and the files extracted from the package both land after publish
     * returns, so keep re-reading the extension until they do. Every read refreshes the card, so
     * the icon appears as soon as the pipeline has stored it.
     */
    const pollUntilSettled = useCallback(
        async (id: number, initial: Readonly<Extension>, options: { readBack: boolean }) => {
            const started = Date.now();
            // The two budgets need their own clocks: the files are only extracted once the package is
            // through, so a review lasting longer than the icon's budget would spend it before it began.
            let iconWaitFrom = started;
            let readBack = options.readBack;
            const pending = (extension: Readonly<Extension>): boolean => {
                // Without a read the publish response is all there is, and it says nothing about the
                // review, so it is as unresolved as an actual review. A conflicting namespace outlives
                // any poll (see statusOf); only its files are still coming.
                const unresolved =
                    !readBack || (extension.reviewStatus === 'under_review' && !extension.namespaceOwnershipConflict);
                if (unresolved) {
                    iconWaitFrom = Date.now();
                    return Date.now() - started < REVIEW_POLL_TIMEOUT_MS;
                }
                return !extension.files?.icon && Date.now() - iconWaitFrom < ASSET_POLL_TIMEOUT_MS;
            };

            let current = initial;
            while (pending(current) && !stopped.current) {
                await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
                if (stopped.current) {
                    return;
                }
                try {
                    const fresh = await readPublished(initial.namespace, initial.name, READ_SHARE_MS);
                    if (!fresh) {
                        continue;
                    }
                    current = fresh;
                    readBack = true;
                } catch {
                    // A failed read says nothing about the package; wait for the next one.
                    continue;
                }
                update(id, {
                    extension: current,
                    status: statusOf(current),
                    error: current.reviewStatus === 'rejected' ? current.reviewMessage : undefined,
                    awaitingIcon: !current.files?.icon
                });
            }
            // Whatever the loop ended on, nothing more is coming: stop promising an icon.
            update(id, { awaitingIcon: false });
        },
        [readPublished, update]
    );

    /** The publish response carries none of that, so read the extension back; undefined if that fails. */
    const hydrate = useCallback(
        async (extension: Readonly<Extension>): Promise<Readonly<Extension> | undefined> => {
            try {
                return await readPublished(extension.namespace, extension.name, 0);
            } catch {
                return undefined;
            }
        },
        [readPublished]
    );

    const upload = useCallback(
        async (id: number, file: File) => {
            try {
                let result: Readonly<Extension>;
                try {
                    result = await publishPackage(file);
                } catch (err) {
                    // A first-time publisher has no namespace yet; create it and publish again.
                    const namespace = unknownNamespace(err);
                    if (!namespace) {
                        throw err;
                    }
                    await createNamespace(namespace);
                    result = await publishPackage(file);
                }
                const fresh = await hydrate(result);
                const extension = fresh ?? result;
                update(id, {
                    extension,
                    status: statusOf(extension),
                    error: extension.reviewStatus === 'rejected' ? extension.reviewMessage : undefined,
                    awaitingIcon: !extension.files?.icon
                });
                await pollUntilSettled(id, extension, { readBack: fresh !== undefined });
            } catch (err) {
                if (!stopped.current) {
                    update(id, { status: 'failed', error: formatError(err) });
                    // The card keeps the record, but a rejected publish is worth interrupting for:
                    // this is the same dialog every other failed request in the app raises.
                    handleError(err);
                }
            }
        },
        [update, pollUntilSettled, publishPackage, createNamespace, hydrate, handleError]
    );

    /** The ceiling as the registry reports it now, falling back to what the app started with. */
    const currentCeiling = useCallback(
        async (fallback: number): Promise<number> => {
            try {
                // revalidate, or the five-minute cache hands back the very value being bypassed
                const version = await service.getRegistryVersion(new AbortController(), { revalidate: true });
                return version.maxExtensionSizeCeiling ?? fallback;
            } catch {
                // Refusing on the stale value is still better than letting a doomed upload run.
                return fallback;
            }
        },
        [service]
    );

    const publish = useCallback(
        async (files: File[]) => {
            if (!user) {
                return;
            }
            const candidates = files.filter(isVsixFile);
            // Checked against the ceiling, not the default: a namespace with a size override may
            // publish more than the default allows, and only the registry knows which applies - it
            // cannot tell until it has parsed the package. Above the ceiling no namespace can
            // publish, so rejecting here wastes nobody's bandwidth. Unknown limit (the version has
            // not loaded): let the server say.
            //
            // The ceiling arrives once when the app starts and is never refreshed, so a tab opened
            // before an admin granted the override would otherwise keep refusing the very package
            // that override was created for. Re-read it before refusing anything - only then, so the
            // common path still costs nothing.
            let ceiling = maxSize;
            if (ceiling !== undefined && candidates.some(file => file.size > ceiling!)) {
                ceiling = await currentCeiling(ceiling);
            }
            const queued = candidates.map(file => {
                // the limit it exceeded, so the message can name it without widening the type again
                const exceeded = ceiling !== undefined && file.size > ceiling ? ceiling : undefined;
                return {
                    id: nextId.current++,
                    fileName: file.name,
                    size: file.size,
                    status: exceeded !== undefined ? ('failed' as const) : ('uploading' as const),
                    error: exceeded !== undefined ? `Larger than the ${formatFileSize(exceeded)} limit.` : undefined,
                    file
                };
            });
            if (queued.length === 0) {
                // Dropping a folder, or anything else that is not a package, would otherwise look
                // like the app simply ignored the drop.
                if (files.length > 0) {
                    handleError({ error: 'Only .vsix packages can be published.' });
                }
                return;
            }
            setItems(current => [...queued.map(({ file: _file, ...item }) => item), ...current]);
            queued.forEach(({ id, file, status }) => {
                if (status === 'uploading') {
                    upload(id, file);
                }
            });
        },
        [user, upload, handleError, maxSize, currentCeiling]
    );

    const clearFinished = useCallback(() => setItems(current => current.filter(item => !isFinished(item))), []);

    return (
        <PublishQueueContext.Provider value={{ items, publish, clearFinished }}>
            {children}
        </PublishQueueContext.Provider>
    );
};
