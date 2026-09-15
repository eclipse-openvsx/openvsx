/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { Box, IconButton, Select, MenuItem, Typography, SelectChangeEvent } from '@mui/material';
import { SortBy, SortOrder } from '../../extension-registry-types';
import { ExtensionCategory } from '../../extension-registry-types';
import { Theme } from '@mui/material/styles';
import { compactControl } from '../../components/page-primitives';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';

// Distance from the content column's right edge to the viewport edge: the centered
// PageContainer's outer gap (its cap is the xl breakpoint) plus its 1.5rem gutter (sm and up).
const edgeBleed = (theme: Theme) => `calc((100vw - min(100vw, ${theme.breakpoints.values.xl}px)) / 2 + 1.5rem)`;

export const SearchHeader: FunctionComponent<SearchHeaderProps> = props => {
    const { sortBy, sortOrder, onSortByChanged, onSortOrderChanged } = props;

    const handleSortByChange = (event: SelectChangeEvent<string>) => {
        onSortByChanged(event.target.value as SortBy);
    };

    const toggleSortOrder = () => {
        onSortOrderChanged(sortOrder === 'asc' ? 'desc' : 'asc');
    };

    const title = props.searchQuery ? `"${props.searchQuery}"` : props.category || 'All extensions';

    return (
        <Box
            // Bleeds left to meet the sidebar seam and right to the viewport edge;
            // matching padding keeps the content itself on the column grid.
            sx={theme => ({
                pt: '1.75rem',
                pb: '0.875rem',
                mb: '1rem',
                ml: { md: '-1.25rem' },
                pl: { md: '1.25rem' },
                mr: { xs: '-1rem', sm: `calc(${edgeBleed(theme)} * -1)` },
                pr: { xs: '1rem', sm: edgeBleed(theme) },
                borderBottom: '1px solid',
                borderColor: 'border2',
                display: 'flex',
                alignItems: 'flex-start',
                justifyContent: 'space-between',
                gap: '1rem'
            })}>
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography
                    component='h1'
                    noWrap
                    sx={{
                        fontSize: { xs: '1.25rem', sm: '1.6rem' },
                        fontWeight: 700,
                        letterSpacing: '-0.025em',
                        lineHeight: 1.2,
                        color: 'text.primary'
                    }}>
                    {title}
                </Typography>
                <Typography sx={{ fontSize: '0.8125rem', color: 'text.disabled', mt: '0.3125rem' }}>
                    {props.resultNumber.toLocaleString()} extensions found
                </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.375rem', flexShrink: 0, mt: '0.375rem' }}>
                <Box component='span' sx={{ fontSize: '0.8125rem', color: 'text.disabled' }}>
                    Sort by
                </Box>
                <Select
                    value={sortBy}
                    onChange={handleSortByChange}
                    size='small'
                    inputProps={{ 'aria-label': 'Sort by' }}
                    sx={compactControl}>
                    <MenuItem value='relevance'>Relevance</MenuItem>
                    <MenuItem value='timestamp'>Date</MenuItem>
                    <MenuItem value='downloadCount'>Downloads</MenuItem>
                    <MenuItem value='rating'>Rating</MenuItem>
                </Select>
                <IconButton
                    onClick={toggleSortOrder}
                    title={sortOrder === 'asc' ? 'Ascending' : 'Descending'}
                    aria-label={sortOrder === 'asc' ? 'Sort ascending' : 'Sort descending'}
                    sx={{
                        color: 'text.disabled',
                        borderRadius: 1,
                        p: '0.1875rem',
                        transition: 'color 0.14s',
                        '&:hover': { color: 'secondary.light', bgcolor: 'transparent' }
                    }}>
                    {sortOrder === 'asc' ? (
                        <ArrowUpwardIcon fontSize='small' />
                    ) : (
                        <ArrowDownwardIcon fontSize='small' />
                    )}
                </IconButton>
            </Box>
        </Box>
    );
};

export interface SearchHeaderProps {
    onSortByChanged: (sb: SortBy) => void;
    onSortOrderChanged: (so: SortOrder) => void;
    sortBy: SortBy;
    sortOrder: SortOrder;
    resultNumber: number;
    searchQuery?: string;
    category?: ExtensionCategory | '';
}
