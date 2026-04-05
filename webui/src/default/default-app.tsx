/********************************************************************************
 * Copyright (c) 2019-2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { createRoot } from 'react-dom/client';
import { useMemo } from 'react';
import { HelmetProvider } from 'react-helmet-async';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { ExtensionRegistryService } from '../extension-registry-service';
import { Main } from '../main';
import createPageSettings from './page-settings';
import createDefaultTheme from './theme';

// This is the default entry point for the webui Docker image and for development.
// The production code for open-vsx.org is at https://github.com/eclipse/open-vsx.org


let serverHost = location.host;
if (serverHost.startsWith('3000-')) {
    // Gitpod dev environment: the frontend runs on port 3000, but the server runs on port 8080
    serverHost = '8080-' + serverHost.substring(5);
} else if (location.port === '3000') {
    // Localhost dev environment
    serverHost = location.hostname + ':8080';
} else if (serverHost.includes('che-webui')) {
    // Eclipse Che dev environment.
    // If serverHost contains 'che-webui', replace it with 'che-server'
    serverHost = serverHost.replace('che-webui', 'che-server');
}
const service = new ExtensionRegistryService(`${location.protocol}//${serverHost}`);

async function getServerVersion(): Promise<string> {
   const abortController = new AbortController();
   try {
    const result = await service.getRegistryVersion(abortController);
    return result.version;
   } catch (error) {
    console.error('Could not determine server version');
    return 'unknown';
   }
}

export const App = () => {
    const prefersDarkMode = useMediaQuery('(prefers-color-scheme: dark)');
    const theme = useMemo(
        () => createDefaultTheme(prefersDarkMode ? 'dark' : 'light'),
        [prefersDarkMode],
    );

    const pageSettings = createPageSettings(prefersDarkMode, service.serverUrl, getServerVersion());
    return (
        <HelmetProvider>
            <ThemeProvider theme={theme}>
                <Main
                    service={service}
                    pageSettings={pageSettings}
                />
            </ThemeProvider>
        </HelmetProvider>
    );
};

const node = document.getElementById('main') as HTMLElement;
const root = createRoot(node);
root.render(<BrowserRouter>
    <App />
</BrowserRouter>);
