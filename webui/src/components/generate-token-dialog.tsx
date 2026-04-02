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

import { ChangeEvent, FunctionComponent, useState } from 'react';
import {
    Button, Dialog, DialogTitle, DialogContent, DialogContentText,
    Box, TextField, DialogActions, Typography, Paper, IconButton,
    Tooltip, LinearProgress, styled
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import copy from 'clipboard-copy';

const TOKEN_DESCRIPTION_SIZE = 255;

const StyledDialog = styled(Dialog)({
    '& .MuiDialog-paper': {
        width: 480,
    }
});

const TokenDisplay = styled(Paper)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    padding: theme.spacing(1.5),
    wordBreak: 'break-all',
    fontFamily: 'monospace',
    fontSize: '0.85rem',
    backgroundColor: theme.palette.mode === 'dark'
        ? theme.palette.grey[900]
        : theme.palette.grey[100],
}));

export const GenerateTokenDialog: FunctionComponent<GenerateTokenDialogProps> = props => {
    const [loading, setLoading] = useState(false);
    const [description, setDescription] = useState('');
    const [descriptionError, setDescriptionError] = useState<string>();
    const [tokenValue, setTokenValue] = useState<string>();
    const [copied, setCopied] = useState(false);

    const { open, onClose, title = 'Generate new token' } = props;

    const resetState = () => {
        setLoading(false);
        setDescription('');
        setDescriptionError(undefined);
        setTokenValue(undefined);
        setCopied(false);
    };

    const handleClose = () => {
        if (!loading) {
            resetState();
            onClose();
        }
    };

    const handleDescriptionChange = (event: ChangeEvent<HTMLInputElement>) => {
        const value = event.target.value;
        setDescription(value);
        setDescriptionError(
            value.length > TOKEN_DESCRIPTION_SIZE
                ? `Description must not exceed ${TOKEN_DESCRIPTION_SIZE} characters.`
                : undefined
        );
    };

    const handleGenerate = async () => {
        setLoading(true);
        try {
            const value = await props.onGenerate(description);
            setTokenValue(value);
        } catch (err) {
            props.onError?.(err);
        } finally {
            setLoading(false);
        }
    };

    const handleCopy = () => {
        if (tokenValue) {
            copy(tokenValue);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        }
    };

    const canGenerate = !descriptionError && !loading;

    return <StyledDialog open={open} onClose={handleClose}>
            <DialogTitle>{title}</DialogTitle>
            {loading && <LinearProgress color='secondary' />}
            <DialogContent>
                {tokenValue ? <>
                    <DialogContentText sx={{ mb: 2 }}>
                        Copy this token now. It will not be shown again.
                    </DialogContentText>
                    <TokenDisplay variant='outlined'>
                        <Typography component='span' sx={{ flex: 1, fontFamily: 'inherit', fontSize: 'inherit' }}>
                            {tokenValue}
                        </Typography>
                        <Tooltip title={copied ? 'Copied!' : 'Copy'} placement='top'>
                            <IconButton size='small' onClick={handleCopy}>
                                <ContentCopyIcon fontSize='small' />
                            </IconButton>
                        </Tooltip>
                    </TokenDisplay>
                </> : <>
                    <DialogContentText>
                        You can add an optional description to help identify where this token is used.
                    </DialogContentText>
                    <Box mt={2}>
                        <TextField
                            autoFocus
                            fullWidth
                            label='Description (optional)'
                            error={Boolean(descriptionError)}
                            helperText={descriptionError}
                            value={description}
                            onChange={handleDescriptionChange}
                            onKeyDown={e => {
                                if (e.key === 'Enter' && canGenerate) {
                                    handleGenerate();
                                }
                            }}
                        />
                    </Box>
                </>}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} color='secondary'>
                    {tokenValue ? 'Close' : 'Cancel'}
                </Button>
                {!tokenValue &&
                    <Button
                        variant='contained'
                        color='secondary'
                        disabled={!canGenerate}
                        onClick={handleGenerate}
                    >
                        Generate Token
                    </Button>
                }
            </DialogActions>
        </StyledDialog>;
};

export interface GenerateTokenDialogProps {
    open: boolean;
    onClose: () => void;
    onGenerate: (description: string) => Promise<string>;
    onError?: (err: unknown) => void;
    title?: string;
}
