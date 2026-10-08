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

import { FC, useEffect, useState } from 'react';
import { Alert, Box, Button, CircularProgress, IconButton, Paper, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { DeleteSizeOverrideDialog } from './delete-size-override-dialog';
import { SizeOverrideFormDialog } from './size-override-form-dialog';
import {
    useCreateSizeOverride,
    useDeleteSizeOverride,
    useSizeOverrides,
    useUpdateSizeOverride
} from './use-size-overrides';

const UNITS: ReadonlyArray<{ label: string; bytes: number }> = [
    { label: 'GB', bytes: 1024 * 1024 * 1024 },
    { label: 'MB', bytes: 1024 * 1024 },
    { label: 'KB', bytes: 1024 }
];

const formatSize = (bytes: number): string => {
    const unit = UNITS.find(candidate => bytes >= candidate.bytes && bytes % candidate.bytes === 0);
    return unit ? `${bytes / unit.bytes} ${unit.label}` : `${bytes} bytes`;
};

export const SizeOverrides: FC = () => {
    const [formDialogOpen, setFormDialogOpen] = useState(false);
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [selected, setSelected] = useState<SizeOverride | undefined>();
    const [errorDismissed, setErrorDismissed] = useState(false);

    const { data, isFetching: loading, error: loadError } = useSizeOverrides();
    const { mutateAsync: createSizeOverride } = useCreateSizeOverride();
    const { mutateAsync: updateSizeOverride } = useUpdateSizeOverride();
    const { mutateAsync: deleteSizeOverride } = useDeleteSizeOverride();

    useEffect(() => {
        setErrorDismissed(false);
    }, [loadError]);

    const error = loadError && !errorDismissed ? handleError(loadError) : null;
    const sizeOverrides: readonly SizeOverride[] = data?.sizeOverrides ?? [];

    const handleCreateClick = () => {
        setSelected(undefined);
        setFormDialogOpen(true);
    };

    const handleEditClick = (override: SizeOverride) => {
        setSelected(override);
        setFormDialogOpen(true);
    };

    const handleDeleteClick = (override: SizeOverride) => {
        setSelected(override);
        setDeleteDialogOpen(true);
    };

    const handleFormSubmit = async (override: SizeOverride) => {
        if (selected) {
            await updateSizeOverride({ id: selected.id, override });
        } else {
            await createSizeOverride(override);
        }
    };

    const handleDeleteConfirm = async () => {
        if (selected) {
            await deleteSizeOverride(selected.id);
        }
    };

    const columns: GridColDef[] = [
        { field: 'namespace', headerName: 'Namespace', flex: 1, minWidth: 160 },
        {
            field: 'extension',
            headerName: 'Extension',
            flex: 1,
            minWidth: 160,
            valueGetter: (value: string) => value || 'All extensions'
        },
        {
            field: 'maxSize',
            headerName: 'Max size',
            width: 160,
            valueFormatter: (value: number) => formatSize(value)
        },
        {
            field: 'actions',
            headerName: '',
            width: 110,
            sortable: false,
            filterable: false,
            renderCell: (params: GridRenderCellParams<SizeOverride>) => (
                <>
                    <IconButton color='primary' title='Edit size override' onClick={() => handleEditClick(params.row)}>
                        <EditIcon />
                    </IconButton>
                    <IconButton
                        color='error'
                        title='Delete size override'
                        onClick={() => handleDeleteClick(params.row)}>
                        <DeleteIcon />
                    </IconButton>
                </>
            )
        }
    ];

    const renderContent = () => {
        if (loading && sizeOverrides.length === 0) {
            return <CircularProgress />;
        }
        if (loadError && sizeOverrides.length === 0) {
            // Nothing was ever loaded, so claiming there are none would be a statement about data we
            // do not have. The alert above already says what went wrong. A refetch that fails while
            // rows are already held keeps showing them - stale beats blank.
            return null;
        }
        if (sizeOverrides.length === 0) {
            return (
                <Paper variant='outlined' sx={{ p: 3 }}>
                    <Typography>
                        No size overrides are configured. Every namespace uses the default limit from the Settings page.
                    </Typography>
                </Paper>
            );
        }
        return (
            <Paper variant='outlined' sx={{ flex: 1 }}>
                <DataGrid
                    rows={sizeOverrides}
                    columns={columns}
                    getRowId={row => row.id}
                    pageSizeOptions={[20, 35, 50]}
                    initialState={{ pagination: { paginationModel: { pageSize: 20 } } }}
                    disableRowSelectionOnClick
                />
            </Paper>
        );
    };

    return (
        <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant='h4' component='h1'>
                    Size overrides
                </Typography>
                <Button variant='contained' startIcon={<AddIcon />} onClick={handleCreateClick}>
                    Create override
                </Button>
            </Box>
            {error && (
                <Alert severity='error' sx={{ mb: 2 }} onClose={() => setErrorDismissed(true)}>
                    {error}
                </Alert>
            )}
            {renderContent()}
            <SizeOverrideFormDialog
                open={formDialogOpen}
                sizeOverride={selected}
                maxOverrideSize={data?.maxOverrideSize}
                onClose={() => {
                    setFormDialogOpen(false);
                    setSelected(undefined);
                }}
                onSubmit={handleFormSubmit}
            />
            <DeleteSizeOverrideDialog
                open={deleteDialogOpen}
                sizeOverride={selected}
                onClose={() => {
                    setDeleteDialogOpen(false);
                    setSelected(undefined);
                }}
                onConfirm={handleDeleteConfirm}
            />
        </Box>
    );
};
