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
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ButtonWithProgress } from '../../../src/components/button-with-progress';

describe('ButtonWithProgress', () => {
    it('places the icon it is given inside the button', () => {
        render(
            <ButtonWithProgress working={false} onClick={vi.fn()} startIcon={<span data-testid='icon' />}>
                Clear all
            </ButtonWithProgress>
        );

        expect(screen.getByRole('button', { name: 'Clear all' })).toContainElement(screen.getByTestId('icon'));
    });

    it('shows the progress indicator and disables itself while working', () => {
        render(
            <ButtonWithProgress working onClick={vi.fn()}>
                Clear all
            </ButtonWithProgress>
        );

        expect(screen.getByRole('progressbar')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Clear all' })).toBeDisabled();
    });

    it('disables itself on error, so a failed action cannot be fired again', () => {
        render(
            <ButtonWithProgress working={false} error onClick={vi.fn()}>
                Clear all
            </ButtonWithProgress>
        );

        expect(screen.getByRole('button', { name: 'Clear all' })).toBeDisabled();
        expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    it('is a plain button when it is not working', async () => {
        const onClick = vi.fn();
        render(
            <ButtonWithProgress working={false} onClick={onClick}>
                Clear all
            </ButtonWithProgress>
        );

        expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Clear all' }));

        expect(onClick).toHaveBeenCalledOnce();
    });
});
