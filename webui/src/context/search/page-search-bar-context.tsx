/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import {
    createContext,
    FunctionComponent,
    ReactNode,
    RefObject,
    useCallback,
    useContext,
    useLayoutEffect,
    useMemo,
    useRef,
    useState
} from 'react';
import { useInView } from '../../hooks/use-in-view';
import { useSignalEffect } from '../../hooks/use-signal-effect';
import { moveCursorToEnd } from '../../utils';
import { useSearchFocus } from './search-focus-context';

/**
 * Tracks whether a page-level search bar (e.g. the home hero) is active — the
 * nav bar derives its own field's visibility from it.
 */

export interface PageSearchBarValue {
    // A page-level search bar is registered — the nav bar hides its own field.
    hasPageSearchBar: boolean;
    // Synchronous read — `hasPageSearchBar` lags one render when a bar (un)registers
    // in the same commit that emits a focus signal.
    isPageSearchBarActive: () => boolean;
    registerPageSearchBar: () => () => void;
}

const PageSearchBarContext = createContext<PageSearchBarValue>({
    hasPageSearchBar: false,
    isPageSearchBarActive: () => false,
    registerPageSearchBar: () => () => {}
});

// eslint-disable-next-line react-refresh/only-export-components
export function usePageSearchBar(): PageSearchBarValue {
    return useContext(PageSearchBarContext);
}

const focusAtEnd = (node: HTMLElement) => {
    node.focus();
    if (node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement) {
        moveCursorToEnd(node);
    }
};

/**
 * Makes the element behind `ref` the page's search bar while it is in the viewport: the
 * nav bar hides its own field and hands focus requests (e.g. the '/' shortcut) over to it,
 * and gets focus back when the bar scrolls away or unmounts while focused. Returns whether
 * the bar is registered; callers gate their view-transition name on it. Whether any bar is
 * registered is read with `usePageSearchBar`.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useSearchBar(ref: RefObject<HTMLElement>): boolean {
    const { registerPageSearchBar } = usePageSearchBar();
    const { searchFocusSignal } = useSearchFocus();
    // Follows the bar out of view, and assumes it in view until measured so the nav field doesn't flash on load.
    const inView = useInView(ref, { once: false, rootMargin: '0px', initialInView: true });

    // `isRegistered` (not `inView`) is what callers see: a guarded skip below (viewport shrunk
    // while focused) keeps registration alive for an instant where `inView` itself still reads
    // false, so returning `inView` directly would misreport "unregistered" during that window.
    const [isRegistered, setIsRegistered] = useState(false);
    const unregisterRef = useRef<(() => void) | null>(null);
    const lastViewportHeight = useRef(typeof window === 'undefined' ? 0 : window.innerHeight);

    const handOff = useCallback(() => {
        if (!unregisterRef.current) {
            return;
        }
        const hadFocus = ref.current != null && document.activeElement === ref.current;
        unregisterRef.current();
        unregisterRef.current = null;
        setIsRegistered(false);
        // Scrolled away or unmounted mid-typing: hand focus to the nav field, or it falls to <body>.
        if (hadFocus) searchFocusSignal.emit();
    }, [ref, searchFocusSignal.emit]);

    // Layout effect so the count is updated before other layout effects in the same commit.
    useLayoutEffect(() => {
        if (inView) {
            lastViewportHeight.current = window.innerHeight;
            if (!unregisterRef.current) {
                unregisterRef.current = registerPageSearchBar();
                setIsRegistered(true);
            }
            return;
        }
        // A mobile virtual keyboard opening shrinks the viewport around a field that never
        // actually moved, which can report it as having left the viewport although the user is
        // still typing into it. Ignore that specific case rather than yanking focus away - this
        // can still misfire if a real scroll happens to coincide with the browser chrome
        // resizing (e.g. hiding the address bar), but that is a much rarer combination.
        const hasFocus = ref.current != null && document.activeElement === ref.current;
        if (hasFocus && window.innerHeight < lastViewportHeight.current) {
            return;
        }
        handOff();
    }, [inView, ref, registerPageSearchBar, handOff]);

    // Unmounting while registered always hands focus back, regardless of the guard above.
    // A layout effect: by the time a passive effect's cleanup would run, React has already
    // detached `ref.current` and the browser has already moved focus off the removed node.
    useLayoutEffect(() => handOff, [handOff]);

    useSignalEffect(
        searchFocusSignal,
        useCallback(() => {
            if (isRegistered && ref.current) focusAtEnd(ref.current);
        }, [isRegistered, ref])
    );

    return isRegistered;
}

/**
 * The mirror of `useSearchBar`'s own handoff: give up focus to a page search bar that just
 * registered, if the element behind `ref` had it (e.g. the nav field, about to go `inert`).
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useYieldSearchFocus(ref: RefObject<HTMLElement>): void {
    const { hasPageSearchBar } = usePageSearchBar();
    const { searchFocusSignal } = useSearchFocus();
    useLayoutEffect(() => {
        if (hasPageSearchBar && ref.current != null && document.activeElement === ref.current) {
            searchFocusSignal.emit();
        }
    }, [hasPageSearchBar, ref, searchFocusSignal.emit]);
}

export const PageSearchBarProvider: FunctionComponent<{ children: ReactNode }> = ({ children }) => {
    // Ref for synchronous reads during a commit; the state mirror drives re-renders.
    const pageSearchBarCount = useRef(0);
    const [hasPageSearchBar, setHasPageSearchBar] = useState(false);
    const registerPageSearchBar = useCallback(() => {
        pageSearchBarCount.current++;
        setHasPageSearchBar(true);
        return () => {
            pageSearchBarCount.current--;
            setHasPageSearchBar(pageSearchBarCount.current > 0);
        };
    }, []);
    const isPageSearchBarActive = useCallback(() => pageSearchBarCount.current > 0, []);
    const value = useMemo(
        () => ({ hasPageSearchBar, isPageSearchBarActive, registerPageSearchBar }),
        [hasPageSearchBar, isPageSearchBarActive, registerPageSearchBar]
    );
    return <PageSearchBarContext.Provider value={value}>{children}</PageSearchBarContext.Provider>;
};
