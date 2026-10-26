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
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../../support/test-providers';
import { LogoutForm } from '../../../../src/pages/user/logout';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { CsrfTokenJson } from '../../../../src/extension-registry-types';

function renderForm(csrfToken: CsrfTokenJson) {
    const getCsrfToken = vi.fn().mockResolvedValue(csrfToken);
    const service = { getCsrfToken, getLogoutUrl: () => '/logout' } as unknown as ExtensionRegistryService;
    renderWithProviders(<LogoutForm />, { mainContext: { service } });
}

describe('LogoutForm', () => {
    it("posts the session's CSRF token", async () => {
        renderForm({ value: 'token', header: 'X-CSRF-TOKEN' });

        expect(await screen.findByDisplayValue('token')).toHaveAttribute('name', '_csrf');
    });
});
