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

import { RefObject, useLayoutEffect, useState } from 'react';
import { useViewportObserver } from '../context/viewport-observer-context';

/** Load a little before the element is actually on screen, so scrolling doesn't reveal placeholders. */
const DEFAULT_ROOT_MARGIN = '300px';

/**
 * Tells you whether the element behind `ref` has come near the viewport, for deferring work until
 * it is worth doing. Latches by default: once in view it stays in view, since the work it usually
 * gates is loading something; pass `once: false` to follow the element in and out of view instead.
 *
 * Reads the element from `ref` when observing starts, so it must not be replaced while observed:
 * remount the component (e.g. with a `key`) to observe a different one. Pass `enabled: false` where there turns out to be
 * nothing to defer, and no element is observed at all. Watching goes through `ViewportObserverContext`.
 */
export function useInView(
    ref: RefObject<Element>,
    { enabled = true, rootMargin = DEFAULT_ROOT_MARGIN, once = true, initialInView = false }: UseInViewOptions = {}
): boolean {
    const { observe } = useViewportObserver();
    const [inView, setInView] = useState(initialInView);
    const latched = once && inView;

    // Layout effect so an element already on screen is seeded before the browser paints the
    // placeholder it would otherwise show for a frame.
    useLayoutEffect(() => {
        const node = ref.current;
        if (!enabled || latched || node == null) {
            return;
        }
        return observe(
            node,
            visible => {
                // A latching hook only ever takes in the element coming into view.
                if (visible || !once) setInView(visible);
            },
            rootMargin
        );
    }, [observe, ref, enabled, latched, once, rootMargin]);

    return inView;
}

export interface UseInViewOptions {
    /** Observe at all. `false` where the caller already knows the deferred work is not needed. */
    enabled?: boolean;
    rootMargin?: string;
    /** Stay in view once seen (the default); `false` follows the element in and out of view. */
    once?: boolean;
    /** Assumed until the element is measured, and kept when there is no element. */
    initialInView?: boolean;
}
