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

import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { ExtensionDetail, ExtensionHeaderInfo } from '../../../../src/pages/extension-detail/extension-detail';
import { ExtensionDetailRoutes } from '../../../../src/pages/extension-detail/extension-detail-routes';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Extension } from '../../../../src/extension-registry-types';
import { PageSettings } from '../../../../src/page-settings';
import { renderWithProviders } from '../../support/test-providers';

const TRUSTED_PUBLISHING_TITLE = 'Published via trusted publishing';

const extension = (overrides: Partial<Extension> = {}): Extension =>
    ({
        name: 'bar',
        namespace: 'foo',
        namespaceDisplayName: 'Foo',
        version: '1.0.0',
        displayName: 'Bar Tools',
        files: {},
        downloadCount: 0,
        reviewCount: 0,
        deprecated: false,
        verified: true,
        publishedBy: { loginName: 'test_user', homepage: 'https://example.com/test_user' },
        ...overrides
    }) as unknown as Extension;

const renderHeaderInfo = (overrides: Partial<Extension> = {}) =>
    renderWithProviders(<ExtensionHeaderInfo extension={extension(overrides)} headerTextColor='#fff' />, {
        mainContext: {
            pageSettings: {
                elements: {},
                urls: {
                    namespaceAccessInfo: 'https://example.com/namespace-access',
                    trustedPublishing: 'https://example.com/trusted-publishing'
                }
            } as PageSettings
        }
    });

describe('ExtensionHeaderInfo', () => {
    it('marks a version published through trusted publishing', () => {
        renderHeaderInfo({ publishedWithTrustedPublishing: true });

        expect(screen.getByLabelText(TRUSTED_PUBLISHING_TITLE)).toBeInTheDocument();
    });

    it('leaves a version published with an access token unmarked', () => {
        renderHeaderInfo({ publishedWithTrustedPublishing: false });

        expect(screen.queryByLabelText(TRUSTED_PUBLISHING_TITLE)).not.toBeInTheDocument();
    });

    it('leaves the mark off when the registry does not report how a version was published', () => {
        renderHeaderInfo();

        expect(screen.queryByLabelText(TRUSTED_PUBLISHING_TITLE)).not.toBeInTheDocument();
    });
});

describe('ExtensionDetail', () => {
    const renderFailingDetail = (failure: object) => {
        const getExtensionDetail = vi.fn().mockRejectedValue(failure);
        const handleError = vi.fn();
        renderWithProviders(
            <Routes>
                <Route path={ExtensionDetailRoutes.MAIN} element={<ExtensionDetail />} />
            </Routes>,
            {
                route: '/extension/foo/bar',
                mainContext: {
                    service: {
                        getExtensionDetail,
                        getExtensionApiUrl: () => 'https://open-vsx.org/api/foo/bar'
                    } as unknown as ExtensionRegistryService,
                    handleError
                }
            }
        );
        return { getExtensionDetail, handleError };
    };

    it('shows a missing extension as not found without reporting an error', async () => {
        const { getExtensionDetail, handleError } = renderFailingDetail({ error: 'Not found', status: 404 });

        expect(await screen.findByText('Extension Not Found: foo.bar')).toBeInTheDocument();
        expect(getExtensionDetail).toHaveBeenCalledWith(expect.any(AbortSignal), 'https://open-vsx.org/api/foo/bar');
        expect(handleError).not.toHaveBeenCalled();
    });

    it('reports any other failure to load the extension', async () => {
        const failure = { error: 'Internal Server Error', message: 'boom', status: 500 };
        const { handleError } = renderFailingDetail(failure);

        expect(await screen.findByText('Failed to load extension details')).toBeInTheDocument();
        await waitFor(() =>
            expect(handleError).toHaveBeenCalledWith(failure, { title: 'Couldn’t load the extension' })
        );
    });
});
