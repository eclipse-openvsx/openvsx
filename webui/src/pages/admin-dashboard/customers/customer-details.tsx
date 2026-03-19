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
    Paper,
    type PaperProps,
    type SxProps,
    type Theme,
    Chip,
    Stack,
    Alert,
    Button,
    Divider,
    Avatar,
    IconButton,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    Grid,
    LinearProgress
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import EditIcon from "@mui/icons-material/Edit";
import PersonAddIcon from "@mui/icons-material/PersonAdd";
import DeleteIcon from "@mui/icons-material/Delete";
import { useParams, useNavigate, Link as RouterLink } from "react-router-dom";
import { MainContext } from "../../../context";
import type { Customer, UserData, UsageStats } from "../../../extension-registry-types";
import type { DateTime } from "luxon";
import { handleError } from "../../../utils";
import { AdminDashboardRoutes } from "../admin-dashboard";
import { UsageStatsChart } from "../usage-stats/usage-stats-chart";
import { useUsageStats } from "../usage-stats/use-usage-stats";
import { CustomerFormDialog } from "./customer-form-dialog";
import { AddUserDialog } from "../../user/add-user-dialog";

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

const BackToCustomersButton: FC<{ sx?: SxProps<Theme> }> = ({ sx }) => {
    const navigate = useNavigate();
    return (
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(AdminDashboardRoutes.CUSTOMERS)} sx={sx}>
            Back to Customers
        </Button>
    );
};

const CustomerDetailsLoading: FC = () => (
    <Box sx={{ p: 3 }}>
        <BackToCustomersButton sx={{ mb: 2 }} />
        <LinearProgress color='secondary' sx={{ width: "100%" }} />
    </Box>
);

const CustomerDetailsError: FC<{ message: string }> = ({ message }) => (
    <Box sx={{ p: 3 }}>
        <BackToCustomersButton sx={{ mb: 2 }} />
        <Alert severity='error'>{message}</Alert>
    </Box>
);

const GeneralInformationSection: FC<{ customer: Customer; onEdit: () => void }> = ({ customer, onEdit }) => {
    const tier = customer.tier;
    return (
        <Paper {...sectionPaperProps}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                <Typography variant='h6'>General Information</Typography>
                <Button
                    size='small'
                    startIcon={<EditIcon />}
                    onClick={onEdit}
                    sx={{ ml: 'auto' }}
                >
                    Edit
                </Button>
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={2}>
                <Grid item xs={12} sm={6} md={4}>
                    <Typography variant='subtitle2' color='text.secondary'>Name</Typography>
                    <Typography variant='body1'>{customer.name}</Typography>
                </Grid>
                <Grid item xs={12} sm={6} md={4}>
                    <Typography variant='subtitle2' color='text.secondary'>State</Typography>
                    <Box sx={{ mt: 0.5 }}>
                        <Chip
                            label={customer.state}
                            size='small'
                            color='secondary'
                        />
                    </Box>
                </Grid>
                {tier ? (
                    <>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Tier</Typography>
                            <Box sx={{ mt: 0.5 }}>
                                <Chip label={tier.name} size='small' />
                            </Box>
                        </Grid>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Tier Type</Typography>
                            <Typography variant='body2'>{tier.tierType}</Typography>
                        </Grid>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Capacity</Typography>
                            <Typography variant='body2'>{tier.capacity} requests / {tier.duration}s</Typography>
                        </Grid>
                        <Grid item xs={12} sm={6} md={4}>
                            <Typography variant='subtitle2' color='text.secondary'>Refill Strategy</Typography>
                            <Typography variant='body2'>{tier.refillStrategy}</Typography>
                        </Grid>
                        {tier.description && (
                            <Grid item xs={12}>
                                <Typography variant='subtitle2' color='text.secondary'>Tier Description</Typography>
                                <Typography variant='body2'>{tier.description}</Typography>
                            </Grid>
                        )}
                    </>
                ) : (
                    <Grid item xs={12} sm={6} md={4}>
                        <Typography variant='subtitle2' color='text.secondary'>Tier</Typography>
                        <Typography variant='body2' color='text.secondary'>No tier assigned</Typography>
                    </Grid>
                )}
                <Grid item xs={12}>
                    <Typography variant='subtitle2' color='text.secondary'>CIDR Blocks</Typography>
                    {customer.cidrBlocks.length > 0 ? (
                        <Stack direction='row' spacing={0.5} sx={{ mt: 0.5 }} flexWrap='wrap' useFlexGap>
                            {customer.cidrBlocks.map((cidr) => (
                                <Chip key={cidr} label={cidr} size='small' variant='outlined' />
                            ))}
                        </Stack>
                    ) : (
                        <Typography variant='body2' color='text.secondary'>None configured</Typography>
                    )}
                </Grid>
            </Grid>
        </Paper>
    );
};

interface MembersSectionProps {
    users: UserData[];
    onAddUser: () => void;
    onRemoveUser: (user: UserData) => void;
}

const MembersSection: FC<MembersSectionProps> = ({ users, onAddUser, onRemoveUser }) => (
    <Paper {...sectionPaperProps}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
            <Typography variant='h6'>Members</Typography>
            <Button
                size='small'
                startIcon={<PersonAddIcon />}
                onClick={onAddUser}
                sx={{ ml: 'auto' }}
            >
                Add Member
            </Button>
        </Box>
        <Divider sx={{ mb: 1 }} />
        {users.length === 0 ? (
            <Typography variant='body2' color='text.secondary' sx={{ py: 1 }}>
                No members assigned to this customer.
            </Typography>
        ) : (
            <List dense disablePadding>
                {users.map(user => (
                    <ListItem
                        key={`${user.loginName}-${user.provider}`}
                        secondaryAction={
                            <IconButton
                                edge='end'
                                size='small'
                                color='error'
                                onClick={() => onRemoveUser(user)}
                                title='Remove member'
                            >
                                <DeleteIcon fontSize='small' />
                            </IconButton>
                        }
                    >
                        <ListItemAvatar>
                            <Avatar src={user.avatarUrl} sx={{ width: 32, height: 32 }} />
                        </ListItemAvatar>
                        <ListItemText
                            primary={
                                <RouterLink style={{ color: 'inherit' }} to={`${AdminDashboardRoutes.PUBLISHER_ADMIN}/${user.loginName}`}>
                                    {user.loginName}
                                </RouterLink>
                            }
                            secondary={user.fullName}
                        />
                    </ListItem>
                ))}
            </List>
        )}
    </Paper>
);

interface UsageStatsSectionProps {
    usageStats: readonly UsageStats[];
    customer: Customer;
    startDate: DateTime;
    onStartDateChange: (date: DateTime) => void;
}

const UsageStatsSection: FC<UsageStatsSectionProps> = ({ usageStats, customer, startDate, onStartDateChange }) => (
    <Paper {...sectionPaperProps}>
        <Typography variant='h6' gutterBottom>
            Usage Statistics
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <UsageStatsChart
            usageStats={usageStats}
            customer={customer}
            startDate={startDate}
            onStartDateChange={onStartDateChange}
            embedded
        />
    </Paper>
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

    const { usageStats, error: statsError, startDate, setStartDate } = useUsageStats(customerName);

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
        <Box sx={{ p: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                <BackToCustomersButton sx={{ mr: 2 }} />
                <Typography variant='h4' component='h1'>
                    {customer.name}
                </Typography>
            </Box>

            <GeneralInformationSection customer={customer} onEdit={() => setFormDialogOpen(true)} />
            <MembersSection users={users} onAddUser={() => setAddUserDialogOpen(true)} onRemoveUser={handleRemoveUser} />
            <UsageStatsSection usageStats={usageStats} customer={customer} startDate={startDate} onStartDateChange={setStartDate} />
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
