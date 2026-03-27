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
package org.eclipse.openvsx.scanning;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs all publish checks against extension files and aggregates results.
 */
@Component
public class PublishCheckRunner{

    private static final Logger logger = LoggerFactory.getLogger(PublishCheckRunner.class);

    private final List<PublishCheck> checks;

    public PublishCheckRunner(List<PublishCheck> checks) {
        this.checks = checks;
        
        logger.info("PublishCheckRunner initialized with {} checks: {}",
            checks.size(),
            checks.stream().map(PublishCheck::getCheckType).toList());
    }
    
    /**
     * Get the list of check types that this runner will execute.
     */
    public List<String> getExpectedCheckTypes() {
        return checks.stream()
            .map(PublishCheck::getCheckType)
            .toList();
    }
    
    /**
     * Get the number of checks that will be executed.
     */
    public int getCheckCount() {
        return checks.size();
    }

    /**
     * Run all publish checks and return the aggregated result.
     * <p>
     * This method does not persist anything. It only executes checks and returns findings.
     * The caller is responsible for recording failures and managing state.
     */
    @Nonnull
    public Result runChecks(
            @Nonnull ExtensionScan scan,
            @Nonnull TempFile extensionFile,
            @Nonnull UserData user
    ) {
        var context = new PublishCheck.Context(scan, extensionFile, user);
        var allFindings = new ArrayList<Finding>();
        var checkExecutions = new ArrayList<CheckExecution>();
        var allErrors = new ArrayList<CheckError>();
        boolean hasEnforcedFailure = false;
        boolean hasRequiredCheckError = false;

        String extId = scan.getNamespaceName() + "." + scan.getExtensionName() + " " + scan.getExtensionVersion();
        
        for (var check : checks) {
            // Skip disabled checks - no record created
            if (!check.isEnabled()) {
                logger.debug("Scan {} ({}) - Skipping disabled check: {}", scan.getId(), extId, check.getCheckType());
                continue;
            }

            logger.debug("Scan {} ({}) - Running check: {}", scan.getId(), extId, check.getCheckType());
            
            var startTime = TimeUtil.getCurrentUTC();
            try {
                var result = check.check(context);
                var endTime = TimeUtil.getCurrentUTC();
                logger.debug("Scan {} ({}) - Check {} passed: {}", scan.getId(), extId, check.getCheckType(), result.passed());
                
                int findingsCount = 0;
                ScanCheckResult.CheckResult checkResult;
                String summary;
                
                if (result.passed()) {
                    checkResult = ScanCheckResult.CheckResult.PASSED;
                    summary = "No issues found";
                } else {
                    boolean enforced = check.isEnforced();
                    findingsCount = result.failures().size();
                    
                    // Get the user-facing message for this check's failures.
                    String userFacingMessage = check.getUserFacingMessage(result.failures());
                    
                    // Convert each failure to a Finding.
                    for (var failure : result.failures()) {
                        allFindings.add(new Finding(
                            check.getCheckType(),
                            failure.ruleName(),
                            failure.reason(),
                            enforced,
                            userFacingMessage
                        ));
                    }
                    
                    if (enforced) {
                        hasEnforcedFailure = true;
                        // Enforced issues reject the extension immediately
                        checkResult = ScanCheckResult.CheckResult.REJECT;
                        summary = String.format("Found %d issue(s) - rejected", findingsCount);
                    } else {
                        // Issues found but not enforced - extension still passes
                        checkResult = ScanCheckResult.CheckResult.PASSED;
                        summary = String.format("Found %d issue(s) - not enforced", findingsCount);
                    }
                    
                    logger.debug("Scan {} - {} detected {} issue(s), enforced={}",
                        scan.getId(), check.getCheckType(), findingsCount, enforced);
                }
                
                checkExecutions.add(new CheckExecution(
                    check.getCheckType(),
                    startTime,
                    endTime,
                    checkResult,
                    findingsCount,
                    null,
                    summary,
                    check.isRequired()
                ));
                
            } catch (Exception e) {
                var endTime = TimeUtil.getCurrentUTC();
                boolean isRequired = check.isRequired();
                
                // Record the check execution with ERROR result
                checkExecutions.add(new CheckExecution(
                    check.getCheckType(),
                    startTime,
                    endTime,
                    ScanCheckResult.CheckResult.ERROR,
                    0,
                    e.getMessage(),
                    "Error: " + e.getMessage(),
                    isRequired
                ));
                
                allErrors.add(new CheckError(check.getCheckType(), e, isRequired));
                
                if (isRequired) {
                    // Required check error - stop processing remaining checks
                    hasRequiredCheckError = true;
                    logger.warn("Scan {} ({}.{}.{}) - Required check {} threw exception, blocking publish: {}",
                        scan.getId(), scan.getNamespaceName(), scan.getExtensionName(),
                        scan.getExtensionVersion(), check.getCheckType(), e.getMessage());
                    break;
                } else {
                    // Non-required check error - log and continue with remaining checks
                    logger.warn("Scan {} ({}.{}.{}) - Non-required check {} threw exception, continuing: {}",
                        scan.getId(), scan.getNamespaceName(), scan.getExtensionName(),
                        scan.getExtensionVersion(), check.getCheckType(), e.getMessage());
                }
            }
        }

        return new Result(allFindings, checkExecutions, allErrors, hasEnforcedFailure, hasRequiredCheckError);
    }


    /**
     * Result of running all publish checks.
     */
    public record Result(
        @Nonnull List<Finding> findings,
        @Nonnull List<CheckExecution> checkExecutions,
        @Nonnull List<CheckError> errors,
        boolean hasEnforcedFailure,
        boolean hasRequiredCheckError
    ) {
        /**
         * Check if all checks passed (no enforced failures and no required check errors).
         * <p>
         * Non-required check errors are logged but don't block publishing.
         */
        public boolean passed() {
            return !hasEnforcedFailure && !hasRequiredCheckError;
        }

        /**
         * Check if there was any error during checks (required or not).
         */
        public boolean hasError() {
            return !errors.isEmpty();
        }

        /**
         * Get errors from required checks (these block publishing).
         */
        @Nonnull
        public List<CheckError> getRequiredErrors() {
            return errors.stream()
                .filter(CheckError::required)
                .toList();
        }

        /**
         * Get errors from non-required checks (these don't block publishing).
         */
        @Nonnull
        public List<CheckError> getNonRequiredErrors() {
            return errors.stream()
                .filter(e -> !e.required())
                .toList();
        }

        /**
         * Get the error message summarizing all required errors.
         * If multiple required checks failed, lists them all.
         */
        @Nullable
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return null;
            }
            
            var requiredErrors = getRequiredErrors();
            if (requiredErrors.isEmpty()) {
                var error = errors.getFirst();
                return error.checkType() + " check failed: " + error.exception().getMessage();
            }
            
            if (requiredErrors.size() == 1) {
                var error = requiredErrors.getFirst();
                return error.checkType() + " check failed: " + error.exception().getMessage();
            }
            
            // Multiple required errors - list them all with count
            var sb = new StringBuilder();
            sb.append(requiredErrors.size()).append(" required checks failed: ");
            for (int i = 0; i < requiredErrors.size(); i++) {
                var error = requiredErrors.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(error.checkType()).append(": ").append(error.exception().getMessage());
            }
            return sb.toString();
        }

        /**
         * Get findings that are enforced (would block publication).
         */
        @Nonnull
        public List<Finding> getEnforcedFindings() {
            return findings.stream()
                .filter(Finding::enforced)
                .toList();
        }

        /**
         * Get findings that are not enforced (warnings only).
         */
        @Nonnull
        public List<Finding> getWarningFindings() {
            return findings.stream()
                .filter(f -> !f.enforced())
                .toList();
        }
    }

    /**
     * An error that occurred during a check execution.
     */
    public record CheckError(
        @Nonnull String checkType,
        @Nonnull Exception exception,
        boolean required
    ) {}

    /**
     * A single finding from a publish check.
     * Contains all info needed for the service to record the failure.
     */
    public record Finding(
        @Nonnull String checkType,
        @Nonnull String ruleName,
        @Nullable String reason,
        boolean enforced,
        @Nullable String userFacingMessage
    ) {}

    /**
     * Execution details for a single check.
     * Used to record all checks that were run for audit trail.
     */
    public record CheckExecution(
        @Nonnull String checkType,
        @Nonnull LocalDateTime startedAt,
        @Nonnull LocalDateTime completedAt,
        @Nonnull ScanCheckResult.CheckResult result,
        int findingsCount,
        @Nullable String errorMessage,
        @Nullable String summary,
        boolean required
    ) {}
}
