/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useEffect, useState, useRef } from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Routes } from 'react-router-dom';
import { AdminDashboard } from './pages/admin-dashboard/admin-dashboard';
import { AdminDashboardRoutes } from './pages/admin-dashboard/admin-routes';
import { ErrorDialog } from './components/error-dialog';
import { handleError } from './utils';
import { ExtensionRegistryService } from './extension-registry-service';
import { UserData, isError, ReportedError, isSuccess, LoginProviders } from './extension-registry-types';
import { MainContext } from './context';
import { PageSettings } from './page-settings';
import { ErrorResponse } from './server-request';

import '../src/main.css';
import { OtherPages } from './other-pages';

export const Main: FunctionComponent<MainProps> = props => {
    const [user, setUser] = useState<UserData>();
    const [userLoading, setUserLoading] = useState<boolean>(true);
    const [loginProviders, setLoginProviders] = useState<Record<string, string> | undefined>(props.loginProviders);
    const [error, setError] = useState<{message: string, code?: number | string}>();
    const [isErrorDialogOpen, setIsErrorDialogOpen] = useState<boolean>(false);
    const abortController = useRef<AbortController>(new AbortController());

    useEffect(() => {
        getLoginProviders();

        // If there was an authentication error, get the message from the server and show it
        const searchParams = new URLSearchParams(window.location.search);
        if (searchParams.has('auth-error')) {
            props.service.getUserAuthError(abortController.current).then(onError);
        }

        // Get data of the currently logged in user
        updateUser();

        return () => abortController.current.abort();
    }, []);

    const updateUser = async () => {
        try {
            setUserLoading(true);
            const user = await props.service.getUser(abortController.current);
            if (isError(user)) {
                // An error result with HTTP OK status indicates that the user is not logged in.
                setUser(undefined);
            } else {
                setUser(user as UserData);
            }
        } catch (err) {
            onError(err);
        }

        setUserLoading(false);
    };

    const getLoginProviders = async () => {
        if (props.loginProviders != null) {
            return;
        }

        const data = await props.service.getLoginProviders(abortController.current);
        if (isSuccess(data)) {
            console.log(data.success);
        } else {
            setLoginProviders((data as LoginProviders).loginProviders);
        }
    };

    const onError = (err: Error | Partial<ErrorResponse> | ReportedError) => {
        if (err instanceof DOMException && err.message.trim() === 'The operation was aborted.') {
            // ignore error caused by AbortController.abort()
            return;
        }

        const message = handleError(err);
        const code = (err as ReportedError).code;
        setError({ message, code });
        setIsErrorDialogOpen(true);
    };

    const onErrorDialogClose = () => {
        setIsErrorDialogOpen(false);
    };

    const renderPageContent = (): ReactNode => {
        const { mainHeadTags: MainHeadTagsComponent } = props.pageSettings.elements;
        return <>
            { MainHeadTagsComponent ? <MainHeadTagsComponent pageSettings={props.pageSettings}/> : null }
            <Routes>
                <Route path={AdminDashboardRoutes.MAIN + '/*'} element={<AdminDashboard userLoading={userLoading} />} />
                <Route path='*' element={ <OtherPages user={user} userLoading={userLoading} /> } />
            </Routes>
            {
                error ?
                    <ErrorDialog
                        errorMessage={error.message}
                        errorCode={error.code}
                        isErrorDialogOpen={isErrorDialogOpen}
                        handleCloseDialog={onErrorDialogClose} />
                    : null
            }
        </>;
    };

    const mainContext: MainContext = {
        service: props.service,
        pageSettings: props.pageSettings,
        user,
        updateUser,
        loginProviders,
        handleError: onError
    };
    return <>
        <CssBaseline />
        <MainContext.Provider value={mainContext}>
            {renderPageContent()}
        </MainContext.Provider>
    </>;
};

export interface MainProps {
    service: ExtensionRegistryService;
    pageSettings: PageSettings;
    loginProviders?: Record<string, string>;
}