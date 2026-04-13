/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useEffect, useState, useRef } from 'react';
import { Box, Divider, Typography } from '@mui/material';
import { MainContext } from '../../context';
import { SanitizedMarkdown } from '../../components/sanitized-markdown';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { Extension } from '../../extension-registry-types';

export const ExtensionDetailChanges: FunctionComponent<ExtensionDetailChangesProps> = props => {
    const [changelog, setChangelog] = useState<string>();
    const [loading, setLoading] = useState<boolean>(true);
    const context = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());

    useEffect(() => {
        setLoading(true);
        updateChanges();
        return () => abortController.current.abort();
    }, [props.extension.namespace, props.extension.name, props.extension.version]);

    const updateChanges = async (): Promise<void> => {
        if (props.extension.files.changelog) {
            try {
                const changelog = await context.service.getExtensionChangelog(abortController.current, props.extension);
                setChangelog(changelog);
            } catch (err) {
                context.handleError(err);
                setChangelog(undefined);
            }
        } else {
            setChangelog('');
        }

        setLoading(false);
    };

    if (typeof changelog === 'undefined') {
        return <DelayedLoadIndicator loading={loading} />;
    }
    if (changelog.length === 0) {
        return <>
            <Box
                sx={{
                    my: 2,
                    display: 'flex',
                    justifyContent: 'space-between',
                    ['@media(max-width: 360px)']: {
                        flexDirection: 'column',
                        '& > div:first-of-type': {
                            marginBottom: '1rem'
                        },
                        '& button': {
                            maxWidth: '12rem',
                        }
                    }
                }}
            >
                <Typography variant='h5'>
                    Changelog
                </Typography>
            </Box>
            <Divider />
            <Box mt={3}>
                <Typography>No changelog available</Typography>
            </Box>
        </>;
    }
    return <Box
        sx={{
            display: 'flex',
            mt: 2,
            flexDirection: {
                xs: 'column-reverse',
                sm: 'column-reverse',
                md: 'column-reverse',
                lg: 'column-reverse',
                xl: 'row'
            }
        }}
    >
        <Box flex={5} overflow='auto'>
            <SanitizedMarkdown content={changelog} />
        </Box>
    </Box>;
};

export interface ExtensionDetailChangesProps {
    extension: Extension;
}