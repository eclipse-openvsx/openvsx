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
import { usePageSearchBar, useSearchBar } from '../../../src/context/search/page-search-bar-context';
import { useSearchFocus } from '../../../src/context/search/search-focus-context';

/**
 * Stands in for the nav bar's search field: shows whether a page search bar is registered (so the
 * real field would hide) and how many focus requests were made, and makes one when clicked, as '/' does.
 * Query it with `getByTestId('nav-field')`.
 */
export const NavFieldProbe = () => {
    const { hasPageSearchBar } = usePageSearchBar();
    const { searchFocusSignal } = useSearchFocus();
    return (
        <button
            data-testid='nav-field'
            data-page-search-bar={hasPageSearchBar}
            data-focus-requests={searchFocusSignal.signal}
            onClick={searchFocusSignal.emit}>
            focus search
        </button>
    );
};

/** A page search bar built on `useSearchBar`, labelled 'page search' and showing whether it is registered. */
export const PageSearchBarProbe = () => {
    const ref = useRef<HTMLInputElement>(null);
    const registered = useSearchBar(ref);
    return <input ref={ref} aria-label='page search' data-registered={registered} />;
};
