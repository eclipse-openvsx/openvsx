/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useState, useContext, createContext, useEffect, useRef, useCallback, ReactNode } from 'react';
import { Typography, Box } from '@mui/material';
import { useParams, useNavigate } from 'react-router-dom';
import { PublisherInfo } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { StyledInput } from './namespace-input';
import { SearchListContainer } from './search-list-container';
import { PublisherDetails } from './publisher-details';
import { AdminDashboardRoutes } from './admin-dashboard';

export const UpdateContext = createContext({ handleUpdate: () => { } });
export const PublisherAdmin: FunctionComponent = () => {
    const { publisher: publisherParam } = useParams<{ publisher: string }>();
    const navigate = useNavigate();
    const { pageSettings, service, user, handleError } = useContext(MainContext);

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

    const [loading, setLoading] = useState(false);

    const [publisher, setPublisher] = useState<PublisherInfo | undefined>();
    const [notFound, setNotFound] = useState('');

    const fetchPublisher = useCallback(async (publisherName: string) => {
        try {
            setLoading(true);
            if (publisherName === '') {
                setNotFound('');
                setPublisher(undefined);
            } else {
                const pub = await service.admin.getPublisherInfo(abortController.current, 'github', publisherName);
                setNotFound('');
                setPublisher(pub);
            }
            setLoading(false);
        } catch (err) {
            if (err?.status === 404) {
                setNotFound(publisherName);
                setPublisher(undefined);
            } else {
                handleError(err);
            }
            setLoading(false);
        }
    }, [service, handleError]);

    useEffect(() => {
        if (publisherParam) {
            fetchPublisher(publisherParam);
        }
    }, [publisherParam, fetchPublisher]);

    const handleSubmit = (inputValue: string) => {
        if (inputValue) {
            navigate(`${AdminDashboardRoutes.PUBLISHER_ADMIN}/${inputValue}`);
        } else {
            navigate(AdminDashboardRoutes.PUBLISHER_ADMIN);
        }
    };

    const handleUpdate = () => {
        if (publisherParam) {
            fetchPublisher(publisherParam);
        }
    };

    let listContainer: ReactNode = '';
    if (publisher && pageSettings && user) {
        listContainer = <UpdateContext.Provider value={{ handleUpdate }}>
            <PublisherDetails publisherInfo={publisher} />
        </UpdateContext.Provider>;
    } else if (notFound) {
        listContainer = <Box display='flex' flexDirection='column'>
            <Typography variant='body1' color='error'>
                Publisher {notFound} not found.
            </Typography>
        </Box>;
    }

    return <SearchListContainer
        searchContainer={
            [<StyledInput placeholder='Publisher Name' key='pi' value={publisherParam || ''} onSubmit={handleSubmit} onChange={() => {}} />]
        }
        listContainer={listContainer}
        loading={loading}
    />;
};