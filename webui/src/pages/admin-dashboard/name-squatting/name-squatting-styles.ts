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

import { Box, Paper, Table } from '@mui/material';
import { styled, Theme } from '@mui/material/styles';

export const GroupCard = styled(Paper)(({ theme }) => ({
    marginBottom: theme.spacing(2),
    overflow: 'hidden'
}));

export const GroupHeader = styled(Box)(({ theme }) => ({
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: theme.spacing(1, 2),
    padding: theme.spacing(1.5, 2),
    backgroundColor: theme.palette.action.hover
}));

export const HeaderEnd = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    marginLeft: 'auto'
}));

// Each group is its own table; a fixed layout makes the columns line up from one group to the next.
export const GroupTable = styled(Table)({
    tableLayout: 'fixed',
    minWidth: '60rem',
    '& td': {
        verticalAlign: 'top',
        overflowWrap: 'anywhere'
    }
});

export const Toolbar = styled(Box)(({ theme }) => ({
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: theme.spacing(1.5, 2),
    marginBottom: theme.spacing(2)
}));

/** `sx` of a one-of-a-few ToggleButtonGroup: the selected button filled in the secondary color, as on Extension Scans. */
export const segmentedToggle = (theme: Theme) => ({
    height: '2rem',
    '& .MuiToggleButton-root': {
        padding: theme.spacing(0, 1.5),
        fontSize: '0.8125rem',
        textTransform: 'none',
        whiteSpace: 'nowrap',
        '&.Mui-selected': {
            backgroundColor: theme.palette.secondary.main,
            color: theme.palette.secondary.contrastText,
            '&:hover': { backgroundColor: theme.palette.secondary.dark }
        }
    }
});
