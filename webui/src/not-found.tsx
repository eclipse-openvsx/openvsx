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

import { FunctionComponent, useRef } from 'react';
import { Box, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import { useLocation } from 'react-router';
import { PageContainer } from './components/page-container';
import { SearchBox } from './components/search-box';
import { OpenVsxMark } from './components/openvsx-mark';
import { useSearch } from './hooks/use-search';
import { useSearchBar } from './context/search/page-search-bar-context';
import { MONO_FONT } from './default/theme';

const NotFoundPage = styled(PageContainer)({
    flexGrow: 1,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    animation: 'fadeIn .25s ease'
});

const Content = styled(Box)({
    width: '100%',
    maxWidth: '41.25rem',
    textAlign: 'center'
});

const Code = styled(Typography)(({ theme }) => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.08em',
    fontFamily: MONO_FONT,
    fontSize: '6.5rem',
    fontWeight: 600,
    lineHeight: 1,
    letterSpacing: '-0.04em',
    color: theme.palette.text.primary,
    userSelect: 'none',
    [theme.breakpoints.down('sm')]: { fontSize: '4.5rem' }
})) as typeof Typography;

const Heading = styled(Typography)(({ theme }) => ({
    marginTop: '1.5rem',
    fontSize: '1.75rem',
    fontWeight: 700,
    letterSpacing: '-0.01em',
    [theme.breakpoints.down('sm')]: { fontSize: '1.375rem' }
})) as typeof Typography;

const Lead = styled(Typography)(({ theme }) => ({
    margin: '0.5rem 0 2rem',
    fontSize: '0.9375rem',
    lineHeight: 1.55,
    color: theme.palette.text.secondary
}));

// A malformed escape (e.g. a stray '%') would make decodeURIComponent throw.
const safeDecode = (value: string): string => {
    try {
        return decodeURIComponent(value);
    } catch {
        return value;
    }
};

/** The page's search bar, starting from `defaultQuery`. */
const NotFoundSearch: FunctionComponent<{ defaultQuery: string }> = ({ defaultQuery }) => {
    const { search } = useSearch();
    const inputRef = useRef<HTMLInputElement>(null);
    const isActiveSearchBar = useSearchBar(inputRef);
    return (
        <SearchBox
            ref={inputRef}
            defaultValue={defaultQuery}
            onSearch={term => search({ query: term })}
            viewTransitionName={isActiveSearchBar ? 'vt-search' : undefined}
        />
    );
};

export const NotFound: FunctionComponent = () => {
    const { pathname } = useLocation();
    // The last path segment is the likeliest thing the visitor was looking for.
    // Split before decoding, so an encoded '/' stays inside its segment.
    const searchTerm = safeDecode(pathname.split('/').filter(Boolean).pop() ?? '');

    return (
        <NotFoundPage component='main'>
            <Content>
                <Code component='p' aria-hidden>
                    4
                    <OpenVsxMark style={{ height: '0.8em' }} />4
                </Code>
                <Heading variant='h1'>Page not found</Heading>
                <Lead>
                    The link may be broken, or the extension or namespace it pointed to may have been renamed or
                    removed. Try searching for it instead.
                </Lead>
                {/* Keyed so the uncontrolled field refills on another missing path. */}
                <NotFoundSearch key={searchTerm} defaultQuery={searchTerm} />
            </Content>
        </NotFoundPage>
    );
};
