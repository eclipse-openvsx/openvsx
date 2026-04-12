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

import type { SyntheticEvent } from "react";
import { FC } from 'react';
import { Autocomplete, TextField } from '@mui/material';
import { GridFilterInputValueProps } from '@mui/x-data-grid';

/**
 * Custom multi-select filter input component for DataGrid columns.
 * Renders an Autocomplete with multiple selection support.
 */
export const MultiSelectFilterInput: FC<GridFilterInputValueProps & { options: string[] }> = ({
  item,
  applyValue,
  options
}) => {
  const handleChange = (_event: SyntheticEvent, newValue: string[]) => {
    applyValue({ ...item, value: newValue });
  };

  return (
    <Autocomplete
      multiple
      size='small'
      options={options}
      value={(item.value as string[]) || []}
      onChange={handleChange}
      renderInput={(params) => (
        <TextField {...params} variant='standard' placeholder='Filter...' />
      )}
      sx={{ minWidth: 150, mt: 'auto' }}
    />
  );
};
