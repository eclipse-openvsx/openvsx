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

import { describe, expect, it, vi } from 'vitest';
import { act } from '@testing-library/react';
import { renderHookWithProviders } from '../../../support/test-providers';
import { useSignPublisherAgreement } from '../../../../../src/pages/user/profile/use-publisher-agreement';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';

function renderSign(signPublisherAgreement: ExtensionRegistryService['signPublisherAgreement']) {
    const updateUser = vi.fn();
    const service = { signPublisherAgreement } as unknown as ExtensionRegistryService;
    const { result } = renderHookWithProviders(() => useSignPublisherAgreement(), {
        mainContext: { service, updateUser }
    });
    return { result, updateUser };
}

describe('useSignPublisherAgreement', () => {
    it('refreshes the user once the agreement is signed', async () => {
        const { result, updateUser } = renderSign(vi.fn().mockResolvedValue({ loginName: 'test_user' }));

        await act(() => result.current.mutateAsync());

        expect(updateUser).toHaveBeenCalledTimes(1);
    });
});
