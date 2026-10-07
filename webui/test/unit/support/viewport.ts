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

import { act } from '@testing-library/react';
import { ViewportObserver } from '../../../src/context/viewport-observer-context';

export interface TestViewport {
    /** Pass to `TestProviders` (`viewport`) so `useInView` watches through this fake. */
    observer: ViewportObserver;
    /** The elements currently observed, with the root margin each was observed with. */
    observed: () => Array<{ node: Element; rootMargin: string }>;
    /** Move every observed element into or out of view. */
    setInView: (inView: boolean) => void;
}

/**
 * A viewport the test drives by hand, standing in for the browser's `IntersectionObserver`
 * (which jsdom lacks). Elements start out of view unless `initiallyInView`.
 */
export function createTestViewport({ initiallyInView = false } = {}): TestViewport {
    const watched = new Map<Element, { onChange: (inView: boolean) => void; rootMargin: string }>();
    return {
        observer: {
            observe(node, onChange, rootMargin, once = false) {
                onChange(initiallyInView);
                if (once && initiallyInView) {
                    // Mirrors browserViewportObserver: already latched, nothing left to watch.
                    return () => {};
                }
                watched.set(node, { onChange, rootMargin });
                return () => watched.delete(node);
            }
        },
        observed: () => [...watched].map(([node, { rootMargin }]) => ({ node, rootMargin })),
        setInView: inView => act(() => watched.forEach(({ onChange }) => onChange(inView)))
    };
}

/** The harness default: everything on screen. Stateless, so one value serves every render and test. */
export const allInViewObserver: ViewportObserver = {
    observe(_node, onChange) {
        onChange(true);
        return () => {};
    }
};
