/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useEffect } from 'react';
import { Dialog, DialogTitle, DialogContent, Button, DialogContentText, DialogActions, Box, Link } from '@mui/material';
import { styled, Theme } from '@mui/material/styles';
import { MainContext } from '../context';

const ErrorLink = styled(Link)(({ theme }: { theme: Theme }) => ({
    textDecoration: 'underline',
    color: theme.palette.primary.main
}));

export const ErrorDialog: FunctionComponent<ErrorDialogProps> = props => {
    const { pageSettings } = useContext(MainContext);
    const ErrorHelp = pageSettings?.elements.errorHelp;

    useEffect(() => {
        document.addEventListener('keydown', handleEnter);
        return () => document.removeEventListener('keydown', handleEnter);
    }, []);

    const handleEnter = (event: KeyboardEvent): void => {
        if (event.code === 'Enter') {
            props.handleCloseDialog();
        }
    };

    const getContentForCode = (): ReactNode => {
        if (!props.errorCode) {
            return null;
        }

        switch (props.errorCode) {
            case 'publisher-agreement-problem': {
                const email = pageSettings?.publisherAgreement?.email;
                return email ? (
                    <>
                        Please contact{' '}
                        <ErrorLink
                            href={`mailto:${email}?subject=${encodeURIComponent('Publisher agreement problem')}`}>
                            {email}
                        </ErrorLink>{' '}
                        if this problem persists.
                    </>
                ) : (
                    <>Please contact the administrators of this registry if this problem persists.</>
                );
            }
            default:
                return null;
        }
    };

    const codeContent = getContentForCode();
    return (
        <Dialog open={props.isErrorDialogOpen} onClose={props.handleCloseDialog}>
            <DialogTitle>Error</DialogTitle>
            <DialogContent>
                <DialogContentText sx={{ color: theme => theme.palette.error.main }}>
                    {props.errorMessage}
                    {codeContent ? <Box mt={2}>{codeContent}</Box> : null}
                    {ErrorHelp ? <ErrorHelp errorCode={props.errorCode} errorMessage={props.errorMessage} /> : null}
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={props.handleCloseDialog}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};

export interface ErrorDialogProps {
    errorMessage: string;
    errorCode?: number | string;
    isErrorDialogOpen: boolean;
    handleCloseDialog: () => void;
}
