/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useEffect, useMemo } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { Box, Grid, CircularProgress, Container } from '@mui/material';
import { ExtensionListItem } from './extension-list-item';
import { ExtensionFilter } from '../../extension-registry-service';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { MainContext } from '../../context';
import { useSearch } from '../../hooks/extension-list/use-search';

export const ExtensionList: FunctionComponent<ExtensionListProps> = props => {
    const { handleError } = useContext(MainContext);

    const { data, isFetching, fetchNextPage, hasNextPage, error } = useSearch(props.filter);

    const extensions = useMemo(() => data?.pages.flatMap(p => p.extensions) ?? [], [data]);
    const totalSize = useMemo(() => data?.pages[0]?.totalSize ?? 0, [data]);

    useEffect(() => {
        props.onUpdate(totalSize);
    }, [totalSize]);

    useEffect(() => {
        if (error) handleError(error);
    }, [error]);

    const loadMore = (): Promise<void> => fetchNextPage().then(() => undefined);

    const extensionList = extensions.map((ext, idx) => (
        <ExtensionListItem
            idx={idx}
            extension={ext}
            filterSize={props.filter.size ?? 10}
            key={`${ext.namespace}.${ext.name}`} />
    ));

    const loader = <Box component='div' key='extension-list-loader' sx={{ display: 'flex', justifyContent: 'center', m: 3 }}>
        <CircularProgress size='3rem' color='secondary' />
    </Box>;

    return <>
        <DelayedLoadIndicator loading={isFetching}/>
        <InfiniteScroll
            loadMore={loadMore}
            hasMore={!!hasNextPage}
            loader={loader}
            threshold={200} >
            <Container maxWidth='xl'>
                <Grid container spacing={2} sx={{ justifyContent: 'center' }}>
                    {extensionList}
                </Grid>
            </Container>
        </InfiniteScroll>
    </>;
};

export interface ExtensionListProps {
    filter: ExtensionFilter;
    debounceTime: number;
    onUpdate: (resultNumber: number) => void;
}
