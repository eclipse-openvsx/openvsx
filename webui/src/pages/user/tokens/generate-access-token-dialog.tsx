/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useRef, useState } from 'react';
import { Box, Button, Checkbox, FormControlLabel, FormHelperText, TextField } from '@mui/material';
import { GenerateTokenDialog } from '../../../components/generate-token-dialog';
import { isError } from '../../../extension-registry-types';
import { MainContext } from '../../../context';

export const GenerateAccessTokenDialog: FunctionComponent<GenerateTokenDialogProps> = props => {
    const context = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());
    const [open, setOpen] = useState(false);
    const [namespace, setNamespace] = useState('');
    const [extension, setExtension] = useState('');
    const [publishingOnly, setPublishingOnly] = useState(false);

    const handleGenerate = async (description: string): Promise<string> => {
        if (!context.user) {
            throw new Error('Not logged in');
        }
        const token = await context.service.createAccessToken(abortController.current, context.user, description, {
            namespace: namespace.trim() || undefined,
            extension: extension.trim() || undefined,
            publishingOnly
        });
        if (isError(token)) {
            throw token;
        }

        props.handleTokenGenerated();

        return token.value!;
    };

    return (
        <>
            <Button variant='outlined' onClick={() => setOpen(true)}>
                Generate new token
            </Button>
            <GenerateTokenDialog
                open={open}
                onClose={() => {
                    setOpen(false);
                    setNamespace('');
                    setExtension('');
                    setPublishingOnly(false);
                }}
                onGenerate={handleGenerate}
                onError={context.handleError}>
                <Box mt={2} display='flex' flexDirection='column' gap={2}>
                    <TextField
                        fullWidth
                        label='Namespace (optional)'
                        helperText='Restrict the token to this namespace. Leave empty for a token that works everywhere you can publish.'
                        value={namespace}
                        onChange={e => setNamespace(e.target.value)}
                    />
                    <TextField
                        fullWidth
                        label='Extension (optional)'
                        helperText='Restrict the token to one extension of the namespace.'
                        disabled={!namespace.trim()}
                        value={extension}
                        onChange={e => setExtension(e.target.value)}
                    />
                    <Box>
                        <FormControlLabel
                            label='Publishing only'
                            control={
                                <Checkbox
                                    checked={publishingOnly}
                                    onChange={e => setPublishingOnly(e.target.checked)}
                                />
                            }
                        />
                        <FormHelperText>
                            The token can only publish extensions. It cannot create namespaces, delete extensions or use
                            administration endpoints.
                        </FormHelperText>
                    </Box>
                </Box>
            </GenerateTokenDialog>
        </>
    );
};

export interface GenerateTokenDialogProps {
    handleTokenGenerated: () => void;
}
