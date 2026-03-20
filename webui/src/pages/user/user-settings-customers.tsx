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

import { FunctionComponent, ReactNode, useContext, useEffect, useState, useRef } from 'react';
import { Box, Typography, Tabs, Tab, useTheme, useMediaQuery } from '@mui/material';
import { Customer } from '../../extension-registry-types';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { MainContext } from '../../context';
import { UserSettingsCustomerDetail } from './user-settings-customer-detail';

interface CustomerTabsProps {
    chosenCustomer: Customer;
    onChange: (value: Customer) => void;
    customers: Customer[];
}

const CustomersTabs = (props: CustomerTabsProps) => {
    const theme = useTheme();
    const isATablet = useMediaQuery(theme.breakpoints.down('md'));
    return (
        <Tabs
            orientation={isATablet ? 'horizontal' : 'vertical'}
            value={props.chosenCustomer}
            onChange={(event, value) => props.onChange(value)}
            variant={isATablet ? 'scrollable' : 'standard'}
            scrollButtons={isATablet ? 'auto' : false}
            indicatorColor='secondary'
            sx={{ width: { xs: '80%', sm: '80%', md: '80%', lg: '160px', xl: '160px' } }}
        >
            {props.customers.map(customer => (
                <Tab
                    sx={{
                        root: { minHeight: '24px' },
                        wrapper: { textTransform: 'none' }
                    }}
                    key={'cust-' + customer.name}
                    value={customer}
                    label={customer.name}
                />
            ))}
        </Tabs>
    );
};

export const UserSettingsCustomers: FunctionComponent = () => {
    const [loading, setLoading] = useState(true);
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [chosenCustomer, setChosenCustomer] = useState<Customer>();
    const { service, handleError } = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());

    useEffect(() => {
        loadCustomers();
        return () => {
            abortController.current.abort();
        };
    }, []);

    const loadCustomers = async (): Promise<void> => {
        try {
            // TODO: Replace with user-scoped endpoint when backend is ready
            const data = await service.getCustomersForUser(abortController.current);
            const chosen = data.length ? data[0] : undefined;
            setCustomers(data);
            setChosenCustomer(chosen);
            setLoading(false);
        } catch (err) {
            handleError(err);
            setLoading(false);
        }
    };

    let customerContainer: ReactNode = null;
    if (customers.length > 0 && chosenCustomer) {
        customerContainer = (
            <Box
                sx={{
                    display: 'flex',
                    width: '100%',
                    flexDirection: { xs: 'column', sm: 'column', md: 'column', lg: 'row', xl: 'row' },
                    alignItems: { xs: 'center', sm: 'center', md: 'center', lg: 'normal', xl: 'normal' }
                }}
            >
                <CustomersTabs
                    chosenCustomer={chosenCustomer}
                    customers={customers}
                    onChange={setChosenCustomer}
                />
                <UserSettingsCustomerDetail customer={chosenCustomer} />
            </Box>
        );
    } else if (!loading) {
        customerContainer = (
            <Typography variant='body1'>
                You are not a member of any rate limiting customer group.
            </Typography>
        );
    }

    return (
        <>
            <Box>
                <Typography variant='h5' gutterBottom>Rate Limiting</Typography>
            </Box>
            <Box mt={2}>
                <DelayedLoadIndicator loading={loading} />
                {customerContainer}
            </Box>
        </>
    );
};
