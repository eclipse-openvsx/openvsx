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

import type {
    NameSquattingCounts,
    NameSquattingFinding,
    NameSquattingFlag
} from '../../../src/extension-registry-types';

export function finding(overrides: Partial<NameSquattingFinding> = {}): NameSquattingFinding {
    return {
        id: '1',
        scanId: '10',
        version: '1.0.0',
        targetPlatform: 'universal',
        scanStatus: 'PASSED',
        ruleName: 'Levenshtein Distance',
        reason: 'Too similar to squatter-target.theme',
        dateDetected: '2026-02-11T14:00Z',
        enforcedFlag: false,
        ...overrides
    };
}

export function flag(overrides: Partial<NameSquattingFlag> = {}): NameSquattingFlag {
    return {
        namespace: 'squatter',
        extensionName: 'squatty-theme',
        displayName: 'Squatty Theme',
        publisher: 'squatter-user',
        state: 'PUBLISHED',
        activeVersionCount: 2,
        findingCount: 1,
        dateFirstDetected: '2026-02-11T14:00Z',
        dateLastDetected: '2026-02-11T14:00Z',
        findings: [finding()],
        ...overrides
    };
}

export function counts(overrides: Partial<NameSquattingCounts> = {}): NameSquattingCounts {
    return { total: 1, published: 1, deactivated: 0, rejected: 0, ...overrides };
}
