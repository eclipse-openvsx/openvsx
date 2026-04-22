/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useEffect, useState, useRef, ReactNode } from 'react';
import { Box, Typography, Tabs, Tab, useTheme, useMediaQuery, Link } from '@mui/material';
import { Namespace, UserData } from '../../extension-registry-types';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { MainContext } from '../../context';
import { NamespaceDetail } from './user-settings-namespace-detail';
import { CreateNamespaceDialog } from './create-namespace-dialog';

interface NamespaceTabProps {
    chosenNamespace: Namespace,
    onChange: (value: Namespace) => void,
    namespaces: Namespace[]
}

const NamespacesTabs = (props: NamespaceTabProps) => {
    const theme = useTheme();
    const isATablet = useMediaQuery(theme.breakpoints.down('md'));
    return <Tabs
        orientation={isATablet ? 'horizontal' : 'vertical'}
        value={props.chosenNamespace}
        onChange={(event, value) => props.onChange(value)}
        variant={isATablet ? 'scrollable' : 'standard'}
        scrollButtons={isATablet ? 'auto' : false}
        indicatorColor='secondary'
        sx={{ width: { xs: '80%', sm: '80%', md: '80%', lg: '160px', xl: '160px' } }}
    >
        {
            props.namespaces.map(namespace => {
                return <Tab
                    sx={{
                        root: {
                            minHeight: '24px'
                        },
                        wrapper: {
                            textTransform: 'none'
                        }
                    }}
                    key={'nmspc-' + namespace.name}
                    value={namespace}
                    label={namespace.name}
                />;
            })
        }
    </Tabs>;
};

export const UserSettingsNamespaces: FunctionComponent = () => {

    const [loading, setLoading] = useState(true);
    const [namespaces, setNamespaces] = useState<Array<Namespace>>([]);
    const [chosenNamespace, setChosenNamespace] = useState<Namespace>();
    const { pageSettings, service, user, handleError } = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());

    useEffect(() => {
        initNamespaces();
        return () => {
            abortController.current.abort();
        };
    }, []);

    const handleChangeNamespace = (value: Namespace): void => {
        doHandleChangeNamespace(value);
    };

    const doHandleChangeNamespace = async(chosenNamespace: Namespace): Promise<void> => {
        setChosenNamespace(chosenNamespace);
    };

    const initNamespaces = async(): Promise<void> => {
        try {
            const namespaces = await service.getNamespaces(abortController.current);
            const chosenNamespace = namespaces.length ? namespaces[0] : undefined;
            setNamespaces(namespaces);
            setChosenNamespace(chosenNamespace);
            setLoading(false);
        } catch (err) {
            handleError(err);
            setLoading(false);
        }
    };

    const handleNamespaceCreated = () => {
        setLoading(true);
        initNamespaces();
    };

    let namespaceContainer: ReactNode = null;
    const namespaceAccessUrl = pageSettings.urls.namespaceAccessInfo;
    if (namespaces.length > 0 && chosenNamespace) {
        namespaceContainer = <Box
            sx={{
                display: 'flex',
                width: '100%',
                flexDirection: { xs: 'column', sm: 'column', md: 'column', lg: 'row', xl: 'row' },
                alignItems: { xs: 'center', sm: 'center', md: 'center', lg: 'normal', xl: 'normal' }
            }}
        >
            <NamespacesTabs
                chosenNamespace={chosenNamespace}
                namespaces={namespaces}
                onChange={handleChangeNamespace}
            />
            <NamespaceDetail
                namespace={chosenNamespace}
                setLoadingState={(loading: boolean) => setLoading(loading)}
                filterUsers={(foundUser: UserData) => foundUser.provider !== user?.provider || foundUser.loginName !== user?.loginName}
                fixSelf={true}
                namespaceAccessUrl={namespaceAccessUrl}
                theme={pageSettings.themeType} />
        </Box>;
    } else if (!loading) {
        namespaceContainer = <Typography variant='body1'>No namespaces available. Read <Link color='secondary' href={namespaceAccessUrl} target='_blank'>here</Link> about claiming namespaces.</Typography>;
    }

    return <>
        <Box
            sx={{
                display: 'flex',
                justifyContent: 'space-between',
                flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' },
                alignItems: { xs: 'center', sm: 'center', md: 'normal', lg: 'normal', xl: 'normal' }
            }}
        >
            <Box>
                <Typography variant='h5' gutterBottom>Namespaces</Typography>
            </Box>
            <Box
                sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    justifyContent: { xs: 'center', sm: 'center', md: 'normal', lg: 'normal', xl: 'normal' }
                }}
            >
                <Box sx={{ mr: 1, mb: 1 }}>
                    <CreateNamespaceDialog
                        namespaceCreated={handleNamespaceCreated}
                    />
                </Box>
            </Box>
        </Box>
        <Box mt={2}>
            <DelayedLoadIndicator loading={loading}/>
            {namespaceContainer}
        </Box>
    </>;
};