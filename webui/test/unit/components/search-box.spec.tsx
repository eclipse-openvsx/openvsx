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
import { renderInEntryShell } from '../support/test-providers';
import { SearchBox } from '../../../src/components/search-box';

const searchField = () => screen.getByLabelText('Search extensions');

describe('SearchBox', () => {
    it('submits the trimmed query', async () => {
        const onSearch = vi.fn();
        renderInEntryShell(<SearchBox defaultValue='  react ' onSearch={onSearch} />);

        await userEvent.click(screen.getByLabelText('Search'));

        expect(onSearch).toHaveBeenCalledWith('react');
    });

    it('ignores a blank query', async () => {
        const onSearch = vi.fn();
        renderInEntryShell(<SearchBox onSearch={onSearch} />);

        await userEvent.type(searchField(), '   {Enter}');

        expect(onSearch).not.toHaveBeenCalled();
    });
});
