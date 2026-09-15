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

import { createContext, FunctionComponent, ReactNode, useContext, useEffect, useMemo, useState } from 'react';

/**
 * The tint region an extension detail page declares while mounted. The page
 * only describes it; the nav bar compares the depth against its own scroll
 * position to decide when to wear the color.
 */
export interface ExtensionTint {
    // Gallery color the nav wears while the region backs it, flipping its
    // content to the contrast color. Null for default-colored bands, which
    // keep the nav on theme colors.
    color: string | null;
    // Document offset where the region ends; scrolled past it the nav returns
    // to theme colors.
    depth: number;
}

// What the current page asks of the navbar chrome, declared while mounted:
// a tint over the page's gallery band, and extra depth for the blur fan to
// back sections pinned under the bar.
interface NavbarChrome {
    tint: ExtensionTint | null;
    setTint: (tint: ExtensionTint | null) => void;
    blurDepth: number;
    setBlurDepth: (depth: number) => void;
}

const NavbarChromeContext = createContext<NavbarChrome>({
    tint: null,
    setTint: () => {},
    blurDepth: 0,
    setBlurDepth: () => {}
});

export const NavbarChromeProvider: FunctionComponent<{ children: ReactNode }> = ({ children }) => {
    const [tint, setTint] = useState<ExtensionTint | null>(null);
    const [blurDepth, setBlurDepth] = useState(0);
    const value = useMemo(() => ({ tint, setTint, blurDepth, setBlurDepth }), [tint, blurDepth]);
    return <NavbarChromeContext.Provider value={value}>{children}</NavbarChromeContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components
export function useExtensionTint(): ExtensionTint | null {
    return useContext(NavbarChromeContext).tint;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSetExtensionTint(): (tint: ExtensionTint | null) => void {
    return useContext(NavbarChromeContext).setTint;
}

/** The navbar reads how far pages want the blur fan extended. */
// eslint-disable-next-line react-refresh/only-export-components
export function useNavbarBlurExtent(): number {
    return useContext(NavbarChromeContext).blurDepth;
}

/**
 * Extends the navbar's blur fan by the given depth (px) while the calling component is mounted —
 * for page sections pinned under the navbar (sticky headers, tab rows) that float on the fan.
 * Only one mounted component may extend the fan at a time. There is no ownership stack: the last
 * writer sets the depth, and whichever writer unmounts first resets it to 0, so a second concurrent
 * writer would lose the fan while still mounted.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useExtendNavbarBlur(depth: number): void {
    const { setBlurDepth } = useContext(NavbarChromeContext);
    useEffect(() => {
        setBlurDepth(depth);
        return () => setBlurDepth(0);
    }, [depth, setBlurDepth]);
}
