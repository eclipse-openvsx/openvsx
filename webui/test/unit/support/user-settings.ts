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

import { vi } from 'vitest';
import { ExtensionRegistryService } from '../../../src/extension-registry-service';
import { Namespace, NamespaceDetails, TrustedPublisherStatus } from '../../../src/extension-registry-types';
import { enabledStatus, statusServiceStub } from './trusted-publishing';

/** A namespace carrying the capability URLs the settings views read. */
export const testNamespace = (overrides: Partial<Namespace> = {}): Namespace => ({
    name: 'foo',
    extensions: {},
    verified: true,
    membersUrl: '',
    roleUrl: '',
    detailsUrl: '',
    ...overrides
});

/** A namespace's public details, as the details form and the logo sidebar read them. */
export const namespaceDetails = (overrides: Partial<NamespaceDetails> = {}): NamespaceDetails => ({
    name: 'foo',
    displayName: 'Foo',
    socialLinks: {},
    ...overrides
});

export interface SettingsStubData {
    status?: TrustedPublisherStatus;
    namespaces?: Namespace[];
}

/**
 * Service stub answering every query the settings navigation makes: the trusted-publishing
 * status and the namespace list that gates the tabs and fills the sidebar.
 */
export function settingsServiceStub({ status = enabledStatus, namespaces = [] }: SettingsStubData = {}) {
    const { getTrustedPublishingStatus, service } = statusServiceStub(status);
    const getNamespaces = vi.fn().mockResolvedValue(namespaces);
    return {
        getTrustedPublishingStatus,
        getNamespaces,
        service: { ...service, getNamespaces } as unknown as ExtensionRegistryService
    };
}
