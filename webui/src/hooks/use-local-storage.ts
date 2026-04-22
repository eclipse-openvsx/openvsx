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
 *****************************************************************************/

import { Dispatch, SetStateAction, useCallback, useEffect, useState } from 'react';

/**
 * Reads a value from localStorage and deserializes it.
 * Returns `fallback` when the key is missing or the stored value cannot be parsed.
 */
function readStorage<T>(key: string, fallback: T): T {
    try {
        const raw = localStorage.getItem(key);
        return raw === null ? fallback : (JSON.parse(raw) as T);
    } catch {
        return fallback;
    }
}

/**
 * A useState-compatible hook that persists its value in localStorage.
 *
 * - Initialises from localStorage (falling back to `initialValue`).
 * - Keeps multiple tabs in sync by listening to the `storage` event.
 * - The setter accepts both a direct value and an updater function, matching
 *   the full useState API.
 */
export function useLocalStorage<T>(key: string, initialValue: T): [T, Dispatch<SetStateAction<T>>] {
    const [state, setState] = useState<T>(() => readStorage(key, initialValue));

    // Persist every state change to localStorage.
    useEffect(() => {
        try {
            localStorage.setItem(key, JSON.stringify(state));
        } catch {
            // Quota exceeded or private-browsing restrictions — silently ignore.
        }
    }, [key, state]);

    // Keep tabs in sync: when another tab writes to the same key, update state.
    useEffect(() => {
        const onStorage = (event: StorageEvent) => {
            if (event.key !== key || event.storageArea !== localStorage) {
                return;
            }
            setState(event.newValue === null ? initialValue : (JSON.parse(event.newValue) as T));
        };

        globalThis.addEventListener('storage', onStorage);
        return () => globalThis.removeEventListener('storage', onStorage);
    }, [key, initialValue]);

    const setValue: Dispatch<SetStateAction<T>> = useCallback((action) => {
        setState(prev => (typeof action === 'function' ? (action as (prev: T) => T)(prev) : action));
    }, []);

    return [state, setValue];
}
