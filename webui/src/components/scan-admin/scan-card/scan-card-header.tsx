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

import { FC, useState } from 'react';
import { Box, Typography, Chip, CircularProgress, Link } from '@mui/material';
import {
    AdminPanelSettings as AdminPanelIcon,
    CheckCircle as CheckCircleIcon,
    GppMaybe as WarningIcon,
    Block as BlockIcon,
    Cancel as CancelIcon,
    Info as InfoIcon,
} from '@mui/icons-material';
import { ScanResult } from '../../../context/scan-admin';
import { ConditionalTooltip } from '../common';
import { useTheme } from '@mui/material/styles';
import {
    ICON_SIZE,
    isRunning,
    shouldShowStriped,
    getHypotheticalStatus,
    getStatusColorSx,
} from './utils';
import { createRoute } from '../../../utils';
import { AdminDashboardRoutes } from '../../../pages/admin-dashboard/admin-routes';
import { ExtensionDetailRoutes } from '../../../pages/extension-detail/extension-detail-routes';

interface ScanCardHeaderProps {
    scan: ScanResult;
}

const getStatusIcon = (status: ScanResult['status']) => {
    switch (status) {
        case 'PASSED':
            return <CheckCircleIcon fontSize='small' />;
        case 'QUARANTINED':
            return <WarningIcon fontSize='small' />;
        case 'AUTO REJECTED':
            return <BlockIcon fontSize='small' />;
        case 'ERROR':
            return <CancelIcon fontSize='small' />;
        default:
            return null;
    }
};

/**
 * Header section of the ScanCard containing:
 * - Extension icon
 * - Display name and namespace
 * - Status badge
 */
export const ScanCardHeader: FC<ScanCardHeaderProps> = ({ scan }) => {
    const theme = useTheme();
    const [imageError, setImageError] = useState(false);

    const hasValidIcon = scan.extensionIcon && !imageError;
    const extensionRoute = createRoute([ExtensionDetailRoutes.ROOT, scan.namespace, scan.extensionName]);
    const adminRoute = createRoute([AdminDashboardRoutes.ROOT, 'extensions'],
        [
            { key: "namespace", value: scan.namespace },
            { key: "extension", value: scan.extensionName }
        ]);

    return (
        <>
            {/* Column 1: Icon */}
            <Box
                sx={{
                    gridRow: '1',
                    gridColumn: '1',
                    width: ICON_SIZE,
                    height: ICON_SIZE,
                    bgcolor: hasValidIcon ? 'transparent' : 'action.hover',
                    borderRadius: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    overflow: 'hidden',
                }}
            >
                {hasValidIcon ? (
                    <img
                        src={scan.extensionIcon}
                        alt={scan.displayName}
                        onError={() => setImageError(true)}
                        style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'contain',
                        }}
                    />
                ) : (
                    <Typography variant='h4' color='text.secondary'>
                        {scan.displayName.charAt(0).toUpperCase()}
                    </Typography>
                )}
            </Box>

            {/* Columns 2-5: Display Name and Namespace */}
            <Box sx={{ gridRow: '1', gridColumn: 'span 4', minWidth: 0 }}>
                <ConditionalTooltip title={scan.displayName} arrow>
                    <Typography
                        variant='h6'
                        sx={{
                            fontWeight: 600,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {scan.displayName}
                    </Typography>
                </ConditionalTooltip>
                <ConditionalTooltip title={`${scan.namespace}.${scan.extensionName}`} arrow>
                    <Typography
                        variant='body2'
                        color='text.secondary'
                        sx={{
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        <Link
                            href={extensionRoute || undefined}
                            rel='noopener noreferrer'
                            variant='body2'
                        >
                            {scan.namespace}.{scan.extensionName}
                        </Link>

                        {adminRoute !== undefined &&
                            <Link
                                href={adminRoute}
                                rel='noopener noreferrer'
                                variant='body2'
                            >
                                <AdminPanelIcon sx={{
                                    verticalAlign: 'bottom',
                                    ml: '3px'
                                }} />
                            </Link>
                        }
                    </Typography>
                </ConditionalTooltip>
            </Box>

            {/* Column 6: Status Badge */}
            <Box sx={{
                gridRow: '1',
                gridColumn: '6',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-end',
                justifyContent: 'flex-start',
                minWidth: 0,
                gap: 0.5,
            }}>
                {isRunning(scan.status) ? (
                    <CircularProgress size={32} color='secondary' />
                ) : (
                    <>
                        <Chip
                            label={scan.status}
                            size='medium'
                            icon={getStatusIcon(scan.status) || undefined}
                            sx={{
                                ...getStatusColorSx(scan.status, theme),
                                transform: 'scale(1.2)',
                                transformOrigin: 'right top',
                                ...(shouldShowStriped(scan) && {
                                    background: `${theme.palette.unenforced.stripe}, ${getStatusColorSx(scan.status, theme).backgroundColor}`,
                                }),
                            }}
                        />
                        {shouldShowStriped(scan) && getHypotheticalStatus(scan) && (
                            <Box sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 0.5,
                                mt: 0.5,
                            }}>
                                <InfoIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                                <Typography variant='caption' sx={{ color: 'text.secondary', fontSize: '0.7rem' }}>
                                    Would be {getHypotheticalStatus(scan)}
                                </Typography>
                            </Box>
                        )}
                    </>
                )}
            </Box>
        </>
    );
};
