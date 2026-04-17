/********************************************************************************
 * Copyright (c) 2026 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

 import { FunctionComponent, useState, useContext, useEffect, useRef } from 'react';
 import {
     Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle
 } from '@mui/material';
 import { ButtonWithProgress } from '../../components/button-with-progress';
 import { Namespace, SuccessResult, isError } from '../../extension-registry-types';
 import { MainContext } from '../../context';
 import { InfoDialog } from '../../components/info-dialog';

 export interface NamespaceDeleteDialogProps {
     open: boolean;
     onClose: () => void;
     namespace: Namespace;
     setLoadingState: (loading: boolean) => void;
 }

 export const NamespaceDeleteDialog: FunctionComponent<NamespaceDeleteDialogProps> = props => {
     const { open } = props;
     const { service, handleError } = useContext(MainContext);
     const [working, setWorking] = useState(false);
     const [infoDialogIsOpen, setInfoDialogIsOpen] = useState(false);
     const [infoDialogMessage, setInfoDialogMessage] = useState('');

     const abortController = useRef<AbortController>(new AbortController());
     useEffect(() => {
         return () => {
             abortController.current.abort();
         };
     }, []);

    const onClose = () => {
        props.onClose();
    };
    const onInfoDialogClose = () => {
        onClose();
        setInfoDialogIsOpen(false);
    };
    const handleDeleteNamespace = async () => {
        try {
            if (!props.namespace) {
                return;
            }
            setWorking(true);
            props.setLoadingState(true);
            const name = props.namespace.name;
            const result = await service.admin.deleteNamespace(abortController.current, { name });
            if (isError(result)) {
                throw result;
            }

            const successResult = result as SuccessResult;
            props.setLoadingState(false);
            setWorking(false);
            setInfoDialogIsOpen(true);
            setInfoDialogMessage(successResult.success);
        } catch (err) {
            props.setLoadingState(false);
            setWorking(false);
            handleError(err);
        }
    };

     return <>
        <Dialog onClose={onClose} open={open} aria-labelledby='form-dialog-title'>
            <DialogTitle id='form-dialog-title'>Delete Namespace</DialogTitle>
            <DialogContent>
                <DialogContentText>
                     Are you sure you'd like to delete the namespace? Only namespaces with no extensions can be deleted.
                </DialogContentText>
             </DialogContent>
             <DialogActions>
                 <Button
                    variant='contained'
                    color='primary'
                    onClick={onClose} >
                    Cancel
                </Button>
                <ButtonWithProgress
                    sx={{ ml: 1 }}
                    working={working}
                    onClick={handleDeleteNamespace} >
                    Delete Namespace
                </ButtonWithProgress>
             </DialogActions>
         </Dialog>
         <InfoDialog infoMessage={infoDialogMessage} isInfoDialogOpen={infoDialogIsOpen} handleCloseDialog={onInfoDialogClose}/>
     </>;
 };