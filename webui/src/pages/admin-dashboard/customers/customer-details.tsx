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

import { FC, useState } from 'react';
import { Box, Typography, Button, Alert, LinearProgress } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import { useParams } from 'react-router';
import type { Customer } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { isNotFound } from '../../../server-request';
import { useAdminUsageStats } from '../usage-stats/use-usage-stats';
import { GeneralDetails, UsageStats } from '../../../components/rate-limiting/customer';
import { CustomerFormDialog } from './customer-form-dialog';
import { CustomerMemberList } from './customer-member-list';
import { CustomerTokenList } from './customer-token-list';
import { useCustomer, useUpdateCustomer } from './use-customers';

const CustomerDetailsLoading: FC = () => (
    <Box sx={{ p: 3 }}>
        <LinearProgress color='secondary' sx={{ width: '100%' }} />
    </Box>
);

const CustomerDetailsError: FC<{ message: string }> = ({ message }) => (
    <Box sx={{ p: 3 }}>
        <Alert severity='error'>{message}</Alert>
    </Box>
);

export const CustomerDetails: FC = () => {
    const { customer: customerName } = useParams<{ customer: string }>();
    const [formDialogOpen, setFormDialogOpen] = useState(false);

    const { data: customer, isLoading: loading, error: customerError } = useCustomer(customerName);
    const { mutateAsync: updateCustomer } = useUpdateCustomer();

    const { usageStats, dailyP95, error: statsError, startDate, setStartDate } = useAdminUsageStats(customerName);

    const error = customerError
        ? isNotFound(customerError)
            ? `Customer "${customerName}" not found.`
            : handleError(customerError as Error)
        : null;

    const handleFormSubmit = async (updatedCustomer: Customer) => {
        if (customer) {
            await updateCustomer({ name: customer.name, customer: updatedCustomer });
        }
    };

    if (loading) {
        return <CustomerDetailsLoading />;
    }

    if (error || statsError) {
        return <CustomerDetailsError message={error ?? statsError ?? ''} />;
    }

    if (!customer) {
        return null;
    }

    return (
        <>
            <Box sx={{ p: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                    <Typography variant='h4' component='h1'>
                        {customer.name}
                    </Typography>
                </Box>

                <GeneralDetails
                    customer={customer}
                    headerAction={
                        <Button size='small' startIcon={<EditIcon />} onClick={() => setFormDialogOpen(true)}>
                            Edit
                        </Button>
                    }
                />
                <CustomerMemberList customer={customer} />
                <CustomerTokenList customer={customer} />
                <UsageStats
                    usageStats={usageStats}
                    dailyP95={dailyP95}
                    customer={customer}
                    startDate={startDate}
                    onStartDateChange={setStartDate}
                />
            </Box>

            <CustomerFormDialog
                open={formDialogOpen}
                customer={customer}
                onClose={() => setFormDialogOpen(false)}
                onSubmit={handleFormSubmit}
            />
        </>
    );
};
