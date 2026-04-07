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
import { Button } from '@mui/material';
import { GenerateTokenDialog as BaseGenerateTokenDialog } from '../../components/generate-token-dialog';
import { isError } from '../../extension-registry-types';
import { MainContext } from '../../context';

export const GenerateTokenDialog: FunctionComponent<GenerateTokenDialogProps> = props => {
    const context = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());
    const [open, setOpen] = useState(false);

    const handleGenerate = async (description: string): Promise<string> => {
        if (!context.user) {
            throw new Error('Not logged in');
        }
        const token = await context.service.createAccessToken(abortController.current, context.user, description);
        if (isError(token)) {
            throw token;
        }

        props.handleTokenGenerated();

        return token.value!;
    };

    return <>
        <Button variant='outlined' onClick={() => setOpen(true)}>Generate new token</Button>
        <BaseGenerateTokenDialog
            open={open}
            onClose={() => setOpen(false)}
            onGenerate={handleGenerate}
            onError={context.handleError}
        />
    </>;
};

export interface GenerateTokenDialogProps {
    handleTokenGenerated: () => void;
}