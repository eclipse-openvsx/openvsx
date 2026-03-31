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

import { FC, useContext, useState, useEffect, useRef, useCallback } from "react";
import {
    Box,
    Typography,
    Button,
    Alert,
    LinearProgress
} from "@mui/material";
import EditIcon from '@mui/icons-material/Edit';
import { useParams } from 'react-router-dom';
import { MainContext } from '../../../context';
import type { Customer } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { useAdminUsageStats } from '../usage-stats/use-usage-stats';
import { GeneralDetails, UsageStats } from '../../../components/rate-limiting/customer';
import { CustomerFormDialog } from './customer-form-dialog';
import { CustomerMemberList } from './customer-member-list';
import { CustomerTokenList } from './customer-token-list';

const CustomerDetailsLoading: FC = () => (
    <Box sx={{ p: 3 }}>
        <LinearProgress color='secondary' sx={{ width: "100%" }} />
    </Box>
);

const CustomerDetailsError: FC<{ message: string }> = ({ message }) => (
    <Box sx={{ p: 3 }}>
        <Alert severity='error'>{message}</Alert>
    </Box>
);

export const CustomerDetails: FC = () => {
    const { customer: customerName } = useParams<{ customer: string }>();
    const abortController = useRef(new AbortController());
    const { service } = useContext(MainContext);

    const [customer, setCustomer] = useState<Customer | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [formDialogOpen, setFormDialogOpen] = useState(false);

    const { usageStats, error: statsError, startDate, setStartDate } = useAdminUsageStats(customerName);

    const loadCustomer = useCallback(async () => {
        if (!customerName) return;
        try {
            setLoading(true);
            setError(null);
            const data = await service.admin.getCustomer(abortController.current, customerName);
            setCustomer(data);
        } catch (err) {
            if ((err as { status?: number })?.status === 404) {
                setError(`Customer "${customerName}" not found.`);
            } else {
                setError(handleError(err as Error));
            }
        } finally {
            setLoading(false);
        }
    }, [service, customerName]);

    useEffect(() => {
        loadCustomer();
        return () => abortController.current.abort();
    }, [loadCustomer]);

    const handleFormSubmit = async (updatedCustomer: Customer) => {
        if (customer) {
            await service.admin.updateCustomer(abortController.current, customer.name, updatedCustomer);
            await loadCustomer();
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
            <CustomerMemberList
                customer={customer}
            />
            <CustomerTokenList
                customer={customer}
            />
            <UsageStats usageStats={usageStats} customer={customer} startDate={startDate} onStartDateChange={setStartDate} />
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
