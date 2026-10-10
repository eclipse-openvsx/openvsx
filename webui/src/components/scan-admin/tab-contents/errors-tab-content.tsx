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
import { Box, Typography, Pagination, CircularProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ScanCard } from '../scan-card';
import { SearchToolbar, CountsToolbar } from '../toolbars';
import { AutoRefresh } from '../common';
import { useErrorsTab } from '../../../hooks/scan-admin';

/**
 * Errors tab component that displays scans that ended in an error and lets an admin
 * activate the extension version anyway, e.g. after a scanner bug.
 */
export const ErrorsTabContent: FunctionComponent = () => {
    const theme = useTheme();
    const {
        scans,
        isLoading,
        lastRefreshed,
        autoRefresh,
        onAutoRefreshChange,
        totalCount,
        search,
        globalFilters,
        quarantineFilters,
        pagination,
        selectedCount,
        checked,
        toggleCheck,
        selectAll,
        deselectAll,
        isAllSelected,
        openAllowDialog
    } = useErrorsTab();

    return (
        <>
            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                <SearchToolbar
                    publisherQuery={search.publisherQuery}
                    namespaceQuery={search.namespaceQuery}
                    nameQuery={search.nameQuery}
                    onPublisherChange={search.handlePublisherChange}
                    onNamespaceChange={search.handleNamespaceChange}
                    onNameChange={search.handleNameChange}
                    filters={[
                        {
                            label: 'Allowed',
                            value: 'allowed',
                            checked: quarantineFilters.filters.has('allowed'),
                            onChange: quarantineFilters.toggle
                        },
                        {
                            label: 'Needs Review',
                            value: 'needs-review',
                            checked: quarantineFilters.filters.has('needs-review'),
                            onChange: quarantineFilters.toggle
                        }
                    ]}
                    showSelectAll={true}
                    allSelected={isAllSelected}
                    onSelectAllChange={isSelected => (isSelected ? selectAll() : deselectAll())}
                    actionButtons={[
                        {
                            label: 'ALLOW',
                            color: theme.palette.allowed!,
                            disabled: selectedCount === 0,
                            onClick: openAllowDialog
                        }
                    ]}
                    selectedCount={selectedCount}
                />
                <CountsToolbar
                    counts={[{ label: 'Total', value: totalCount, color: 'text.primary' }]}
                    dateRange={globalFilters.dateRange}
                    onDateRangeChange={globalFilters.setDateRange}
                    enforcement={globalFilters.enforcement}
                    onEnforcementChange={globalFilters.setEnforcement}
                />
                <AutoRefresh
                    lastRefreshed={lastRefreshed}
                    autoRefresh={autoRefresh}
                    onAutoRefreshChange={onAutoRefreshChange}
                />
            </Box>
            {isLoading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
                    <CircularProgress color='secondary' />
                </Box>
            ) : scans.length === 0 ? (
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 4 }}>
                    <Typography variant='h6' color='text.secondary'>
                        No errored scans
                    </Typography>
                    <Typography variant='body2' color='text.secondary' sx={{ mt: 1 }}>
                        Extensions whose scan ended in an error will appear here
                    </Typography>
                </Box>
            ) : (
                scans.map(scan => (
                    <ScanCard
                        key={scan.id}
                        scan={scan}
                        showCheckbox={!scan.adminDecision?.decision}
                        checked={checked[scan.id] || false}
                        onCheckboxChange={toggleCheck}
                    />
                ))
            )}
            {pagination.totalPages > 1 && (
                <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3, mb: 2 }}>
                    <Pagination
                        count={pagination.totalPages}
                        page={pagination.currentPage + 1}
                        onChange={(_, page) => pagination.goToPage(page - 1)}
                        disabled={isLoading}
                        color='secondary'
                        size='large'
                        showFirstButton
                        showLastButton
                    />
                </Box>
            )}
        </>
    );
};
