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

import { createContext, useContext } from 'react';

export interface ViewportObserver {
    /**
     * Reports whether `node` is within `rootMargin` of the viewport — once right away,
     * then on every change — until the returned function is called.
     * <p>
     * `once`: the caller only cares about the first time `node` is in view, so if the
     * right-away report already says so, no live watch is worth setting up for it.
     */
    observe: (node: Element, onChange: (inView: boolean) => void, rootMargin: string, once?: boolean) => () => void;
}

const isOnScreen = (node: Element): boolean => {
    const rect = node.getBoundingClientRect();
    return rect.bottom > 0 && rect.top < window.innerHeight && rect.right > 0 && rect.left < window.innerWidth;
};

/**
 * Backed by the browser's `IntersectionObserver`; where that is missing (server rendering,
 * older browsers) everything counts as in view, so nothing waits on it indefinitely.
 */
export const browserViewportObserver: ViewportObserver = {
    observe(node, onChange, rootMargin, once = false) {
        if (typeof IntersectionObserver === 'undefined') {
            onChange(true);
            return () => {};
        }
        // The observer's own first callback is async, so measure directly.
        const visible = isOnScreen(node);
        onChange(visible);
        if (once && visible) {
            // Already latched by the measurement above - nothing left to watch for.
            return () => {};
        }
        const observer = new IntersectionObserver(entries => onChange(entries[entries.length - 1].isIntersecting), {
            rootMargin
        });
        observer.observe(node);
        return () => observer.disconnect();
    }
};

/** How `useInView` watches elements; the app uses the browser default, tests provide a controllable one. */
export const ViewportObserverContext = createContext<ViewportObserver>(browserViewportObserver);

export const useViewportObserver = (): ViewportObserver => useContext(ViewportObserverContext);
