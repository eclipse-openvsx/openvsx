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

import { FunctionComponent, useLayoutEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router';

// BrowserRouter never scrolls on push, so reset to the top on forward navigations only: POP
// keeps the browser's restoration, and same-path param updates must not jump (hence keying on
// pathname alone). Links that swap content in place opt out via state `{ preserveScroll: true }`.
// TODO Use react-router's <ScrollRestoration/> once on a data router — also fixes the pop
// landing clamped to the outgoing page's height: https://github.com/eclipse-openvsx/openvsx/issues/2079
export const ScrollRestoration: FunctionComponent = () => {
    const { pathname, state } = useLocation();
    const navigationType = useNavigationType();
    const preserveScroll = (state as { preserveScroll?: boolean } | null)?.preserveScroll;

    useLayoutEffect(() => {
        if (navigationType !== 'POP' && !preserveScroll) {
            window.scrollTo({ top: 0, left: 0 });
        }
    }, [pathname]);

    return null;
};
