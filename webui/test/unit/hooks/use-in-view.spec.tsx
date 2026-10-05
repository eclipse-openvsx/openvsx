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

import { useRef } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useInView, UseInViewOptions } from '../../../src/hooks/use-in-view';
import { browserViewportObserver, ViewportObserverContext } from '../../../src/context/viewport-observer-context';
import { createTestViewport, TestViewport } from '../support/viewport';

/** Renders the hook's ref on a `div` (or on nothing) and shows `inView`. */
const InViewProbe = ({ options, withElement }: { options: UseInViewOptions; withElement: boolean }) => {
    const ref = useRef<HTMLDivElement>(null);
    const inView = useInView(ref, options);
    return (
        <>
            {withElement && <div ref={ref} />}
            <output data-testid='in-view'>{String(inView)}</output>
        </>
    );
};

function renderInView(options: UseInViewOptions, viewport: TestViewport, { withElement = true } = {}) {
    render(
        <ViewportObserverContext.Provider value={viewport.observer}>
            <InViewProbe options={options} withElement={withElement} />
        </ViewportObserverContext.Provider>
    );
    return { inView: () => screen.getByTestId('in-view').textContent === 'true' };
}

describe('useInView', () => {
    it('starts observing 300px before the element is on screen by default', () => {
        const viewport = createTestViewport();
        renderInView({}, viewport);

        expect(viewport.observed()[0].rootMargin).toBe('300px');
    });

    it('stays in view once seen by default', () => {
        const viewport = createTestViewport();
        const { inView } = renderInView({}, viewport);

        viewport.setInView(true);
        viewport.setInView(false);

        expect(inView()).toBe(true);
        expect(viewport.observed()).toHaveLength(0); // nothing left to watch
    });

    it('follows the element in and out of view with `once: false`', () => {
        const viewport = createTestViewport();
        const { inView } = renderInView({ once: false }, viewport);

        viewport.setInView(true);
        expect(inView()).toBe(true);
        viewport.setInView(false);
        expect(inView()).toBe(false);
    });

    it('keeps `initialInView` when there is no element', () => {
        const viewport = createTestViewport();

        expect(renderInView({ once: false, initialInView: true }, viewport, { withElement: false }).inView()).toBe(
            true
        );
        expect(viewport.observed()).toHaveLength(0);
    });

    it('observes nothing when disabled', () => {
        const viewport = createTestViewport();
        renderInView({ enabled: false }, viewport);

        expect(viewport.observed()).toHaveLength(0);
    });
});

/** jsdom has no IntersectionObserver; this records what the browser observer asks of one. */
class FakeIntersectionObserver implements IntersectionObserver {
    static instances: FakeIntersectionObserver[] = [];
    readonly root = null;
    readonly thresholds = [];
    readonly rootMargin: string;
    observed: Element[] = [];
    disconnected = false;
    constructor(
        readonly callback: IntersectionObserverCallback,
        options?: IntersectionObserverInit
    ) {
        this.rootMargin = options?.rootMargin ?? '';
        FakeIntersectionObserver.instances.push(this);
    }
    observe(node: Element) {
        this.observed.push(node);
    }
    unobserve() {}
    disconnect() {
        this.disconnected = true;
    }
    takeRecords(): IntersectionObserverEntry[] {
        return [];
    }
    report(isIntersecting: boolean) {
        this.callback([{ isIntersecting, target: this.observed[0] } as IntersectionObserverEntry], this);
    }
}

describe('browserViewportObserver', () => {
    beforeEach(() => {
        FakeIntersectionObserver.instances = [];
        vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    });
    afterEach(() => vi.unstubAllGlobals());

    it('reports a measurement right away, then what the IntersectionObserver sees', () => {
        const node = document.createElement('div');
        const onChange = vi.fn();

        browserViewportObserver.observe(node, onChange, '300px');
        expect(onChange).toHaveBeenLastCalledWith(false); // jsdom lays nothing out

        const [observer] = FakeIntersectionObserver.instances;
        expect(observer.observed).toEqual([node]);
        expect(observer.rootMargin).toBe('300px');
        observer.report(true);
        expect(onChange).toHaveBeenLastCalledWith(true);
    });

    it('disconnects when stopped', () => {
        const stop = browserViewportObserver.observe(document.createElement('div'), () => {}, '0px');

        stop();

        expect(FakeIntersectionObserver.instances[0].disconnected).toBe(true);
    });

    it('counts everything as in view where the browser has no IntersectionObserver', () => {
        vi.unstubAllGlobals();
        const onChange = vi.fn();

        browserViewportObserver.observe(document.createElement('div'), onChange, '300px');

        expect(onChange).toHaveBeenCalledWith(true);
    });
});
