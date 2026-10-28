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
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../support/test-providers';
import { PublisherReinstateAgreementButton } from '../../../../src/pages/admin-dashboard/publisher-reinstate-agreement-button';
import { UpdateContext } from '../../../../src/pages/admin-dashboard/publisher-admin';
import { ExtensionRegistryService, AdminService } from '../../../../src/extension-registry-service';
import { PublisherInfo } from '../../../../src/extension-registry-types';

const publisherInfo: PublisherInfo = {
    user: { loginName: 'octocat', tokensUrl: '', createTokenUrl: '', provider: 'github' },
    extensions: [],
    activeAccessTokenNum: 0
};

function serviceWith(reinstatePublisherAgreement: AdminService['reinstatePublisherAgreement']) {
    return {
        serverUrl: 'https://open-vsx.org',
        admin: { reinstatePublisherAgreement } as unknown as AdminService
    } as ExtensionRegistryService;
}

describe('PublisherReinstateAgreementButton', () => {
    it('reinstates the publisher and refreshes the page', async () => {
        const reinstate = vi.fn().mockResolvedValue({ success: 'ok' });
        const handleUpdate = vi.fn();
        renderWithProviders(
            <UpdateContext.Provider value={{ handleUpdate, handleUserDeleted: () => {} }}>
                <PublisherReinstateAgreementButton publisherInfo={publisherInfo} />
            </UpdateContext.Provider>,
            { mainContext: { service: serviceWith(reinstate) } }
        );

        await userEvent.click(screen.getByRole('button', { name: 'Reinstate' }));

        expect(reinstate).toHaveBeenCalledWith('github', 'octocat');
        await waitFor(() => expect(handleUpdate).toHaveBeenCalled());
    });

    it('reports a failed request and does not refresh the page', async () => {
        const reinstate = vi.fn().mockRejectedValue({ error: 'nope' });
        const handleError = vi.fn();
        const handleUpdate = vi.fn();
        renderWithProviders(
            <UpdateContext.Provider value={{ handleUpdate, handleUserDeleted: () => {} }}>
                <PublisherReinstateAgreementButton publisherInfo={publisherInfo} />
            </UpdateContext.Provider>,
            { mainContext: { service: serviceWith(reinstate), handleError } }
        );

        await userEvent.click(screen.getByRole('button', { name: 'Reinstate' }));

        await waitFor(() =>
            expect(handleError).toHaveBeenCalledWith(
                { error: 'nope' },
                { title: 'Couldn’t reinstate the publisher agreement' }
            )
        );
        expect(handleUpdate).not.toHaveBeenCalled();
    });
});
