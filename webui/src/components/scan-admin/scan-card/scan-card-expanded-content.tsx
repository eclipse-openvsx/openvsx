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

import { FC } from 'react';
import { Box, Typography, Collapse, Chip, Link } from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { useTheme, Theme } from '@mui/material/styles';
import { ScanResult, Threat, ValidationFailure, CheckResult } from '../../../context/scan-admin';
import { ScanDetailCard } from './scan-detail-card';
import { formatDateTime } from '../common';

interface ScanCardExpandedContentProps {
    scan: ScanResult;
    expanded: boolean;
    onCollapseComplete?: () => void;
}

interface ThreatItemProps {
    threat: Threat;
}

interface ValidationFailureItemProps {
    failure: ValidationFailure;
}

interface CheckResultItemProps {
    checkResult: CheckResult;
}

/**
 * A single threat item in the expanded content.
 */
const ThreatItem: FC<ThreatItemProps> = ({ threat }) => {
    const theme = useTheme();

    return (
        <ScanDetailCard
            accentColor={theme.palette.quarantined.dark as string}
            isUnenforced={!threat.enforcedFlag}
            description={threat.reason}
            descriptionColor='warning.dark'
            details={[
                { label: 'File', value: threat.fileName || '(package-level scan)' },
                { label: 'Hash', value: threat.fileHash || undefined },
                { label: 'Scanner', value: threat.type },
                { label: 'Rule Name', value: threat.ruleName },
                { label: 'Severity', value: threat.severity },
                { label: 'Detected at', value: threat.dateDetected ? formatDateTime(threat.dateDetected) : undefined },
            ]}
        />
    );
};

/**
 * A single validation failure item in the expanded content.
 */
const ValidationFailureItem: FC<ValidationFailureItemProps> = ({ failure }) => {
    const theme = useTheme();
    const isUnenforced = !failure.enforcedFlag;

    return (
        <ScanDetailCard
            accentColor={theme.palette.rejected.dark as string}
            isUnenforced={isUnenforced}
            chip={{
                label: failure.type,
                color: theme.palette.rejected.dark as string,
                textColor: theme.palette.rejected.light as string,
            }}
            description={failure.reason}
            details={[
                { label: 'Rule Name', value: failure.ruleName },
                { label: 'Detected at', value: failure.dateDetected ? formatDateTime(failure.dateDetected) : undefined },
            ]}
        />
    );
};

/**
 * Get color for check result status.
 */
const getCheckResultColor = (result: CheckResult['result'], theme: Theme) => {
    switch (result) {
        case 'PASSED':
            return { bg: theme.palette.success.dark, text: theme.palette.success.light };
        case 'QUARANTINE':
            // Orange/amber - scanner found enforced threats, recommends quarantine
            return { bg: theme.palette.quarantined.dark as string, text: theme.palette.quarantined.light as string };
        case 'REJECT':
            // Red - publish check recommends rejection
            return { bg: theme.palette.rejected.dark as string, text: theme.palette.rejected.light as string };
        case 'ERROR':
            return { bg: theme.palette.errorStatus.dark as string, text: theme.palette.errorStatus.light as string };
        default:
            return { bg: theme.palette.grey[500], text: theme.palette.grey[100] };
    }
};

/**
 * Format duration in milliseconds to a readable string.
 */
const formatDuration = (ms: number | null): string | undefined => {
    if (ms === null) return undefined;
    if (ms === 0) return '<1ms';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
};

/**
 * A single check result item showing what check was run and its outcome.
 */
const CheckResultItem: FC<CheckResultItemProps> = ({ checkResult }) => {
    const theme = useTheme();
    const colors = getCheckResultColor(checkResult.result, theme);
    const isPassed = checkResult.result === 'PASSED';
    const isError = checkResult.result === 'ERROR';
    // Non-required errors get striped styling to indicate they didn't block publishing
    const isOptionalError = isError && checkResult.required === false;

    // Scanner rows often carry long verdict text while publish checks just show
    // short statuses like "No issues found"
    const isScannerJob = checkResult.category === 'SCANNER_JOB';
    const showSummaryInMiddle = isScannerJob && !!checkResult.summary;

    return (
        <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            p: 1.5,
            borderRadius: 1,
            bgcolor: isPassed ? 'action.hover' : 'transparent',
            border: isPassed ? 'none' : `1px solid ${theme.palette.divider}`,
        }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexShrink: 0 }}>
                <Chip
                    label={checkResult.result}
                    size='small'
                    sx={{
                        bgcolor: isOptionalError ? 'transparent' : colors.bg,
                        background: isOptionalError
                            ? `${theme.palette.unenforced.stripe}, ${colors.bg}`
                            : undefined,
                        color: colors.text,
                        fontWeight: 600,
                        fontSize: '0.7rem',
                        height: 22,
                    }}
                />
                <Typography variant='body2' sx={{ fontWeight: 500 }}>
                    {checkResult.checkType}
                </Typography>
                <Chip
                    label={checkResult.category === 'PUBLISH_CHECK' ? 'Publish Check' : 'Scanner'}
                    size='small'
                    variant='outlined'
                    sx={{ fontSize: '0.65rem', height: 18 }}
                />
            </Box>
            {showSummaryInMiddle && (
                <Typography
                    variant='caption'
                    sx={{
                        flex: 1,
                        minWidth: 0,
                        lineHeight: 1.5,
                        color: 'text.secondary',
                    }}
                >
                    {checkResult.summary}
                </Typography>
            )}
            <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2,
                ml: 'auto',
                color: 'text.secondary',
                flexShrink: 0,
            }}>
                {!showSummaryInMiddle && checkResult.summary && (
                    <Typography variant='caption'>
                        {checkResult.summary}
                    </Typography>
                )}
                {checkResult.externalUrl && (
                    <Link
                        href={checkResult.externalUrl}
                        target='_blank'
                        rel='noopener noreferrer'
                        onClick={(e) => e.stopPropagation()}
                        sx={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 0.25,
                            fontSize: '0.7rem',
                            fontWeight: 500,
                            whiteSpace: 'nowrap',
                        }}
                    >
                        View in scanner
                        <OpenInNewIcon sx={{ fontSize: '0.85rem' }} />
                    </Link>
                )}
                {checkResult.durationMs !== null && (
                    <Typography variant='caption' sx={{ minWidth: 50, textAlign: 'right' }}>
                        {formatDuration(checkResult.durationMs)}
                    </Typography>
                )}
            </Box>
        </Box>
    );
};

/**
 * The expanded content section showing threats, validation failures, and check results.
 * Each item's enforcedFlag controls its individual striping effect.
 */
export const ScanCardExpandedContent: FC<ScanCardExpandedContentProps> = ({ scan, expanded, onCollapseComplete }) => {
    const theme = useTheme();
    const hasThreats = scan.threats.length > 0;
    const hasValidationFailures = scan.validationFailures.length > 0;
    const hasCheckResults = scan.checkResults && scan.checkResults.length > 0;
    const hasErrorMessage = scan.status === 'ERROR' && scan.errorMessage;
    const hasAnyContent = hasThreats || hasValidationFailures || hasCheckResults || hasErrorMessage;

    return (
        <Collapse in={expanded} timeout='auto' unmountOnExit onExited={onCollapseComplete}>
            <Box sx={{
                px: 4,
                pb: 3,
                pt: 2,
                borderBottomRightRadius: 8,
            }}>
                {/* Error Message */}
                {hasErrorMessage && (
                    <Box sx={{ mb: hasAnyContent ? 2 : 0 }}>
                        <ScanDetailCard
                            accentColor={theme.palette.errorStatus.dark as string}
                            chip={{
                                label: 'ERROR',
                                color: theme.palette.errorStatus.dark as string,
                                textColor: theme.palette.errorStatus.light as string,
                            }}
                            description={scan.errorMessage ?? undefined}
                            descriptionColor='text.secondary'
                            details={[]}
                        />
                    </Box>
                )}

                {/* Check Results - What scans/checks were run */}
                {hasCheckResults && (
                    <Box sx={{ mb: (hasThreats || hasValidationFailures) ? 2 : 0 }}>
                        <Typography variant='subtitle2' sx={{ mb: 1.5, color: 'text.secondary' }}>
                            Checks Executed
                        </Typography>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                            {scan.checkResults.map((result, index) => (
                                <CheckResultItem key={index} checkResult={result} />
                            ))}
                        </Box>
                    </Box>
                )}

                {/* Threats */}
                {hasThreats && (
                    <Box>
                        <Typography variant='subtitle2' sx={{ mb: 1.5, color: 'text.secondary' }}>
                            Threats Detected
                        </Typography>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                            {scan.threats.map((threat, index) => (
                                <ThreatItem key={index} threat={threat} />
                            ))}
                        </Box>
                    </Box>
                )}

                {/* Validation Failures */}
                {hasValidationFailures && (
                    <Box sx={{ mt: hasThreats ? 2 : 0 }}>
                        <Typography variant='subtitle2' sx={{ mb: 1.5, color: 'text.secondary' }}>
                            Validation Failures
                        </Typography>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                            {scan.validationFailures.map((failure, index) => (
                                <ValidationFailureItem key={index} failure={failure} />
                            ))}
                        </Box>
                    </Box>
                )}
            </Box>
        </Collapse>
    );
};
