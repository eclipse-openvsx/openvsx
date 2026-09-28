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

import { ChangeEvent, FC } from 'react';
import { FormControlLabel, FormGroup, Switch } from '@mui/material';

export interface SettingsSwitchProps {
    /** What is being toggled, for the accessible label ("Toggle <name>"). */
    name: string;
    checked: boolean;
    disabled?: boolean;
    onChange: (event: ChangeEvent<HTMLInputElement>, checked: boolean) => void;
}

/** The on/off control every runtime setting is switched with, so they all read the same. */
export const SettingsSwitch: FC<SettingsSwitchProps> = ({ name, checked, disabled, onChange }) => (
    <FormGroup>
        <FormControlLabel
            control={
                <Switch
                    color='secondary'
                    checked={checked}
                    onChange={onChange}
                    disabled={disabled}
                    inputProps={{ 'aria-label': `Toggle ${name}` }}
                />
            }
            label={checked ? 'Enabled' : 'Disabled'}
        />
    </FormGroup>
);
