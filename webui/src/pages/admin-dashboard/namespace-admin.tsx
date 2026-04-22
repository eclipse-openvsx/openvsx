/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useState, useContext, useEffect, useRef, ReactNode } from 'react';
import { Typography, Box } from '@mui/material';
import { NamespaceDetail, NamespaceDetailConfigContext } from '../user/user-settings-namespace-detail';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Namespace, isError } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { StyledInput } from './namespace-input';
import { SearchListContainer } from './search-list-container';

export const NamespaceAdmin: FunctionComponent = props => {
    const { pageSettings, service, user, handleError } = useContext(MainContext);

    const [loading, setLoading] = useState(false);
    const [currentNamespace, setCurrentNamespace] = useState<Namespace | undefined>();
    const [notFound, setNotFound] = useState('');

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

    const handleDeleteNamespace = () => {
        setCurrentNamespace(undefined);
    };

    const fetchNamespace = async (namespaceName: string) => {
        if (!namespaceName) {
            setCurrentNamespace(undefined);
            setNotFound('');
            return;
        }
        try {
            setLoading(true);
            const namespace = await service.admin.getNamespace(abortController.current, namespaceName);
            if (isError(namespace)) {
                throw namespace;
            }
            setCurrentNamespace(namespace);
            setNotFound('');
            setLoading(false);
        } catch (err) {
            if (err && err.status === 404) {
                setNotFound(namespaceName);
                setCurrentNamespace(undefined);
            } else {
                handleError(err);
            }
            setLoading(false);
        }
    };

    const [inputValue, setInputValue] = useState('');
    const onChangeInput = (name: string) => {
        setInputValue(name);
    };

    const [creating, setCreating] = useState(false);
    const onCreate = async () => {
        try {
            setCreating(true);
            await service.admin.createNamespace(abortController.current, {
                name: inputValue
            });
            await fetchNamespace(inputValue);
        } catch (err) {
            handleError(err);
        } finally {
            setCreating(false);
        }
    };

    let listContainer: ReactNode = '';
    if (currentNamespace && pageSettings && user) {
        listContainer = <NamespaceDetailConfigContext.Provider value={{ defaultMemberRole: 'owner' }}>
            <NamespaceDetail
                setLoadingState={setLoading}
                onDelete={handleDeleteNamespace}
                namespace={currentNamespace}
                filterUsers={() => true}
                fixSelf={false}
            />
        </NamespaceDetailConfigContext.Provider>;
    } else if (notFound) {
        listContainer = <Box display='flex' flexDirection='column' justifyContent='center' alignItems='center'>
            <Typography variant='body1'>
                Namespace {notFound} not found. Do you want to create it?
            </Typography>
            <Box mt={3}>
                <ButtonWithProgress
                    working={creating}
                    onClick={onCreate}>
                    Create Namespace {notFound}
                </ButtonWithProgress>
            </Box>
        </Box>;
    }

    return <SearchListContainer
        searchContainer={
            [<StyledInput key='nsi' placeholder='Namespace' onSubmit={fetchNamespace} onChange={onChangeInput} />]
        }
        listContainer={listContainer}
        loading={loading}
    />;
};