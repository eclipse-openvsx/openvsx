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

import { useEffect, useState } from 'react';

/** Load a little before the element is actually on screen, so scrolling doesn't reveal placeholders. */
const DEFAULT_ROOT_MARGIN = '300px';

/**
 * Tells you whether an element has come near the viewport, for deferring work until it is worth
 * doing. Latches: once in view it stays in view, since the work it gates is loading something.
 *
 * Attach the returned ref to the element; where `IntersectionObserver` is missing (server rendering,
 * older browsers) everything counts as in view, so the work is never deferred indefinitely. Pass
 * `enabled: false` where there turns out to be nothing to defer, and no element is observed at all.
 */
export function useInView({ enabled = true, rootMargin = DEFAULT_ROOT_MARGIN }: UseInViewOptions = {}): [
    (node: Element | null) => void,
    boolean
] {
    const [node, setNode] = useState<Element | null>(null);
    const [inView, setInView] = useState(typeof IntersectionObserver === 'undefined');

    useEffect(() => {
        if (!enabled || inView || node == null) {
            return;
        }
        // Seed synchronously: the observer's first callback is async, and an element already on
        // screen would otherwise show its placeholder for a frame.
        const rect = node.getBoundingClientRect();
        if (rect.bottom > 0 && rect.top < window.innerHeight && rect.right > 0 && rect.left < window.innerWidth) {
            setInView(true);
            return;
        }

        const observer = new IntersectionObserver(
            entries => {
                if (entries.some(entry => entry.isIntersecting)) {
                    setInView(true);
                }
            },
            { rootMargin }
        );
        observer.observe(node);
        return () => observer.disconnect();
    }, [node, enabled, inView, rootMargin]);

    return [setNode, inView];
}

export interface UseInViewOptions {
    /** Observe at all. `false` where the caller already knows the deferred work is not needed. */
    enabled?: boolean;
    rootMargin?: string;
}
