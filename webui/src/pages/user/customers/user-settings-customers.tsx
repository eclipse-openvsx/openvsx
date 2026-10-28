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
 *****************************************************************************/

import { FunctionComponent, useState } from 'react';
import { Box, ButtonBase } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import { DelayedLoadIndicator } from '../../../components/delayed-load-indicator';
import { accentHover, focusOutline, pillSurface } from '../../../components/page-primitives';
import { EmptyPlaceholder } from '../settings/settings-primitives';
import { SettingsHeader } from '../settings/settings-header';
import { UserSettingsCustomerDetail } from './user-settings-customer-detail';
import { useUserCustomers } from './use-user-customers';

// Same pill treatment as the sticky tab strips: quiet surface, accent-tinted when selected.
const CustomerPill = styled(ButtonBase, { shouldForwardProp: prop => prop !== 'active' })<{ active?: boolean }>(
    ({ theme, active }) => ({
        ...pillSurface(theme),
        backgroundColor: theme.palette.surface2,
        fontFamily: 'inherit',
        ...(active && {
            borderColor: alpha(theme.palette.secondary.main, 0.35),
            backgroundColor: theme.palette.accentSoft,
            color: theme.palette.secondary.light,
            fontWeight: 700
        }),
        ...focusOutline(theme),
        ...(active ? {} : accentHover(theme))
    })
);

export const UserSettingsCustomers: FunctionComponent = () => {
    const customersQuery = useUserCustomers();
    const [chosenName, setChosenName] = useState<string>();

    const customers = customersQuery.data ?? [];
    const loading = customersQuery.isLoading;
    const chosenCustomer = customers.find(customer => customer.name === chosenName) ?? customers[0];

    return (
        <Box>
            <SettingsHeader
                title='Rate Limiting'
                description='Usage statistics and rate limits for the customer groups you belong to.'
            />
            <DelayedLoadIndicator loading={loading} />
            {customers.length > 0 && chosenCustomer ? (
                <Box>
                    {customers.length > 1 ? (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', mb: '1.375rem' }}>
                            {customers.map(customer => (
                                <CustomerPill
                                    key={'cust-' + customer.name}
                                    active={customer === chosenCustomer}
                                    onClick={() => setChosenName(customer.name)}>
                                    {customer.name}
                                </CustomerPill>
                            ))}
                        </Box>
                    ) : null}
                    <UserSettingsCustomerDetail customer={chosenCustomer} />
                </Box>
            ) : !loading ? (
                <EmptyPlaceholder>You are not a member of any rate limiting customer group.</EmptyPlaceholder>
            ) : null}
        </Box>
    );
};
