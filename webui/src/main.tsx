/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useEffect, useState, useRef, lazy, Suspense } from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Routes } from 'react-router';
import { AppProviders } from './app-providers';
import { AdminDashboardRoutes } from './pages/admin-dashboard/admin-dashboard-routes';
import { ErrorDialog } from './components/error-dialog';
import { handleError } from './utils';
import { ExtensionRegistryService } from './extension-registry-service';
import {
    UserData,
    isError,
    ReportedError,
    isSuccess,
    LoginProviders,
    RegistryVersion
} from './extension-registry-types';
import { MainContext } from './context';
import { PageSettings } from './page-settings';
import { AppLayout } from './layout/app-layout';

import '../src/main.css';

const AdminDashboard = lazy(() =>
    import('./pages/admin-dashboard/admin-dashboard').then(m => ({ default: m.AdminDashboard }))
);

export const Main: FunctionComponent<MainProps> = props => {
    const [user, setUser] = useState<UserData>();
    const [userLoading, setUserLoading] = useState<boolean>(true);
    const [loginProviders, setLoginProviders] = useState<Record<string, string> | undefined>(props.loginProviders);
    const [error, setError] = useState<{ message: string; code?: number | string }>();
    const [isErrorDialogOpen, setIsErrorDialogOpen] = useState<boolean>(false);
    const [version, setVersion] = useState<RegistryVersion | undefined>(undefined);
    const abortController = useRef<AbortController>(new AbortController());
    // Optional callback to run when the error dialog is dismissed (e.g. re-fetch stale data).
    const errorDialogOnClose = useRef<(() => void) | undefined>(undefined);

    useEffect(() => {
        getLoginProviders();

        // If there was an authentication error, get the message from the server and show it
        const searchParams = new URLSearchParams(window.location.search);
        if (searchParams.has('auth-error')) {
            props.service.getUserAuthError(abortController.current).then(onError);
        }

        // Get data of the currently logged in user
        updateUser();

        props.service
            .getRegistryVersion(abortController.current)
            .then(setVersion)
            .catch(() => {});

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

    const onError = (err: unknown, options?: { onClose?: () => void }) => {
        if (err instanceof DOMException && err.message.trim() === 'The operation was aborted.') {
            // ignore error caused by AbortController.abort()
            return;
        }

        const message = handleError(err);
        const code = (err as ReportedError).code;
        errorDialogOnClose.current = options?.onClose;
        setError({ message, code });
        setIsErrorDialogOpen(true);
    };

    const onErrorDialogClose = () => {
        setIsErrorDialogOpen(false);
        const onClose = errorDialogOnClose.current;
        errorDialogOnClose.current = undefined;
        onClose?.();
    };

    const renderPageContent = (): ReactNode => {
        const { mainHeadTags: MainHeadTagsComponent } = props.pageSettings.elements;
        return (
            <>
                {MainHeadTagsComponent ? <MainHeadTagsComponent pageSettings={props.pageSettings} /> : null}
                <Routes>
                    <Route
                        path={AdminDashboardRoutes.MAIN + '/*'}
                        element={
                            <Suspense fallback={null}>
                                <AdminDashboard userLoading={userLoading} />
                            </Suspense>
                        }
                    />
                    <Route path='*' element={<AppLayout userLoading={userLoading} />} />
                </Routes>
                {error ? (
                    <ErrorDialog
                        errorMessage={error.message}
                        errorCode={error.code}
                        isErrorDialogOpen={isErrorDialogOpen}
                        handleCloseDialog={onErrorDialogClose}
                    />
                ) : null}
            </>
        );
    };

    const mainContext: MainContext = {
        service: props.service,
        pageSettings: props.pageSettings,
        user,
        userLoading,
        updateUser,
        loginProviders,
        handleError: onError,
        version
    };
    return (
        <>
            <CssBaseline />
            <AppProviders mainContext={mainContext}>{renderPageContent()}</AppProviders>
        </>
    );
};

export interface MainProps {
    service: ExtensionRegistryService;
    pageSettings: PageSettings;
    loginProviders?: Record<string, string>;
}
