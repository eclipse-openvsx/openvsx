/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useCallback, useEffect, useMemo, useState, useRef, lazy, Suspense } from 'react';
import { CssBaseline } from '@mui/material';
import { Route, Routes } from 'react-router';
import { AppProviders } from './app-providers';
import { AdminDashboardRoutes } from './pages/admin-dashboard/admin-dashboard-routes';
import { ErrorDialog } from './components/error-dialog';
import { handleError } from './utils';
import { ExtensionRegistryService } from './extension-registry-service';
import { ReportedError } from './extension-registry-types';
import { isNotFound } from './server-request';
import { MainContext } from './context';
import { PageSettings } from './page-settings';
import { AppLayout } from './layout/app-layout';
import { isNotLoggedIn, useAuthError, useCurrentUser, useLoginProviders, useRegistryVersion } from './use-main-queries';

import '../src/main.css';

const AdminDashboard = lazy(() =>
    import('./pages/admin-dashboard/admin-dashboard').then(m => ({ default: m.AdminDashboard }))
);

export const Main: FunctionComponent<MainProps> = props => {
    const [error, setError] = useState<{ message: string; code?: number | string }>();
    const [isErrorDialogOpen, setIsErrorDialogOpen] = useState<boolean>(false);
    // Optional callback to run when the error dialog is dismissed (e.g. re-fetch stale data).
    const errorDialogOnClose = useRef<(() => void) | undefined>(undefined);
    const [hasAuthError] = useState(() => new URLSearchParams(window.location.search).has('auth-error'));

    const {
        data: user,
        error: userError,
        isLoading: userLoading,
        refetch: refetchUser
    } = useCurrentUser(props.service);
    const { data: registryLoginProviders } = useLoginProviders(props.service, {
        enabled: props.loginProviders == null
    });
    const { error: authError } = useAuthError(props.service, { enabled: hasAuthError });
    const { data: version } = useRegistryVersion(props.service);

    const loginProviders = props.loginProviders ?? registryLoginProviders ?? undefined;
    const loggedOut = isNotLoggedIn(userError);

    const onError = useCallback((err: unknown, options?: { onClose?: () => void }) => {
        if (err instanceof DOMException && err.message.trim() === 'The operation was aborted.') {
            // an aborted request is not a failure to report
            return;
        }

        const message = handleError(err);
        const code = (err as ReportedError).code;
        errorDialogOnClose.current = options?.onClose;
        setError({ message, code });
        setIsErrorDialogOpen(true);
    }, []);

    useEffect(() => {
        if (userError && !loggedOut) {
            onError(userError);
        }
    }, [userError, loggedOut, onError]);

    // A 404 means the session holds no login error any more, e.g. a stale `?auth-error` URL.
    useEffect(() => {
        if (authError && !isNotFound(authError)) {
            onError(authError);
        }
    }, [authError, onError]);

    // Memoized because it is part of mainContext; a fresh function would defeat that memo.
    const updateUser = useCallback(() => {
        refetchUser();
    }, [refetchUser]);

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

    const mainContext = useMemo<MainContext>(
        () => ({
            service: props.service,
            pageSettings: props.pageSettings,
            user: loggedOut ? undefined : user,
            userLoading,
            updateUser,
            loginProviders,
            handleError: onError,
            version
        }),
        [props.service, props.pageSettings, loggedOut, user, userLoading, updateUser, loginProviders, onError, version]
    );
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
