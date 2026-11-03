/********************************************************************************
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
 ********************************************************************************/

import { describe, expect, it } from 'vitest';
import { needsQuarantineReview } from '../../../../src/hooks/scan-admin';
import { ScanResult } from '../../../../src/context/scan-admin';

function scan(overrides: Partial<ScanResult> = {}): ScanResult {
    return { adminDecision: null, threats: [], validationFailures: [], ...overrides } as unknown as ScanResult;
}

const enforced = { enforcedFlag: true } as ScanResult['threats'][number];
const unenforced = { enforcedFlag: false } as ScanResult['threats'][number];
const decided = { decision: 'ALLOWED', decidedBy: 'admin', dateDecided: '2026-01-01' };

describe('needsQuarantineReview', () => {
    it('holds a scan for review on an enforced threat or an enforced validation failure', () => {
        expect(needsQuarantineReview(scan({ threats: [enforced] }))).toBe(true);
        expect(needsQuarantineReview(scan({ validationFailures: [enforced as never] }))).toBe(true);
    });

    it('does not hold a scan with only unenforced findings, or one already decided', () => {
        expect(needsQuarantineReview(scan({ threats: [unenforced], validationFailures: [unenforced as never] }))).toBe(
            false
        );
        expect(needsQuarantineReview(scan({ validationFailures: [enforced as never], adminDecision: decided }))).toBe(
            false
        );
    });
});
