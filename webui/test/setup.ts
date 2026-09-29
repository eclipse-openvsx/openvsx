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

import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Vitest globals are off, so React Testing Library cannot auto-register cleanup.
// Unmount rendered trees between tests to keep the jsdom document isolated.
afterEach(() => {
    cleanup();
});

// jsdom's getComputedStyle chokes on some MUI-generated relative font sizes
// ("object null is not iterable" in its font-size resolver), and RTL role
// queries call it on every candidate element. Fall back to an empty style so
// a CSS edge case cannot crash accessibility checks.
const originalGetComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = (element: Element, pseudoElt?: string | null) => {
    try {
        return originalGetComputedStyle(element, pseudoElt);
    } catch {
        return document.createElement('div').style;
    }
};
