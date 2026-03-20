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
    IconButton,
    Alert,
    LinearProgress
} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import PersonAddIcon from "@mui/icons-material/PersonAdd";
import DeleteIcon from "@mui/icons-material/Delete";
import { useParams, Link as RouterLink } from "react-router-dom";
import { MainContext } from "../../../context";
import type { Customer, UserData } from "../../../extension-registry-types";
import { createRoute, handleError } from "../../../utils";
import { AdminDashboardRoutes } from "../admin-routes";
import { useAdminUsageStats } from "../usage-stats/use-usage-stats";
import { GeneralDetails, Members, UsageStats } from "../../../components/rate-limiting/customer";
import { CustomerFormDialog } from "./customer-form-dialog";
import { AddUserDialog } from "../../user/add-user-dialog";

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
    const [addUserDialogOpen, setAddUserDialogOpen] = useState(false);

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

    const users = customer?.users ?? [];

    // TODO: Replace with real API calls when backend is ready
    const handleAddUser = (user: UserData) => {
    };

    const handleRemoveUser = (user: UserData) => {
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
            <Members
                users={users}
                headerAction={
                    <Button size='small' startIcon={<PersonAddIcon />} onClick={() => setAddUserDialogOpen(true)}>
                        Add Member
                    </Button>
                }
                renderUserAction={(user) => (
                    <IconButton edge='end' size='small' color='error' onClick={() => handleRemoveUser(user)} title='Remove member'>
                        <DeleteIcon fontSize='small' />
                    </IconButton>
                )}
                renderUserPrimary={(user) => (
                    <RouterLink style={{ color: 'inherit' }} to={createRoute([AdminDashboardRoutes.PUBLISHER_ADMIN, user.loginName])}>
                        {user.loginName}
                    </RouterLink>
                )}
            />
            <UsageStats usageStats={usageStats} customer={customer} startDate={startDate} onStartDateChange={setStartDate} />
        </Box>

        <CustomerFormDialog
            open={formDialogOpen}
            customer={customer}
            onClose={() => setFormDialogOpen(false)}
            onSubmit={handleFormSubmit}
        />

        <AddUserDialog
            open={addUserDialogOpen}
            title='Add Member'
            description='Search for a user by login name to add them to this customer.'
            existingUsers={users}
            onClose={() => setAddUserDialogOpen(false)}
            onAddUser={handleAddUser}
        />
      </>
    );
};
