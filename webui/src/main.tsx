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
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { AppProviders } from './app-providers';
import { queryClient } from './query-client';
import { AdminDashboardRoutes } from './pages/admin-dashboard/admin-dashboard-routes';
import { ErrorDialog } from './components/error-dialog';
import { SessionExpiredDialog } from './components/session-expired-dialog';
import { handleError } from './utils';
import { ExtensionRegistryService } from './extension-registry-service';
import { ReportedError } from './extension-registry-types';
import { isNotFound, isUnauthorized } from './server-request';
import { MainContext } from './context';
import { PageSettings } from './page-settings';
import { AppLayout } from './layout/app-layout';
import { useAuthError, useCurrentUser, useLoginProviders, useRegistryVersion } from './use-main-queries';

import '../src/main.css';

const AdminDashboard = lazy(() =>
    import('./pages/admin-dashboard/admin-dashboard').then(m => ({ default: m.AdminDashboard }))
);

// The query client wraps everything else, so the hooks that build MainContext read it like any other hook.
export const Main: FunctionComponent<MainProps> = props => (
    <QueryClientProvider client={queryClient}>
        <MainContent {...props} />
        <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
);

const MainContent: FunctionComponent<MainProps> = props => {
    const [error, setError] = useState<{ title?: string; message: string; code?: number | string }>();
    const [isErrorDialogOpen, setIsErrorDialogOpen] = useState<boolean>(false);
    // Optional callback to run when the error dialog is dismissed (e.g. re-fetch stale data).
    const errorDialogOnClose = useRef<(() => void) | undefined>(undefined);
    const [hasAuthError] = useState(() => new URLSearchParams(window.location.search).has('auth-error'));
    const [sessionDialogDismissed, setSessionDialogDismissed] = useState(false);
    const sessionDialogDismissedRef = useRef(false);

    const { user, userError, userLoading, loggedOut, sessionExpired, refetchUser, confirmSessionExpiry } =
        useCurrentUser(props.service);
    const { data: registryLoginProviders } = useLoginProviders(props.service, {
        enabled: props.loginProviders == null
    });
    const { error: authError } = useAuthError(props.service, { enabled: hasAuthError });
    const { data: version } = useRegistryVersion(props.service);

    const loginProviders = props.loginProviders ?? registryLoginProviders ?? undefined;

    const onError = useCallback(
        (err: unknown, options?: { onClose?: () => void; title?: string }) => {
            if (err instanceof DOMException && err.message.trim() === 'The operation was aborted.') {
                // an aborted request is not a failure to report
                return;
            }

            const report = () => {
                const message = handleError(err);
                const code = (err as ReportedError).code;
                errorDialogOnClose.current = options?.onClose;
                setError({ title: options?.title, message, code });
                setIsErrorDialogOpen(true);
            };
            if (!isUnauthorized(err)) {
                report();
                return;
            }
            // the session-expired dialog explains a 401 while it is open; any other 401 is a failure to report
            void confirmSessionExpiry().then(expired => {
                if (!expired || sessionDialogDismissedRef.current) {
                    report();
                }
            });
        },
        [confirmSessionExpiry]
    );

    useEffect(() => {
        if (userError && !loggedOut) {
            onError(userError, { title: 'Couldn’t load your account' });
        }
    }, [userError, loggedOut, onError]);

    // A 404 means the session holds no login error any more, e.g. a stale `?auth-error` URL.
    useEffect(() => {
        if (authError && !isNotFound(authError)) {
            onError(authError, { title: 'Couldn’t log you in' });
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
                        title={error.title}
                        errorMessage={error.message}
                        errorCode={error.code}
                        isErrorDialogOpen={isErrorDialogOpen}
                        handleCloseDialog={onErrorDialogClose}
                    />
                ) : null}
                <SessionExpiredDialog
                    open={sessionExpired && !sessionDialogDismissed}
                    loginProviders={loginProviders}
                    onClose={() => {
                        sessionDialogDismissedRef.current = true;
                        setSessionDialogDismissed(true);
                    }}
                />
            </>
        );
    };

    const mainContext = useMemo<MainContext>(
        () => ({
            service: props.service,
            pageSettings: props.pageSettings,
            user,
            userLoading,
            updateUser,
            loginProviders,
            handleError: onError,
            version
        }),
        [props.service, props.pageSettings, user, userLoading, updateUser, loginProviders, onError, version]
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
