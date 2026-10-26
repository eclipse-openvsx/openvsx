/******************************************************************************
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
import { ErrorDialog } from '../../../src/components/error-dialog';
import { PageSettings } from '../../../src/page-settings';
import { renderWithProviders } from '../support/test-providers';

const open = (errorCode?: string, settings: Partial<PageSettings> = {}) =>
    renderWithProviders(
        <ErrorDialog
            errorMessage='Something failed.'
            errorCode={errorCode}
            isErrorDialogOpen
            handleCloseDialog={vi.fn()}
        />,
        {
            mainContext: {
                pageSettings: {
                    elements: {},
                    urls: { extensionDefaultIcon: '', namespaceAccessInfo: '' },
                    ...settings
                } as PageSettings
            }
        }
    );

describe('ErrorDialog', () => {
    it('shows the error message', () => {
        open();

        expect(screen.getByText('Something failed.')).toBeInTheDocument();
    });

    it('points a failed publisher agreement to the contact of the agreement', () => {
        open('publisher-agreement-problem', { publisherAgreement: { email: 'agreements@example.org' } });

        expect(screen.getByRole('link', { name: 'agreements@example.org' })).toHaveAttribute(
            'href',
            expect.stringMatching(/^mailto:agreements@example\.org\?subject=/)
        );
    });

    it('points a failed publisher agreement to the administrators when no contact is set', () => {
        open('publisher-agreement-problem');

        expect(screen.getByText(/contact the administrators of this registry/i)).toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('adds nothing to the message of a code it has no help for', () => {
        open('some-login-code');

        expect(screen.getByText('Something failed.')).toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('shows the help the deployment provides for the code', () => {
        const ErrorHelp = ({ errorCode, errorMessage }: { errorCode?: number | string; errorMessage: string }) => (
            <a href='https://help.example.org/login'>
                help for {errorCode} / {errorMessage}
            </a>
        );

        open('some-login-code', { elements: { errorHelp: ErrorHelp } });

        expect(screen.getByRole('link', { name: 'help for some-login-code / Something failed.' })).toHaveAttribute(
            'href',
            'https://help.example.org/login'
        );
    });
});
