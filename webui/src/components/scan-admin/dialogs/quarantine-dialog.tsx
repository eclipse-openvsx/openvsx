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

import { FunctionComponent, useMemo } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Typography,
    List,
    ListItem,
    ListItemText,
    Box,
    Tooltip
} from '@mui/material';
import { Info as InfoIcon } from '@mui/icons-material';
import { useDialogs } from '../../../hooks/scan-admin';
import { useTheme } from '@mui/material/styles';
import { ScanResult } from '../../../context/scan-admin';

/**
 * Get only the enforced threats from a scan.
 */
const getEnforcedThreats = (scan: ScanResult) => {
    return scan.threats?.filter(threat => threat.enforcedFlag) || [];
};

/**
 * Get only the unenforced threats from a scan.
 */
const getUnenforcedThreats = (scan: ScanResult) => {
    return scan.threats?.filter(threat => !threat.enforcedFlag) || [];
};

/**
 * Get actionable threats - enforced threats that have file information.
 * Only these can be added to allow/block lists.
 */
const getActionableThreats = (scan: ScanResult) => {
    return scan.threats?.filter(threat => threat.enforcedFlag && threat.fileName) || [];
};

/**
 * Get threats without file information (not actionable for allow/block).
 * These are from scanners that don't report individual files.
 */
const getThreatsWithoutFileInfo = (scan: ScanResult) => {
    return scan.threats?.filter(threat => !threat.fileName) || [];
};

/**
 * Confirmation dialog for extension-level allow/block actions.
 * Uses the useDialogs hook to consume context.
 *
 * Only files from ENFORCED threats are actionable.
 * Unenforced threats are informational only and
 * should not have allow/block decisions made on them.
 */
export const QuarantineDialog: FunctionComponent = () => {
    const theme = useTheme();
    const { confirmDialog } = useDialogs();

    // Check if any extension has unenforced threats (to show info message)
    const hasAnyUnenforcedThreats = useMemo(() => {
        return confirmDialog.selectedExtensions.some((scan: ScanResult) => getUnenforcedThreats(scan).length > 0);
    }, [confirmDialog.selectedExtensions]);

    // Check if any extension has threats without file info (not actionable)
    const hasThreatsWithoutFileInfo = useMemo(() => {
        return confirmDialog.selectedExtensions.some((scan: ScanResult) => getThreatsWithoutFileInfo(scan).length > 0);
    }, [confirmDialog.selectedExtensions]);

    // Calculate total actionable files across all selected extensions
    // Only counts enforced threats that have file information
    const totalActionableFiles = useMemo(() => {
        return confirmDialog.selectedExtensions.reduce(
            (total: number, scan: ScanResult) => total + getActionableThreats(scan).length,
            0
        );
    }, [confirmDialog.selectedExtensions]);

    // An errored scan can still carry threats persisted by scanners that finished; those go through
    // the regular dialog so that the files about to be allow-listed are shown.
    const allErrored =
        confirmDialog.selectedExtensions.length > 0 &&
        confirmDialog.selectedExtensions.every(
            (scan: ScanResult) => scan.status === 'ERROR' && (scan.threats?.length ?? 0) === 0
        );

    if (allErrored) {
        const count = confirmDialog.selectedExtensions.length;
        return (
            <Dialog open={confirmDialog.isOpen} onClose={confirmDialog.close} maxWidth='md' fullWidth>
                <DialogTitle>Confirm Allow</DialogTitle>
                <DialogContent>
                    <Typography variant='body1' sx={{ mb: 2 }}>
                        Activate {count} extension version{count !== 1 ? 's' : ''} although the scan ended in an error?
                        {count !== 1 ? ' They become' : ' It becomes'} publicly available without a completed scan.
                    </Typography>
                    <List
                        sx={{
                            maxHeight: '400px',
                            overflow: 'auto',
                            border: `1px solid ${theme.palette.scanBackground.default}`,
                            borderRadius: 1
                        }}>
                        {confirmDialog.selectedExtensions.map((scan: ScanResult) => (
                            <ListItem key={scan.id}>
                                <ListItemText
                                    primary={`${scan.namespace}.${scan.extensionName} ${scan.version}`}
                                    secondary={scan.errorMessage}
                                />
                            </ListItem>
                        ))}
                    </List>
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 2 }}>
                    <Button onClick={confirmDialog.close} color='inherit'>
                        Cancel
                    </Button>
                    <Button onClick={confirmDialog.execute} variant='contained' color='success'>
                        Confirm Allow
                    </Button>
                </DialogActions>
            </Dialog>
        );
    }

    return (
        <Dialog open={confirmDialog.isOpen} onClose={confirmDialog.close} maxWidth='md' fullWidth>
            <DialogTitle>{confirmDialog.action === 'allow' ? 'Confirm Allow' : 'Confirm Block'}</DialogTitle>
            <DialogContent>
                <Typography variant='body1' sx={{ mb: 2 }}>
                    Are you sure you want to {confirmDialog.action} {totalActionableFiles} file
                    {totalActionableFiles !== 1 ? 's' : ''} from{' '}
                    {confirmDialog.selectedExtensions.length !== 1 ? 'these' : 'this'}{' '}
                    {confirmDialog.selectedExtensions.length} extension
                    {confirmDialog.selectedExtensions.length !== 1 ? 's' : ''}?
                </Typography>

                {/* Info message about unenforced threats */}
                {hasAnyUnenforcedThreats && (
                    <Box
                        sx={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: 1,
                            mb: 2,
                            p: 1.5,
                            backgroundColor: theme.palette.info.dark + '20',
                            borderRadius: 1,
                            border: `1px solid ${theme.palette.info.dark}40`
                        }}>
                        <InfoIcon sx={{ fontSize: 18, color: theme.palette.info.main, mt: 0.25 }} />
                        <Typography variant='body2' color='text.secondary'>
                            Some extensions have unenforced threats. These are informational only and will not be added
                            to the {confirmDialog.action === 'allow' ? 'allow' : 'block'} list.
                        </Typography>
                    </Box>
                )}

                {/* Info message about threats without file info */}
                {hasThreatsWithoutFileInfo && (
                    <Box
                        sx={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: 1,
                            mb: 2,
                            p: 1.5,
                            backgroundColor: theme.palette.info.dark + '20',
                            borderRadius: 1,
                            border: `1px solid ${theme.palette.info.dark}40`
                        }}>
                        <InfoIcon sx={{ fontSize: 18, color: theme.palette.info.main, mt: 0.25 }} />
                        <Typography variant='body2' color='text.secondary'>
                            Some threats do not have file-level information (e.g., from scanners that analyze the whole
                            package). These cannot be added to allow/block lists.
                        </Typography>
                    </Box>
                )}

                <List
                    sx={{
                        maxHeight: '400px',
                        overflow: 'auto',
                        border: `1px solid ${theme.palette.scanBackground.default}`,
                        borderRadius: 1
                    }}>
                    {confirmDialog.selectedExtensions.map((scan: ScanResult) => {
                        const actionableThreats = getActionableThreats(scan);
                        const enforcedThreats = getEnforcedThreats(scan);
                        const unenforcedThreats = getUnenforcedThreats(scan);

                        return (
                            <ListItem
                                key={scan.id}
                                sx={{
                                    borderBottom: `1px solid ${theme.palette.scanBackground.dark}`,
                                    '&:last-child': { borderBottom: 'none' },
                                    display: 'flex',
                                    alignItems: 'flex-start',
                                    gap: 2
                                }}>
                                <ListItemText
                                    sx={{ flex: 1, minWidth: 0 }}
                                    primary={
                                        <Typography
                                            variant='body1'
                                            sx={{
                                                fontWeight: 500,
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}>
                                            {scan.displayName}
                                        </Typography>
                                    }
                                    secondary={
                                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
                                            <Typography
                                                variant='body2'
                                                color='text.secondary'
                                                sx={{
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap'
                                                }}>
                                                {scan.namespace}.{scan.extensionName}
                                            </Typography>
                                            <Typography
                                                variant='body2'
                                                color='text.secondary'
                                                sx={{
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap'
                                                }}>
                                                Publisher: {scan.publisher}
                                            </Typography>
                                            <Typography
                                                variant='body2'
                                                color='text.secondary'
                                                sx={{
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap'
                                                }}>
                                                Version: {scan.version}
                                            </Typography>
                                        </Box>
                                    }
                                />
                                <Tooltip
                                    title={
                                        actionableThreats.length > 0 ? (
                                            <Box>
                                                <Typography variant='caption' sx={{ fontWeight: 600 }}>
                                                    Files to {confirmDialog.action}:
                                                </Typography>
                                                <Box sx={{ whiteSpace: 'pre-line', mt: 0.5 }}>
                                                    {actionableThreats.map((threat, index) => (
                                                        <div key={index}>{threat.fileName}</div>
                                                    ))}
                                                </Box>
                                            </Box>
                                        ) : (
                                            'No actionable files (no file-level info)'
                                        )
                                    }
                                    arrow>
                                    <Box
                                        sx={{
                                            flexShrink: 0,
                                            alignSelf: 'flex-start',
                                            mt: 0.5,
                                            cursor: 'pointer',
                                            textAlign: 'right'
                                        }}>
                                        <Typography variant='body2' color='text.secondary'>
                                            {actionableThreats.length} file{actionableThreats.length !== 1 ? 's' : ''}
                                        </Typography>
                                        {enforcedThreats.length - actionableThreats.length > 0 && (
                                            <Typography
                                                variant='caption'
                                                sx={{ color: 'text.disabled', display: 'block' }}>
                                                +{enforcedThreats.length - actionableThreats.length} without file info
                                            </Typography>
                                        )}
                                        {unenforcedThreats.length > 0 && (
                                            <Typography
                                                variant='caption'
                                                sx={{ color: 'text.disabled', display: 'block' }}>
                                                +{unenforcedThreats.length} unenforced
                                            </Typography>
                                        )}
                                    </Box>
                                </Tooltip>
                            </ListItem>
                        );
                    })}
                </List>
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2 }}>
                <Button onClick={confirmDialog.close} color='inherit'>
                    Cancel
                </Button>
                <Button
                    onClick={confirmDialog.execute}
                    variant='contained'
                    color={confirmDialog.action === 'allow' ? 'success' : 'error'}>
                    Confirm {confirmDialog.action === 'allow' ? 'Allow' : 'Block'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
