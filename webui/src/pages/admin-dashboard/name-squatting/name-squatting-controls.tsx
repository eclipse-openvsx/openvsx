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

import { FC, useEffect, useState } from 'react';
import {
    Box,
    Button,
    Checkbox,
    CheckboxProps,
    CircularProgress,
    FormControlLabel,
    IconButton,
    IconButtonProps,
    InputAdornment,
    TableCell,
    TextField,
    Tooltip,
    Typography
} from '@mui/material';
import { styled, Theme } from '@mui/material/styles';
import CheckIcon from '@mui/icons-material/Check';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import { toLocalTime, toRelativeTime } from '../../../utils';
import { pluralize } from './name-squatting-model';

/** Search box that leads a toolbar, focused in the secondary color like the Extension Scans filters. */
export const SearchField: FC<{
    value: string;
    onChange: (value: string) => void;
    placeholder: string;
    label: string;
}> = ({ value, onChange, placeholder, label }) => (
    <TextField
        size='small'
        type='search'
        placeholder={placeholder}
        value={value}
        onChange={event => onChange(event.target.value)}
        inputProps={{ 'aria-label': label }}
        InputProps={{
            startAdornment: (
                <InputAdornment position='start'>
                    <SearchIcon sx={{ fontSize: 20 }} />
                </InputAdornment>
            )
        }}
        sx={{
            flex: '1 1 18rem',
            maxWidth: '26rem',
            '& .MuiOutlinedInput-root.Mui-focused fieldset': { borderColor: 'secondary.main' }
        }}
    />
);

/**
 * How long ago the list was looked up, counted in seconds under a minute: the lists refresh every minute,
 * so the shared formatter's "now" would be all it ever said. Ticks on its own, since the list only
 * re-renders when it refreshes.
 */
const UpdatedAgo: FC<{ updatedAt: number }> = ({ updatedAt }) => {
    const [now, setNow] = useState(Date.now);
    useEffect(() => {
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, []);
    const iso = new Date(updatedAt).toISOString();
    const seconds = Math.max(1, Math.floor((now - updatedAt) / 1000));
    return (
        <Typography variant='body2' color='text.secondary' title={toLocalTime(iso)} sx={{ whiteSpace: 'nowrap' }}>
            Updated {seconds < 60 ? `${pluralize(seconds, 'second')} ago` : toRelativeTime(iso)}
        </Typography>
    );
};

/**
 * When the list was last looked up, and a button to look it up again now. The list also refreshes itself,
 * so the spinner shows for those refreshes too.
 */
export const RefreshControl: FC<{
    onClick: () => void;
    refreshing: boolean;
    /** `dataUpdatedAt` of the query, 0 before its first answer. */
    updatedAt: number;
}> = ({ onClick, refreshing, updatedAt }) => (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, ml: 'auto' }}>
        {updatedAt > 0 && <UpdatedAgo updatedAt={updatedAt} />}
        <Tooltip title='Check again'>
            {/* a disabled button fires no events, so the span keeps the tooltip working while it refreshes */}
            <span>
                <IconButton size='small' onClick={onClick} disabled={refreshing} aria-label='Check again'>
                    {refreshing ? <CircularProgress size={18} /> : <RefreshIcon fontSize='small' />}
                </IconButton>
            </span>
        </Tooltip>
    </Box>
);

/** A checkbox in the secondary color, plain grey when unchecked so a checked one stands out. */
export const SelectCheckbox: FC<CheckboxProps> = ({ sx, ...props }) => (
    <Checkbox
        size='small'
        color='secondary'
        {...props}
        sx={[{ color: theme => theme.palette.checkboxUnchecked }, ...(Array.isArray(sx) ? sx : [sx])]}
    />
);

/**
 * The checkbox leading a row. Its 8px inset matches the select-all and group checkboxes, so they line up;
 * no top padding centers it on the row's first line of text.
 */
export const SelectCell: FC<{ checked: boolean; onChange: (checked: boolean) => void; label: string }> = ({
    checked,
    onChange,
    label
}) => (
    <TableCell sx={{ pl: 1, pr: 0, pt: 0 }}>
        <SelectCheckbox
            checked={checked}
            onChange={event => onChange(event.target.checked)}
            inputProps={{ 'aria-label': label }}
        />
    </TableCell>
);

// The theme's outlined error button and DeleteIconButton, in the color Extension Scans allows in.
const allowColors = (theme: Theme) => ({
    backgroundColor: theme.palette.surface2,
    border: `1px solid ${theme.palette.divider}`,
    color: theme.palette.allowed,
    transition: 'background-color 0.15s',
    '&:hover': {
        backgroundColor: theme.palette.allowed,
        borderColor: theme.palette.allowed,
        color: theme.palette.common.white
    }
});

// Circular twin of DeleteIconButton, so the two sit side by side in a row.
const AllowIconRoot = styled(IconButton)(({ theme }) => ({
    width: '1.875rem',
    height: '1.875rem',
    ...allowColors(theme)
}));

/** Allows the pair of one row, or every pair of a group. */
export const AllowIconButton: FC<IconButtonProps & { tooltip?: string }> = ({ tooltip = 'Allow pair', ...props }) => (
    <Tooltip title={tooltip}>
        <AllowIconRoot {...props}>
            <CheckIcon sx={{ fontSize: '0.9375rem' }} />
        </AllowIconRoot>
    </Tooltip>
);

/** An on/off filter, as the Extension Scans toolbars show them. */
export const FilterCheckbox: FC<{
    label: string;
    checked: boolean;
    onChange: (checked: boolean) => void;
    disabled?: boolean;
}> = ({ label, checked, onChange, disabled }) => (
    <FormControlLabel
        control={
            <SelectCheckbox checked={checked} disabled={disabled} onChange={event => onChange(event.target.checked)} />
        }
        label={<Typography variant='body2'>{label}</Typography>}
        sx={{ mr: 0 }}
    />
);

const Bar = styled(Box)(({ theme }) => ({
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: theme.spacing(1, 2),
    padding: theme.spacing(0.5, 2, 0.5, 1),
    marginBottom: theme.spacing(2),
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius
}));

/** Select all, how many pairs are selected, and the button that allows them. */
export const SelectionBar: FC<{
    selected: number;
    total: number;
    onSelectAll: (selected: boolean) => void;
    onAction: () => void;
}> = ({ selected, total, onSelectAll, onAction }) => (
    <Bar>
        <FormControlLabel
            control={
                <SelectCheckbox
                    checked={total > 0 && selected === total}
                    indeterminate={selected > 0 && selected < total}
                    onChange={event => onSelectAll(event.target.checked)}
                />
            }
            label={<Typography variant='body2'>Select all</Typography>}
            sx={{ ml: 0, mr: 0 }}
        />
        <Typography variant='body2' color='text.secondary'>
            {selected > 0 ? `${pluralize(selected, 'pair')} selected` : 'Select pairs to allow them together'}
        </Typography>
        <Button
            variant='outlined'
            size='small'
            disabled={selected === 0}
            onClick={onAction}
            sx={theme => ({ ml: 'auto', ...allowColors(theme) })}>
            Allow selected
        </Button>
    </Bar>
);
