/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useEffect, useState } from 'react';
import { Box } from '@mui/material';
import { useLocation } from 'react-router-dom';
import { addQuery } from '../../utils';
import { ExtensionCategory, SortOrder, SortBy } from '../../extension-registry-types';
import { ExtensionList } from './extension-list';
import { ExtensionListHeader } from './extension-list-header';

export const ExtensionListContainer: FunctionComponent = () => {

    const [searchQuery, setSearchQuery] = useState('');
    const [category, setCategory] = useState<ExtensionCategory | ''>('');
    const [resultNumber, setResultNumber] = useState(0);
    const [sortBy, setSortBy] = useState<SortBy>('relevance');
    const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
    const [searchDebounceTime, setSearchDebounceTime] = useState(0);

    const { pathname, search } = useLocation();

    useEffect(() => {
        const searchParams = new URLSearchParams(search);
        setSearchQuery(searchParams.get('search') ?? '');
        setCategory(searchParams.get('category') as ExtensionCategory ?? '');
        setSortBy(searchParams.get('sortBy') as SortBy ?? 'relevance');
        setSortOrder(searchParams.get('sortOrder') as SortOrder ?? 'desc');
    }, []);

    const onSearchChanged = (searchQuery: string): void => {
        setSearchQuery(searchQuery);
        setSearchDebounceTime(1000);
        updateURL(searchQuery, category, sortBy, sortOrder);
    };

    const onSearchSubmit = (searchQuery: string): void => {
        setSearchQuery(searchQuery);
        setSearchDebounceTime(0);
    };

    const onCategoryChanged = (category: ExtensionCategory | ''): void => {
        setCategory(category);
        updateURL(searchQuery, category, sortBy, sortOrder);
    };

    const onSortByChanged = (sortBy: SortBy): void => {
        setSortBy(sortBy);
        updateURL(searchQuery, category, sortBy, sortOrder);
    };

    const onSortOrderChanged = (sortOrder: SortOrder): void => {
        setSortOrder(sortOrder);
        updateURL(searchQuery, category, sortBy, sortOrder);
    };

    const updateURL = (searchQuery: string, category: ExtensionCategory | '', sortBy?: SortBy, sortOrder?: SortOrder): void => {
        const queries: { key: string, value: string }[] = [];
        if (searchQuery) {
            queries.push({ key: 'search', value: searchQuery });
        }
        if (category) {
            queries.push({ key: 'category', value: category });
        }
        if (sortBy) {
            queries.push({ key: 'sortBy', value: sortBy });
        }
        if (sortOrder) {
            queries.push({ key: 'sortOrder', value: sortOrder });
        }
        const url = addQuery('', queries) || pathname;
        history.replaceState(null, '', url);
    };

    const handleUpdate = (resultNumber: number): void => setResultNumber(resultNumber);

    return <Box display='flex' flexDirection='column' >
        <ExtensionListHeader
            resultNumber={resultNumber}
            searchQuery={searchQuery}
            category={category}
            sortBy={sortBy}
            sortOrder={sortOrder}
            onSearchChanged={onSearchChanged}
            onSearchSubmit={onSearchSubmit}
            onCategoryChanged={onCategoryChanged}
            onSortByChanged={onSortByChanged}
            onSortOrderChanged={onSortOrderChanged} />
        <ExtensionList
            filter={{ query: searchQuery, category, offset: 0, size: 10, sortBy, sortOrder }}
            debounceTime={searchDebounceTime}
            onUpdate={handleUpdate}
        />
    </Box>;
};
