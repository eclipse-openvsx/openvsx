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

import { FC, useContext, useState, useEffect, useRef, useMemo } from "react";
import { Box, Alert } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { MainContext } from "../../../context";
import type { Customer } from "../../../extension-registry-types";
import { handleError } from "../../../utils";
import { AdminDashboardRoutes } from "../admin-routes";
import { SearchListContainer } from "../search-list-container";
import { CustomerSearch } from "./usage-stats-search";
import { UsageStatsChart } from "./usage-stats-chart";
import { useUsageStats } from "./use-usage-stats";

export const UsageStatsView: FC = () => {
    const { customer } = useParams<{ customer: string }>();
    const navigate = useNavigate();
    const abortController = useRef(new AbortController());
    const { service, pageSettings } = useContext(MainContext);

    const [customers, setCustomers] = useState<Customer[]>([]);
    const [customersLoading, setCustomersLoading] = useState(true);
    const [customersError, setCustomersError] = useState<string | null>(null);

    const { usageStats, loading, error: statsError, startDate, setStartDate } = useUsageStats(customer);

    // Load customers for autocomplete
    useEffect(() => {
        const loadCustomers = async () => {
            try {
                setCustomersLoading(true);
                const data = await service.admin.getCustomers(abortController.current);
                setCustomers(data.customers);
            } catch (err) {
                setCustomersError(handleError(err as Error));
            } finally {
                setCustomersLoading(false);
            }
        };
        loadCustomers();
        return () => abortController.current.abort();
    }, [service]);

    const selectedCustomer = useMemo(
        () => customers.find(c => c.name === customer) || null,
        [customers, customer]
    );

    const handleCustomerChange = (_: unknown, value: Customer | null) => {
        if (value) {
            navigate(`${AdminDashboardRoutes.USAGE_STATS}/${value.name}`);
        } else {
            navigate(AdminDashboardRoutes.USAGE_STATS);
        }
    };

    const error = customersError || statsError;
    if (error) {
        return <Alert severity='error'>{error}</Alert>;
    }

    return (
        <Box>
            <SearchListContainer
                searchContainer={[
                    <CustomerSearch
                        key='customer-search'
                        customers={customers}
                        selectedCustomer={selectedCustomer}
                        loading={customersLoading}
                        onCustomerChange={handleCustomerChange}
                        pageSettings={pageSettings}
                    />
                ]}
                listContainer={!customer && <Alert severity='info'>Select a customer to view usage statistics.</Alert>}
                loading={loading || customersLoading}
            />
            {customer && (
              <UsageStatsChart
                usageStats={usageStats}
                customer={selectedCustomer}
                startDate={startDate}
                onStartDateChange={setStartDate}
            />)}
        </Box>
    );
};
