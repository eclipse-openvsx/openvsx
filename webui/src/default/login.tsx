/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */

import { FunctionComponent, ReactNode, useState } from 'react';
import { useLocation } from 'react-router';
import { Button, Dialog, DialogContent, DialogTitle, Stack } from '@mui/material';

export const LoginComponent: FunctionComponent<LoginComponentProps> = props => {
    const [dialogOpen, setDialogOpen] = useState(false);
    const location = useLocation();

    const showLoginDialog = () => setDialogOpen(true);

    // Come back to the page the visitor logged in from. The server only honours its own routes,
    // and resolves them against ovsx.webui.url, so an ignored target just lands on the front page.
    const authorizationUrl = (provider: string): string => {
        const returnTo = location.pathname + location.search + location.hash;
        const url = props.loginProviders[provider];
        return returnTo === '/' ? url : `${url}?redirect=${encodeURIComponent(returnTo)}`;
    };

    const providers = Object.keys(props.loginProviders);
    if (providers.length === 1) {
        return props.renderButton(authorizationUrl(providers[0]));
    } else {
        return (
            <>
                {props.renderButton(undefined, showLoginDialog)}
                <Dialog fullWidth open={dialogOpen} onClose={() => setDialogOpen(false)}>
                    <DialogTitle>Log In</DialogTitle>
                    <DialogContent>
                        <Stack spacing={2}>
                            {providers.map(provider => (
                                <Button
                                    key={provider}
                                    fullWidth
                                    variant='contained'
                                    color='secondary'
                                    href={authorizationUrl(provider)}>
                                    {provider}
                                </Button>
                            ))}
                        </Stack>
                    </DialogContent>
                </Dialog>
            </>
        );
    }
};

export interface LoginComponentProps {
    loginProviders: Record<string, string>;
    renderButton: (href?: string, onClick?: () => void) => ReactNode;
}
