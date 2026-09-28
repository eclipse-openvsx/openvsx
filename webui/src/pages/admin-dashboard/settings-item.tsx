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

import { ChangeEvent, FC } from 'react';
import { Box, Skeleton, Typography } from '@mui/material';
import { SettingsSwitch } from './settings-switch';

export interface SettingsItemProps {
    title: string;
    description: string;
    checked: boolean;
    disabled?: boolean;
    loading?: boolean;
    onChange: (event: ChangeEvent<HTMLInputElement>, checked: boolean) => void;
}

export const SettingsItem: FC<SettingsItemProps> = ({ title, description, checked, disabled, loading, onChange }) => {
    return (
        <Box
            sx={{
                p: 3,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 3,
                flexWrap: { xs: 'wrap', sm: 'nowrap' }
            }}>
            <Box sx={{ flex: { xs: '1 1 0', sm: '1 1 320px' } }}>
                <Typography variant='subtitle1' gutterBottom>
                    {title}
                </Typography>
                <Typography variant='body2' color='text.secondary' sx={{ display: { xs: 'none', sm: 'block' } }}>
                    {description}
                </Typography>
            </Box>

            {loading ? (
                <Skeleton variant='rounded' width={60} height={24} />
            ) : (
                <SettingsSwitch name={title} checked={checked} disabled={disabled} onChange={onChange} />
            )}

            <Typography
                variant='body2'
                color='text.secondary'
                sx={{ display: { xs: 'block', sm: 'none' }, flexBasis: '100%' }}>
                {description}
            </Typography>
        </Box>
    );
};
