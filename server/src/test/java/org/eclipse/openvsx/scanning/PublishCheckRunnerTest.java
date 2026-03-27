/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.scanning;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.ScanCheckResult;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TempFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

/**
 * Tests for {@link PublishCheckRunner} orchestration logic.
 */
@ExtendWith(MockitoExtension.class)
class PublishCheckRunnerTest {

    @Mock ExtensionScan scan;
    @Mock TempFile extensionFile;
    @Mock UserData user;

    @Test
    void runChecks_passesWhenAllChecksPass() {
        var check1 = mockCheck("CHECK1", true, true, PublishCheck.Result.pass());
        var check2 = mockCheck("CHECK2", true, true, PublishCheck.Result.pass());
        var runner = new PublishCheckRunner(List.of(check1, check2));

        var result = runner.runChecks(scan, extensionFile, user);

        assertTrue(result.passed());
        assertFalse(result.hasEnforcedFailure());
        assertFalse(result.hasError());
        assertTrue(result.findings().isEmpty());
        assertEquals(2, result.checkExecutions().size());
    }

    @Test
    void runChecks_failsWhenEnforcedCheckFails() {
        var failure = new PublishCheck.Failure("RULE_001", "Found secret");
        var passingCheck = mockCheck("PASS", true, true, PublishCheck.Result.pass());
        var failingCheck = mockCheck("FAIL", true, true, PublishCheck.Result.fail(List.of(failure)));
        var runner = new PublishCheckRunner(List.of(passingCheck, failingCheck));

        var result = runner.runChecks(scan, extensionFile, user);

        assertFalse(result.passed());
        assertTrue(result.hasEnforcedFailure());
        assertEquals(1, result.findings().size());
        assertEquals("RULE_001", result.findings().getFirst().ruleName());
    }

    @Test
    void runChecks_passesWhenNonEnforcedCheckFails() {
        var failure = new PublishCheck.Failure("RULE_001", "Found issue");
        var failingCheck = mockCheck("CHECK", true, false, PublishCheck.Result.fail(List.of(failure)));
        var runner = new PublishCheckRunner(List.of(failingCheck));

        var result = runner.runChecks(scan, extensionFile, user);

        assertTrue(result.passed());
        assertFalse(result.hasEnforcedFailure());
        assertEquals(1, result.findings().size());
        assertFalse(result.findings().getFirst().enforced());
    }

    @Test
    void runChecks_skipsDisabledChecks() {
        var disabledCheck = mockCheck("DISABLED", false, true, PublishCheck.Result.pass());
        var enabledCheck = mockCheck("ENABLED", true, true, PublishCheck.Result.pass());
        var runner = new PublishCheckRunner(List.of(disabledCheck, enabledCheck));

        var result = runner.runChecks(scan, extensionFile, user);

        assertTrue(result.passed());
        // Only enabled check should have an execution record
        assertEquals(1, result.checkExecutions().size());
        assertEquals("ENABLED", result.checkExecutions().getFirst().checkType());
    }

    @Test
    void runChecks_capturesCheckError() {
        var errorCheck = mock(PublishCheck.class);
        when(errorCheck.getCheckType()).thenReturn("ERROR_CHECK");
        when(errorCheck.isEnabled()).thenReturn(true);
        when(errorCheck.isRequired()).thenReturn(true);
        when(errorCheck.check(any())).thenThrow(new RuntimeException("Check failed"));
        var runner = new PublishCheckRunner(List.of(errorCheck));

        var result = runner.runChecks(scan, extensionFile, user);

        assertFalse(result.passed());
        assertTrue(result.hasError());
        assertEquals("ERROR_CHECK", result.getRequiredErrors().getFirst().checkType());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void runChecks_stopsAfterError() {
        var errorCheck = mock(PublishCheck.class);
        when(errorCheck.getCheckType()).thenReturn("ERROR");
        when(errorCheck.isEnabled()).thenReturn(true);
        when(errorCheck.isRequired()).thenReturn(true);
        when(errorCheck.check(any())).thenThrow(new RuntimeException("Boom"));

        // Create a simple mock that won't be called
        var afterErrorCheck = mock(PublishCheck.class);
        when(afterErrorCheck.getCheckType()).thenReturn("AFTER");
        
        var runner = new PublishCheckRunner(List.of(errorCheck, afterErrorCheck));

        var result = runner.runChecks(scan, extensionFile, user);

        // Should have only one execution (the erroring check)
        assertEquals(1, result.checkExecutions().size());
        verify(afterErrorCheck, never()).check(any());
    }

    @Test
    void getExpectedCheckTypes_returnsAllCheckTypes() {
        var check1 = mockCheck("TYPE_A", true, true, PublishCheck.Result.pass());
        var check2 = mockCheck("TYPE_B", true, true, PublishCheck.Result.pass());
        var runner = new PublishCheckRunner(List.of(check1, check2));

        var types = runner.getExpectedCheckTypes();

        assertEquals(List.of("TYPE_A", "TYPE_B"), types);
    }

    @Test
    void getCheckCount_returnsCount() {
        var check1 = mockCheck("A", true, true, PublishCheck.Result.pass());
        var check2 = mockCheck("B", true, true, PublishCheck.Result.pass());
        var runner = new PublishCheckRunner(List.of(check1, check2));

        assertEquals(2, runner.getCheckCount());
    }

    @Test
    void result_getEnforcedFindings_filtersCorrectly() {
        var enforcedFinding = new PublishCheckRunner.Finding("CHECK", "RULE1", "reason", true, "msg");
        var warningFinding = new PublishCheckRunner.Finding("CHECK", "RULE2", "reason", false, "msg");
        var result = new PublishCheckRunner.Result(
                List.of(enforcedFinding, warningFinding),
                List.of(),
                List.of(),
                true,
                false
        );

        var enforced = result.getEnforcedFindings();
        var warnings = result.getWarningFindings();

        assertEquals(1, enforced.size());
        assertEquals("RULE1", enforced.getFirst().ruleName());
        assertEquals(1, warnings.size());
        assertEquals("RULE2", warnings.getFirst().ruleName());
    }

    @Test
    void checkExecution_recordsCorrectResult() {
        var failure = new PublishCheck.Failure("RULE", "Issue found");
        var failingCheck = mockCheck("TEST", true, true, PublishCheck.Result.fail(List.of(failure)));
        var runner = new PublishCheckRunner(List.of(failingCheck));

        var result = runner.runChecks(scan, extensionFile, user);

        var execution = result.checkExecutions().getFirst();
        assertEquals("TEST", execution.checkType());
        assertEquals(ScanCheckResult.CheckResult.REJECT, execution.result());
        assertEquals(1, execution.findingsCount());
        assertNotNull(execution.startedAt());
        assertNotNull(execution.completedAt());
    }

    /**
     * Create a mock PublishCheck with lenient stubbing to avoid strict stubbing errors.
     */
    private PublishCheck mockCheck(String type, boolean enabled, boolean enforced, PublishCheck.Result result) {
        var check = mock(PublishCheck.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(check.getCheckType()).thenReturn(type);
        when(check.isEnabled()).thenReturn(enabled);
        when(check.isEnforced()).thenReturn(enforced);
        when(check.isRequired()).thenReturn(true);
        when(check.check(any())).thenReturn(result);
        when(check.getUserFacingMessage(any())).thenReturn("User message");
        return check;
    }
}
