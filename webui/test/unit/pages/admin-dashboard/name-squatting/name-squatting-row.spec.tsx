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
import { NameSquattingRow } from '../../../../../src/pages/admin-dashboard/name-squatting/name-squatting-row';
import { flag } from '../../../support/name-squatting-data';

const clearButton = () => screen.queryByRole('button', { name: /mark as false positive/i });
const deleteButton = () => screen.queryByRole('button', { name: /soft delete extension/i });

describe('NameSquattingRow', () => {
    it('offers both moderation actions for a published extension', async () => {
        const onClear = vi.fn();
        const onDelete = vi.fn();
        renderWithProviders(<NameSquattingRow flag={flag()} onClear={onClear} onDelete={onDelete} />);

        expect(screen.getByText('Squatty Theme')).toBeInTheDocument();
        expect(screen.getByText(/squatter\.squatty-theme/)).toBeInTheDocument();

        await userEvent.click(clearButton()!);
        await userEvent.click(deleteButton()!);

        expect(onClear).toHaveBeenCalledWith(flag());
        expect(onDelete).toHaveBeenCalledWith(flag());
    });

    // A rejected extension never made it into the registry, so neither action has anything to act on.
    it('replaces the actions with an explanation when publication was blocked', () => {
        renderWithProviders(
            <NameSquattingRow
                flag={flag({ state: 'REJECTED', activeVersionCount: 0 })}
                onClear={vi.fn()}
                onDelete={vi.fn()}
            />
        );

        expect(screen.getByText(/publication was blocked by the check/i)).toBeInTheDocument();
        expect(clearButton()).not.toBeInTheDocument();
        expect(deleteButton()).not.toBeInTheDocument();
    });

    it('keeps the clear action but disables soft delete once no active versions are left', () => {
        renderWithProviders(
            <NameSquattingRow
                flag={flag({ state: 'DEACTIVATED', activeVersionCount: 0 })}
                onClear={vi.fn()}
                onDelete={vi.fn()}
            />
        );

        expect(clearButton()).toBeEnabled();
        expect(deleteButton()).toBeDisabled();
    });

    it('lists the individual findings once the row is expanded', async () => {
        renderWithProviders(<NameSquattingRow flag={flag()} onClear={vi.fn()} onDelete={vi.fn()} />);

        expect(screen.queryByText('Levenshtein Distance')).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /show findings for squatter\.squatty-theme/i }));

        expect(screen.getByText('Levenshtein Distance')).toBeInTheDocument();
        expect(screen.getByText(/too similar to squatter-target\.theme/i)).toBeInTheDocument();
        expect(screen.getByText('1.0.0')).toBeInTheDocument();
    });
});
