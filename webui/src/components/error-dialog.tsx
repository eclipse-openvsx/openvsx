/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useEffect } from 'react';
import { Dialog, DialogTitle, DialogContent, Button, DialogContentText, DialogActions, Box, Link } from '@mui/material';
import { styled, Theme } from '@mui/material/styles';

const ErrorLink = styled(Link)(({ theme }: { theme: Theme }) => ({
    textDecoration: 'underline',
    color: theme.palette.primary.main
}));

export const ErrorDialog: FunctionComponent<ErrorDialogProps> = props => {

    useEffect(() => {
        document.addEventListener('keydown', handleEnter);
        return () => document.removeEventListener('keydown', handleEnter);
    }, []);

    const handleEnter = (event: KeyboardEvent): void => {
        if (event.code ===  'Enter') {
            props.handleCloseDialog();
        }
    };

    const getContentForCode = (): ReactNode => {
        if (!props.errorCode) {
            return null;
        }

        switch (props.errorCode) {
            case 'eclipse-missing-github-id':
                return <>
                    Please fill in the &ldquo;GitHub Username&rdquo; field
                    in <ErrorLink href='https://accounts.eclipse.org/user/edit' target='_blank'>
                        your Eclipse account
                    </ErrorLink> and try again.
                </>;
            case 'eclipse-mismatch-github-id':
                return <>
                    Please edit the &ldquo;GitHub Username&rdquo; field
                    in <ErrorLink href='https://accounts.eclipse.org/user/edit' target='_blank'>
                        your Eclipse account
                    </ErrorLink> or log in with a different GitHub account.
                </>;
            case 'publisher-agreement-problem':
                return <>
                    Please contact <ErrorLink href='mailto:webmaster@eclipse.org?subject=Problem%20With%20open-vsx.org%20Publisher%20Agreement' >
                        webmaster@eclipse.org
                    </ErrorLink> if this problem persists.
                </>;
            default:
                return null;
        }
    };

    const codeContent = getContentForCode();
    return <Dialog
            open={props.isErrorDialogOpen}
            onClose={props.handleCloseDialog} >
        <DialogTitle>Error</DialogTitle>
        <DialogContent>
            <DialogContentText sx={{ color: (theme) => theme.palette.error.main }}>
                {props.errorMessage}
                {
                    codeContent ?
                    <Box mt={2}>
                        {codeContent}
                    </Box>
                    : null
                }
            </DialogContentText>
        </DialogContent>
        <DialogActions>
            <Button onClick={props.handleCloseDialog}>
                Close
            </Button>
        </DialogActions>
    </Dialog>;
};

export interface ErrorDialogProps {
    errorMessage: string;
    errorCode?: number | string;
    isErrorDialogOpen: boolean;
    handleCloseDialog: () => void;
}