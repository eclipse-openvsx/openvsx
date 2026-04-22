/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/

import { FunctionComponent, useState, useContext, useEffect, useRef } from 'react';
import {
    Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle
} from '@mui/material';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Namespace, isError } from '../../extension-registry-types';
import { MainContext } from '../../context';

export interface NamespaceDeleteDialogProps {
    open: boolean;
    onClose: () => void;
    onDelete: () => void;
    namespace: Namespace;
    setLoadingState: (loading: boolean) => void;
}

export const NamespaceDeleteDialog: FunctionComponent<NamespaceDeleteDialogProps> = props => {
    const { open, onClose, onDelete, namespace } = props;
    const { service, handleError } = useContext(MainContext);
    const [working, setWorking] = useState(false);

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

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

            props.setLoadingState(false);
            setWorking(false);
            onDelete();
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
                    Are you sure you want to delete the namespace <strong>{namespace.name}</strong>?
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button
                    variant='contained'
                    color='primary'
                    onClick={onClose}>
                    Cancel
                </Button>
                <ButtonWithProgress
                    sx={{ ml: 1 }}
                    working={working}
                    onClick={handleDeleteNamespace}>
                    Delete Namespace
                </ButtonWithProgress>
            </DialogActions>
        </Dialog>
    </>;
};
