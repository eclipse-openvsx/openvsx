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

import { FunctionComponent } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Namespace } from '../../extension-registry-types';
import { useDeleteNamespace } from './use-namespace-admin';

export interface NamespaceDeleteDialogProps {
    open: boolean;
    onClose: () => void;
    onDelete: () => void;
    namespace: Namespace;
    setLoadingState: (loading: boolean) => void;
}

export const NamespaceDeleteDialog: FunctionComponent<NamespaceDeleteDialogProps> = props => {
    const { open, onClose, onDelete, namespace } = props;
    const { mutate: deleteNamespace, isPending: working } = useDeleteNamespace();

    const handleDeleteNamespace = () => {
        if (!props.namespace) {
            return;
        }
        props.setLoadingState(true);
        deleteNamespace(props.namespace.name, {
            onSuccess: onDelete,
            onSettled: () => props.setLoadingState(false)
        });
    };

    return (
        <>
            <Dialog onClose={onClose} open={open} aria-labelledby='form-dialog-title'>
                <DialogTitle id='form-dialog-title'>Delete Namespace</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Are you sure you want to delete the namespace <strong>{namespace.name}</strong>?
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant='contained' color='primary' onClick={onClose}>
                        Cancel
                    </Button>
                    <ButtonWithProgress sx={{ ml: 1 }} working={working} onClick={handleDeleteNamespace}>
                        Delete Namespace
                    </ButtonWithProgress>
                </DialogActions>
            </Dialog>
        </>
    );
};
