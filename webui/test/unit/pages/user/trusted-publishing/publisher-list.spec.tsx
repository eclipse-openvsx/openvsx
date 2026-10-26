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
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../../support/test-providers';
import { PublisherList } from '../../../../../src/pages/user/trusted-publishing/publisher-list';
import { gitHubProvider, trustedPublisher } from '../../../support/trusted-publishing';

const providers = [gitHubProvider];

describe('PublisherList', () => {
    it('shows the provider display name, falling back to the raw id', () => {
        const publishers = [trustedPublisher({ id: 1 }), trustedPublisher({ id: 2, provider: 'custom-ci' })];
        renderWithProviders(
            <PublisherList publishers={publishers} providers={providers} loading={false} onDelete={vi.fn()} />
        );

        expect(screen.getByText(/GitHub Actions/)).toBeInTheDocument();
        expect(screen.getByText(/custom-ci/)).toBeInTheDocument();
    });

    it('formats the registration as a path plus the remaining fields', () => {
        const publisher = trustedPublisher({
            registration: { environment: 'prod', workflow: 'release.yml', repo: 'vsx', owner: 'octo' }
        });
        renderWithProviders(
            <PublisherList publishers={[publisher]} providers={providers} loading={false} onDelete={vi.fn()} />
        );

        expect(screen.getByText('octo/vsx · workflow: release.yml · environment: prod')).toBeInTheDocument();
    });

    it('shows explicit provider IDs next to the path', () => {
        const publishers = [
            trustedPublisher({
                id: 1,
                registration: { owner: 'acme', repo: 'private', workflow: 'r.yml', repository_id: '42', owner_id: '7' }
            }),
            trustedPublisher({
                id: 2,
                provider: 'gitlab',
                registration: { namespace: 'acme', project: 'priv', workflow: 'ci', project_id: '5', namespace_id: '6' }
            })
        ];
        renderWithProviders(
            <PublisherList publishers={publishers} providers={providers} loading={false} onDelete={vi.fn()} />
        );

        expect(screen.getByText('acme/private (id 42, owner id 7) · workflow: r.yml')).toBeInTheDocument();
        expect(screen.getByText('acme/priv (id 5, owner id 6) · workflow: ci')).toBeInTheDocument();
    });

    it('labels rows according to rowDetail', () => {
        const publisher = trustedPublisher();
        const { rerender } = renderWithProviders(
            <PublisherList
                publishers={[publisher]}
                providers={providers}
                loading={false}
                rowDetail='namespace/extension'
                onDelete={vi.fn()}
            />
        );
        expect(screen.getByText('GitHub Actions · foo/bar')).toBeInTheDocument();

        rerender(
            <PublisherList
                publishers={[publisher]}
                providers={providers}
                loading={false}
                rowDetail='none'
                onDelete={vi.fn()}
            />
        );
        expect(screen.getByText('GitHub Actions')).toBeInTheDocument();
    });

    it('shows the empty text when there are no publishers', () => {
        renderWithProviders(
            <PublisherList
                publishers={[]}
                providers={providers}
                loading={false}
                emptyText='Nothing registered.'
                onDelete={vi.fn()}
            />
        );

        expect(screen.getByText('Nothing registered.')).toBeInTheDocument();
    });

    it('asks for confirmation before deleting', async () => {
        const onDelete = vi.fn();
        const publisher = trustedPublisher();
        const ue = userEvent.setup();
        renderWithProviders(
            <PublisherList publishers={[publisher]} providers={providers} loading={false} onDelete={onDelete} />
        );

        await ue.click(screen.getByRole('button', { name: 'Delete trusted publisher' }));
        expect(onDelete).not.toHaveBeenCalled();

        await ue.click(screen.getByRole('button', { name: 'Delete' }));
        expect(onDelete).toHaveBeenCalledWith(publisher);
    });
});
