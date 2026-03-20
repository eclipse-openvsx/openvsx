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

import { FC, useContext, useState, useEffect, useRef, useCallback, useMemo } from "react";
import {
    Box,
    Button,
    Paper,
    Typography,
    CircularProgress,
    Alert,
    IconButton,
    Stack,
    Chip,
    Avatar,
    AvatarGroup
} from "@mui/material";
import { DataGrid, GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import VisibilityIcon from "@mui/icons-material/Visibility";
import { MainContext } from "../../../context";
import type { Customer } from "../../../extension-registry-types";
import { CustomerFormDialog } from "./customer-form-dialog";
import { DeleteCustomerDialog } from "./delete-customer-dialog";
import { createRoute, handleError } from "../../../utils";
import { createMultiSelectFilterOperators, createArrayContainsFilterOperators } from "../components";
import { AdminDashboardRoutes } from "../admin-routes";
import { Link } from "react-router-dom";

export const Customers: FC = () => {
    const abortController = useRef<AbortController>(new AbortController());
    const { service } = useContext(MainContext);
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [formDialogOpen, setFormDialogOpen] = useState(false);
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [selectedCustomer, setSelectedCustomer] = useState<Customer | undefined>();

    // Load all customers
    const loadCustomers = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await service.admin.getCustomers(abortController.current);
            setCustomers(data.customers);
        } catch (err: any) {
            setError(handleError(err));
        } finally {
            setLoading(false);
        }
    }, [service]);

    useEffect(() => {
        loadCustomers();
        return () => abortController.current.abort();
    }, []);

    const handleCreateClick = () => {
        setSelectedCustomer(undefined);
        setFormDialogOpen(true);
    };

    const handleEditClick = (customer: Customer) => {
        setSelectedCustomer(customer);
        setFormDialogOpen(true);
    };

    const handleDeleteClick = (customer: Customer) => {
        setSelectedCustomer(customer);
        setDeleteDialogOpen(true);
    };

    const handleFormSubmit = async (customer: Customer) => {
        if (selectedCustomer) {
            // update existing customer
            await service.admin.updateCustomer(abortController.current, selectedCustomer.name, customer);
        } else {
            // create new customer
            await service.admin.createCustomer(abortController.current, customer);
        }
        await loadCustomers();
    };

    const handleDeleteConfirm = async () => {
        if (selectedCustomer) {
            await service.admin.deleteCustomer(abortController.current, selectedCustomer.name);
            await loadCustomers();
        }
    };

    const handleFormDialogClose = () => {
        setFormDialogOpen(false);
        setSelectedCustomer(undefined);
    };

    const handleDeleteDialogClose = () => {
        setDeleteDialogOpen(false);
        setSelectedCustomer(undefined);
    };

  // Extract unique values for filter dropdowns
  const tierOptions = useMemo(() =>
    [...new Set(customers.map(c => c.tier?.name).filter(Boolean))] as string[],
    [customers]
  );
  const stateOptions = useMemo(() =>
    [...new Set(customers.map(c => c.state).filter(Boolean))],
    [customers]
  );
  const cidrBlockOptions = useMemo(() => {
    const allCidrs = customers.reduce<string[]>((acc, c) => acc.concat(c.cidrBlocks), []);
    return [...new Set(allCidrs)];
  }, [customers]);

  const columns: GridColDef[] = [
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 150 },
    {
      field: 'tier',
      headerName: 'Tier',
      flex: 1,
      minWidth: 120,
      valueGetter: (value: Customer['tier']) => value?.name || '',
      filterOperators: createMultiSelectFilterOperators(tierOptions)
    },
    {
      field: 'state',
      headerName: 'State',
      flex: 1,
      minWidth: 100,
      filterOperators: createMultiSelectFilterOperators(stateOptions)
    },
    {
      field: 'cidrBlocks',
      headerName: 'CIDR Blocks',
      flex: 2,
      minWidth: 200,
      sortable: false,
      filterOperators: createArrayContainsFilterOperators(cidrBlockOptions),
      renderCell: (params: GridRenderCellParams<Customer>) => {
        const cidrBlocks = params.row.cidrBlocks;
        const maxVisible = 2;
        const visibleCidrs = cidrBlocks.slice(0, maxVisible);
        const remainingCount = cidrBlocks.length - maxVisible;

        return (
          <Stack direction='row' spacing={0.5} alignItems='center' height='100%' sx={{ py: 0.5 }}>
            {visibleCidrs.map((cidr: string) => (
              <Chip key={cidr} label={cidr} size='small' variant='filled' />
            ))}
            {remainingCount > 0 && (
              <Chip
                label={`+${remainingCount}`}
                size='small'
                variant='outlined'
                title={cidrBlocks.slice(maxVisible).join(', ')}
              />
            )}
          </Stack>
        );
      }
    },
    {
      field: 'users',
      headerName: 'Members',
      minWidth: 140,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams<Customer>) => {
        const users = params.row.users ?? [];
        return (
          <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
            <AvatarGroup max={3} sx={{ justifyContent: 'flex-start', '& .MuiAvatar-root': { width: 28, height: 28, fontSize: 12 } }}>
              {users.map((user) => (
                <Avatar key={user.loginName} alt={user.fullName || user.loginName} src={user.avatarUrl} />
              ))}
            </AvatarGroup>
          </Box>
        );
      }
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 160,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams<Customer>) => (
        <>
          <IconButton
            size='small'
            component={Link}
            to={createRoute([AdminDashboardRoutes.CUSTOMERS, params.row.name])}
            title='View Details'
          >
            <VisibilityIcon />
          </IconButton>
          <IconButton
            size='small'
            onClick={() => handleEditClick(params.row)}
            title='Edit'
          >
            <EditIcon />
          </IconButton>
          <IconButton
            size='small'
            onClick={() => handleDeleteClick(params.row)}
            title='Delete'
            color='error'
          >
            <DeleteIcon />
          </IconButton>
        </>
      )
    }
  ];

  return (
    <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant='h4' component='h1'>
          Customer Management
        </Typography>
        <Button
          variant='contained'
          startIcon={<AddIcon />}
          onClick={handleCreateClick}
          disabled={loading}
        >
          Create Customer
        </Button>
      </Box>

            {error && (
                <Alert severity='error' sx={{ mb: 2 }} onClose={() => setError(null)}>
                    {error}
                </Alert>
            )}

            {loading && (
                <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
                    <CircularProgress/>
                </Box>
            )}

      { !loading && customers.length === 0 && (
        <Paper elevation={0} sx={{ p: 3, textAlign: "center" }}>
          <Typography color='textSecondary' gutterBottom>
            No customers found. Create one to get started.
          </Typography>
        </Paper>
      )}

      { !loading && customers.length > 0 && (
        <Paper elevation={0} sx={{ flex: 1, minHeight: 400, width: '100%', display: 'flex', flexDirection: 'column' }}>
          <DataGrid
            rows={customers}
            columns={columns}
            getRowId={(row) => row.name}
            pageSizeOptions={[20, 35, 50]}
            initialState={{
              pagination: { paginationModel: { pageSize: 20 } },
            }}
            disableRowSelectionOnClick
            sx={{
              flex: 1,
            }}
          />
        </Paper>
      )}

            <CustomerFormDialog
                open={formDialogOpen}
                customer={selectedCustomer}
                onClose={handleFormDialogClose}
                onSubmit={handleFormSubmit}
            />

            <DeleteCustomerDialog
                open={deleteDialogOpen}
                customer={selectedCustomer}
                onClose={handleDeleteDialogClose}
                onConfirm={handleDeleteConfirm}
            />
        </Box>
    );
};
