/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, FunctionComponent, useState, useContext, useEffect, useRef } from 'react';
import {
    Button, Checkbox, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, FormControlLabel, TextField
} from '@mui/material';
import { ButtonWithProgress } from '../../components/button-with-progress';
import { Namespace, SuccessResult, isError } from '../../extension-registry-types';
import { MainContext } from '../../context';
import { InfoDialog } from '../../components/info-dialog';

export interface NamespaceChangeDialogProps {
    open: boolean;
    onClose: () => void;
    namespace: Namespace;
    setLoadingState: (loading: boolean) => void;
}

export const NamespaceChangeDialog: FunctionComponent<NamespaceChangeDialogProps> = props => {
    const { open } = props;
    const { service, handleError } = useContext(MainContext);
    const [working, setWorking] = useState(false);
    const [newNamespace, setNewNamespace] = useState('');
    const [removeOldNamespace, setRemoveOldNamespace] = useState(false);
    const [mergeIfNewNamespaceAlreadyExists, setMergeIfNewNamespaceAlreadyExists] = useState(false);
    const [infoDialogIsOpen, setInfoDialogIsOpen] = useState(false);
    const [infoDialogMessage, setInfoDialogMessage] = useState('');

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

    useEffect(() => {
        if (open) {
            setNewNamespace('');
            setRemoveOldNamespace(true);
            setMergeIfNewNamespaceAlreadyExists(false);
        }
    }, [open]);

    const onClose = () => {
        props.onClose();
    };
    const onInfoDialogClose = () => {
        onClose();
        setInfoDialogIsOpen(false);
    };
    const onRemoveOldNamespaceChange = (event: ChangeEvent, checked: boolean) => {
        setRemoveOldNamespace(checked);
    };
    const onMergeIfNewNamespaceAlreadyExistsChange = (event: ChangeEvent, checked: boolean) => {
        setMergeIfNewNamespaceAlreadyExists(checked);
    };
    const handleChangeNamespace = async () => {
        try {
            if (!props.namespace) {
                return;
            }
            setWorking(true);
            props.setLoadingState(true);
            const oldNamespace = props.namespace.name;
            const result = await service.admin.changeNamespace(abortController.current, {
                oldNamespace,
                newNamespace,
                removeOldNamespace,
                mergeIfNewNamespaceAlreadyExists
            });
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
            <DialogTitle id='form-dialog-title'>Change Namespace</DialogTitle>
            <DialogContent>
                <DialogContentText>
                    Enter the new Namespace name.
                </DialogContentText>
                <TextField
                    autoFocus
                    margin='dense'
                    id='name'
                    autoComplete='off'
                    label='New Open VSX Namespace'
                    fullWidth
                    onChange={(event) => {
                        setNewNamespace(event.target.value);
                    }}
                />
                <FormControlLabel
                    control={<Checkbox checked={removeOldNamespace} onChange={onRemoveOldNamespaceChange}
                                       name='remove-old-namespace'/>}
                    label={`Remove '${props.namespace.name}' namespace after namespace change`}/>
                <FormControlLabel
                    control={<Checkbox checked={mergeIfNewNamespaceAlreadyExists}
                                       onChange={onMergeIfNewNamespaceAlreadyExistsChange}
                                       name='merge-change-namespace'/>}
                    label='Merge namespaces if new namespace already exists'/>
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
                    onClick={handleChangeNamespace}>
                    Change Namespace
                </ButtonWithProgress>
            </DialogActions>
        </Dialog>
        <InfoDialog infoMessage={infoDialogMessage} isInfoDialogOpen={infoDialogIsOpen}
                    handleCloseDialog={onInfoDialogClose}/>
    </>;
};