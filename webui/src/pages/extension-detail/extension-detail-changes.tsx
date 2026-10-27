/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { Box, Divider, Typography } from '@mui/material';
import { SanitizedMarkdown } from '../../components/sanitized-markdown';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { Extension } from '../../extension-registry-types';
import { useExtensionChangelog } from './use-extension-details';

export const ExtensionDetailChanges: FunctionComponent<ExtensionDetailChangesProps> = props => {
    const { data: changelogContent, isLoading: changelogLoading } = useExtensionChangelog(props.extension);
    const changelog = props.extension.files.changelog ? changelogContent : '';

    if (typeof changelog === 'undefined') {
        return <DelayedLoadIndicator loading={changelogLoading} />;
    }
    if (changelog.length === 0) {
        return (
            <>
                <Box
                    sx={{
                        mb: 2,
                        display: 'flex',
                        justifyContent: 'space-between',
                        ['@media(max-width: 360px)']: {
                            flexDirection: 'column',
                            '& > div:first-of-type': {
                                marginBottom: '1rem'
                            },
                            '& button': {
                                maxWidth: '12rem'
                            }
                        }
                    }}>
                    <Typography variant='h5'>Changelog</Typography>
                </Box>
                <Divider />
                <Box mt={3}>
                    <Typography>No changelog available</Typography>
                </Box>
            </>
        );
    }
    return (
        <Box
            sx={{
                display: 'flex',
                flexDirection: {
                    xs: 'column-reverse',
                    sm: 'column-reverse',
                    md: 'column-reverse',
                    lg: 'column-reverse',
                    xl: 'row'
                }
            }}>
            <Box flex={5} overflow='auto'>
                <SanitizedMarkdown content={changelog} />
            </Box>
        </Box>
    );
};

export interface ExtensionDetailChangesProps {
    extension: Extension;
}
