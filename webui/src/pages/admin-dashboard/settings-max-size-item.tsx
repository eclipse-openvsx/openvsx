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
import { Box, TextField, Typography } from '@mui/material';

export const BYTES_PER_MB = 1024 * 1024;

// Flooring would understate a ceiling that is not a whole number of MB (e.g. 1.5 MiB reported as
// "1 MB"), making a value the server would accept look invalid. Rounding instead can overstate it
// at a near-boundary value (e.g. 2 MiB - 1 byte rounds to "2 MB", naming a value the server would
// reject) - so the exact byte count is the authoritative part of the message, with the MB figure
// only an approximation alongside it.
const formatCeiling = (bytes: number): string => {
    const mb = bytes / BYTES_PER_MB;
    const approxMB = Number.isInteger(mb) ? String(mb) : mb.toFixed(2);
    return `${bytes} bytes (~${approxMB} MB)`;
};

export interface SettingsMaxSizeItemProps {
    /** What the admin typed, in MB, rather than the draft's number rendered back, so the field can be emptied. */
    value: string;
    /** The largest default the server accepts, in bytes; named by the error once the value is invalid. */
    ceiling: number;
    valid: boolean;
    disabled?: boolean;
    onChange: (event: ChangeEvent<HTMLInputElement>) => void;
}

/** The default max extension size, edited in MB. */
export const SettingsMaxSizeItem: FC<SettingsMaxSizeItemProps> = ({ value, ceiling, valid, disabled, onChange }) => (
    <Box sx={{ p: 3 }}>
        <Typography variant='subtitle1' gutterBottom>
            Default max extension size
        </Typography>
        <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
            The largest extension package accepted for publishing when no namespace or extension override applies.
        </Typography>
        <TextField
            label='Max extension size (MB)'
            type='number'
            value={value}
            onChange={onChange}
            disabled={disabled}
            error={!valid}
            helperText={
                valid
                    ? undefined
                    : `Must be a whole number of bytes, greater than 0 and at most ${formatCeiling(ceiling)}`
            }
            inputProps={{ min: '1' }}
            sx={{ maxWidth: 240 }}
        />
    </Box>
);
