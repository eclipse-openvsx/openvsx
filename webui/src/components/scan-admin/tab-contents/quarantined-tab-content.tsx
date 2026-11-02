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
import { ScanCard } from '../scan-card';
import { SearchToolbar, CountsToolbar } from '../toolbars';
import { AutoRefresh } from '../common';
import { useQuarantinedTab, needsQuarantineReview } from '../../../hooks/scan-admin';
import { useScanContext } from '../../../context/scan-admin';
import { useTheme } from '@mui/material/styles';

/**
 * Quarantined tab component that displays extensions flagged by security scans.
 * Uses the useQuarantinedTab hook to consume context.
 */
export const QuarantinedTabContent: FunctionComponent = () => {
    const theme = useTheme();
    const { state } = useScanContext();
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
        selection,
        toggleCheck,
        selectAll,
        deselectAll,
        isAllSelected,
        bulkActions
    } = useQuarantinedTab();

    // Calculate allowed/blocked/needs review counts from scanCounts
    const allowedCount = state.scanCounts?.ALLOWED ?? 0;
    const blockedCount = state.scanCounts?.BLOCKED ?? 0;
    const needsReviewCount = state.scanCounts?.NEEDS_REVIEW ?? 0;

    const handleSelectAllChange = (checked: boolean) => {
        if (checked) {
            selectAll();
        } else {
            deselectAll();
        }
    };

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
                            label: 'Blocked',
                            value: 'blocked',
                            checked: quarantineFilters.filters.has('blocked'),
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
                    onSelectAllChange={handleSelectAllChange}
                    actionButtons={[
                        {
                            label: 'ALLOW',
                            color: theme.palette.allowed!,
                            disabled: !bulkActions.canPerformBulkAction,
                            onClick: bulkActions.openAllowDialog
                        },
                        {
                            label: 'BLOCK',
                            color: theme.palette.blocked!,
                            disabled: !bulkActions.canPerformBulkAction,
                            onClick: bulkActions.openBlockDialog
                        }
                    ]}
                    selectedCount={selection.selectedCount}
                />
                <CountsToolbar
                    counts={[
                        { label: 'Total', value: totalCount, color: 'text.primary' },
                        { label: 'Allowed', value: allowedCount, color: theme.palette.allowed },
                        { label: 'Blocked', value: blockedCount, color: theme.palette.blocked },
                        { label: 'Needs Review', value: needsReviewCount, color: theme.palette.review }
                    ]}
                    filterOptions={quarantineFilters.availableThreatScanners.map(scanner => ({
                        label: scanner,
                        value: scanner,
                        checked: quarantineFilters.threatScannerFilters.has(scanner)
                    }))}
                    onFilterOptionToggle={quarantineFilters.toggleThreatScanner}
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
                        No quarantined extensions
                    </Typography>
                    <Typography variant='body2' color='text.secondary' sx={{ mt: 1 }}>
                        Extensions flagged by security scans will appear here
                    </Typography>
                </Box>
            ) : (
                scans.map(scan => (
                    <ScanCard
                        key={scan.id}
                        scan={scan}
                        showCheckbox={needsQuarantineReview(scan)}
                        checked={selection.checked[scan.id] || false}
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
