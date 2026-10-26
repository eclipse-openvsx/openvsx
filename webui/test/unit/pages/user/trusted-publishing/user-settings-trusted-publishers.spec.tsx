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
import { renderWithProviders } from '../../../support/test-providers';
import { UserSettingsTrustedPublishers } from '../../../../../src/pages/user/trusted-publishing/user-settings-trusted-publishers';
import { ExtensionRegistryService } from '../../../../../src/extension-registry-service';
import { PageSettings } from '../../../../../src/page-settings';
import { Namespace, TrustedPublisherList, TrustedPublisherStatus } from '../../../../../src/extension-registry-types';
import { enabledStatus, testUser, trustedPublisher, trustedPublisherList } from '../../../support/trusted-publishing';

const URL_ALPHA = '/user/namespace/alpha/trusted-publishing';
const URL_BETA = '/user/namespace/beta/trusted-publishing';
// the tab description reads pageSettings.urls (docs link), which the harness default lacks
const pageSettings = { urls: {} } as PageSettings;

const namespace = (name: string, trustedPublishingUrl?: string): Namespace => ({
    name,
    extensions: {},
    verified: true,
    membersUrl: '',
    roleUrl: '',
    detailsUrl: '',
    trustedPublishingUrl
});

function serviceStub(
    options: { status?: TrustedPublisherStatus; publishersByUrl?: Record<string, TrustedPublisherList> } = {}
) {
    const { status = enabledStatus, publishersByUrl = {} } = options;
    // beta before alpha and one namespace without TP access, to exercise sorting and filtering
    const getNamespaces = vi
        .fn()
        .mockResolvedValue([namespace('beta', URL_BETA), namespace('alpha', URL_ALPHA), namespace('gamma')]);
    const getTrustedPublishingStatus = vi.fn().mockResolvedValue(status);
    const getTrustedPublishers = vi
        .fn()
        .mockImplementation((_signal: AbortSignal, url: string) =>
            Promise.resolve(publishersByUrl[url] ?? trustedPublisherList())
        );
    const service = {
        getNamespaces,
        getTrustedPublishingStatus,
        getTrustedPublishers
    } as unknown as ExtensionRegistryService;
    return { getNamespaces, getTrustedPublishingStatus, getTrustedPublishers, service };
}

describe('UserSettingsTrustedPublishers', () => {
    it('aggregates the publishers of all manageable namespaces, sorted by namespace and extension', async () => {
        const { service } = serviceStub({
            publishersByUrl: {
                [URL_BETA]: trustedPublisherList({
                    trustedPublishers: [trustedPublisher({ id: 2, namespace: 'beta', extension: 'one' })]
                }),
                [URL_ALPHA]: trustedPublisherList({
                    trustedPublishers: [trustedPublisher({ id: 1, namespace: 'alpha', extension: 'one' })]
                })
            }
        });
        renderWithProviders(<UserSettingsTrustedPublishers />, {
            mainContext: { service, user: testUser, pageSettings }
        });

        await screen.findByText('GitHub Actions · alpha/one');
        const labels = screen.getAllByText(/GitHub Actions · /).map(element => element.textContent);
        expect(labels).toEqual(['GitHub Actions · alpha/one', 'GitHub Actions · beta/one']);
    });

    it('hints at joining a namespace and hides registration when there are no providers', async () => {
        const { getTrustedPublishers, service } = serviceStub({ status: { enabled: true, allowed: false } });
        renderWithProviders(<UserSettingsTrustedPublishers />, {
            mainContext: { service, user: testUser, pageSettings }
        });

        expect(await screen.findByText(/create or join a namespace first/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Add a trusted publisher/ })).not.toBeInTheDocument();
        expect(getTrustedPublishers).not.toHaveBeenCalled();
    });

    it('offers registration when namespaces are manageable but nothing is registered yet', async () => {
        const { service } = serviceStub({
            publishersByUrl: { [URL_ALPHA]: trustedPublisherList({ registrableExtensions: ['one'] }) }
        });
        renderWithProviders(<UserSettingsTrustedPublishers />, {
            mainContext: { service, user: testUser, pageSettings }
        });

        expect(await screen.findByText('No trusted publishers registered yet.')).toBeInTheDocument();
        expect(await screen.findByRole('button', { name: /Add a trusted publisher/ })).toBeInTheDocument();
    });

    // an extension takes at most one trusted publisher, so a fully registered account has nothing to add
    it('hides registration and explains when every active extension is already registered', async () => {
        const { service } = serviceStub({
            publishersByUrl: {
                [URL_ALPHA]: trustedPublisherList({
                    trustedPublishers: [trustedPublisher({ id: 1, namespace: 'alpha', extension: 'one' })]
                })
            }
        });
        renderWithProviders(<UserSettingsTrustedPublishers />, {
            mainContext: { service, user: testUser, pageSettings }
        });

        expect(await screen.findByText(/already has a trusted publisher/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Add a trusted publisher/ })).not.toBeInTheDocument();
    });
});
