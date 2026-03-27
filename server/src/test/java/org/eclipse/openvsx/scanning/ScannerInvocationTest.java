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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Scanner.Invocation} nested sealed interface.
 */
class ScannerInvocationTest {

    @Test
    void completed_storesResult() {
        var result = Scanner.Result.clean();
        var invocation = new Scanner.Invocation.Completed(result);

        assertEquals(result, invocation.result());
    }

    @Test
    void submitted_storesSubmission() {
        var submission = new Scanner.Submission("job-123");
        var invocation = new Scanner.Invocation.Submitted(submission);

        assertEquals(submission, invocation.submission());
    }

    @Test
    void completed_withThreats() {
        var threat = new Scanner.Threat("virus", "Malware detected", "HIGH");
        var result = Scanner.Result.withThreats(List.of(threat));
        var invocation = new Scanner.Invocation.Completed(result);

        assertFalse(invocation.result().isClean());
        assertEquals(1, invocation.result().getThreats().size());
        assertEquals("virus", invocation.result().getThreats().getFirst().getName());
    }
}
