/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { Box } from '@mui/material';
import { SxProps, Theme } from '@mui/material/styles';
import { toRelativeTime, toLocalTime } from '../utils';

export const Timestamp: FunctionComponent<TimestampProps> = props => {
    const sx = props.sx ?? [];
    const isFutureTime = props.isFutureTime ?? false;
    const timestamp = props.value;
    return <Box
        component='span'
        title={toLocalTime(timestamp)}
        sx={[...(Array.isArray(sx) ? sx : [sx])]}>
        {toRelativeTime(timestamp, isFutureTime)}
    </Box>;
};

export interface TimestampProps {
    value: string;
    isFutureTime?: boolean;
    sx?: SxProps<Theme>;
}