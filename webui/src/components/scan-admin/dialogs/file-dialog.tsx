/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Typography,
    List,
    ListItem,
    ListItemText,
    Box
} from '@mui/material';
import { ButtonWithProgress } from '../../button-with-progress';
import { useDialogs } from '../../../hooks/scan-admin';
import { useTheme } from '@mui/material/styles';
import { FileDecision } from '../../../context/scan-admin';

/**
 * Confirmation dialog for file-level allow/block/delete actions.
 * Uses the useDialogs hook to consume context.
 */
export const FileDialog: FunctionComponent = () => {
    const theme = useTheme();
    const { fileDialog } = useDialogs();

    return (
        <Dialog
            open={fileDialog.isOpen}
            onClose={fileDialog.isPending ? undefined : fileDialog.close}
            maxWidth='md'
            fullWidth>
            <DialogTitle>
                {fileDialog.actionType === 'allow'
                    ? 'Confirm Allow Files'
                    : fileDialog.actionType === 'block'
                      ? 'Confirm Block Files'
                      : 'Confirm Delete Files'}
            </DialogTitle>
            <DialogContent>
                <Typography variant='body1' sx={{ mb: 2 }}>
                    Are you sure you want to {fileDialog.actionType}{' '}
                    {fileDialog.selectedFiles.length !== 1 ? 'these' : 'this'} {fileDialog.selectedFiles.length} file
                    {fileDialog.selectedFiles.length !== 1 ? 's' : ''}?
                </Typography>
                <List
                    sx={{
                        maxHeight: '400px',
                        overflow: 'auto',
                        border: `1px solid ${theme.palette.scanBackground.default}`,
                        borderRadius: 1
                    }}>
                    {fileDialog.selectedFiles.map((file: FileDecision) => (
                        <ListItem
                            key={file.id}
                            sx={{
                                borderBottom: `1px solid ${theme.palette.scanBackground.dark}`,
                                '&:last-child': { borderBottom: 'none' }
                            }}>
                            <ListItemText
                                primary={
                                    <Typography
                                        variant='body1'
                                        sx={{
                                            fontWeight: 500,
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap'
                                        }}>
                                        {file.fileName}
                                    </Typography>
                                }
                                secondary={
                                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
                                        <Typography
                                            variant='body2'
                                            color='text.secondary'
                                            sx={{
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}>
                                            Hash: {file.fileHash}
                                        </Typography>
                                        <Typography
                                            variant='body2'
                                            color='text.secondary'
                                            sx={{
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}>
                                            {file.displayName}
                                        </Typography>
                                        <Typography
                                            variant='body2'
                                            color='text.secondary'
                                            sx={{
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}>
                                            {file.namespace}.{file.extensionName}
                                        </Typography>
                                        <Typography
                                            variant='body2'
                                            color='text.secondary'
                                            sx={{
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}>
                                            Version: {file.version}
                                        </Typography>
                                    </Box>
                                }
                            />
                        </ListItem>
                    ))}
                </List>
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2 }}>
                <Button onClick={fileDialog.close} color='inherit' disabled={fileDialog.isPending}>
                    Cancel
                </Button>
                <ButtonWithProgress
                    onClick={fileDialog.execute}
                    working={fileDialog.isPending}
                    color={
                        fileDialog.actionType === 'allow'
                            ? 'success'
                            : fileDialog.actionType === 'block'
                              ? 'error'
                              : 'secondary'
                    }>
                    Confirm{' '}
                    {fileDialog.actionType === 'allow'
                        ? 'Allow'
                        : fileDialog.actionType === 'block'
                          ? 'Block'
                          : 'Delete'}
                </ButtonWithProgress>
            </DialogActions>
        </Dialog>
    );
};
