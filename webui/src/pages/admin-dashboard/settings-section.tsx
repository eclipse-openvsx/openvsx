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

import { FC, PropsWithChildren } from 'react';
import { Box, Paper, Typography } from '@mui/material';

export interface SettingsSectionProps {
    title: string;
    description: string;
    /** Outlines the card while it holds unsaved edits, for settings worth a second look. */
    changed?: boolean;
}

/** One titled group of runtime settings, so unrelated ones don't share a card. */
export const SettingsSection: FC<PropsWithChildren<SettingsSectionProps>> = ({
    title,
    description,
    changed,
    children
}) => (
    <Box>
        <Typography variant='h6' component='h2'>
            {title}
        </Typography>
        <Typography variant='body2' color='text.secondary' gutterBottom>
            {description}
        </Typography>
        <Paper
            variant='outlined'
            elevation={0}
            sx={{ mt: 1, overflow: 'hidden', borderColor: changed ? 'warningAccent' : 'divider' }}>
            {children}
        </Paper>
    </Box>
);
