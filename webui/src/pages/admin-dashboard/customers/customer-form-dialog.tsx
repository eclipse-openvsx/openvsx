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

import React, { FC, useState, useEffect, useRef } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Button,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    CircularProgress,
    Alert,
    Box,
    Autocomplete,
    FormHelperText,
    styled
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { type Customer, EnforcementState, type Tier } from '../../../extension-registry-types';
import { MainContext } from '../../../context';
import { handleError } from '../../../utils';

interface CustomerFormDialogProps {
    open: boolean;
    customer?: Customer;
    onClose: () => void;
    onSubmit: (formData: Customer) => Promise<void>;
}

const Code = styled('code')(({ theme }) => ({
  fontFamily: 'source-code-pro, Menlo, Monaco, Consolas, "Courier New", monospace',
  backgroundColor: theme.palette.action.hover, // Subtle gray background
  padding: '2px 6px',
  borderRadius: '4px',
  fontSize: '0.9em',
  color: theme.palette.text.primary,
}));

export const CustomerFormDialog: FC<CustomerFormDialogProps> = ({ open, customer, onClose, onSubmit }) => {
    const abortController = useRef<AbortController>(new AbortController());
    const { service } = React.useContext(MainContext);
    const [formData, setFormData] = useState<Customer>({
        name: '',
        tier: undefined,
        state: EnforcementState.ENFORCEMENT,
        cidrBlocks: []
    });
    const [tiers, setTiers] = useState<Tier[]>([]);
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [touched, setTouched] = useState<Record<string, boolean>>({});

    const loadTiers = async () => {
        try {
            const data = await service.admin.getTiers(abortController.current);
            setTiers(data.tiers);
        } catch (err: any) {
            console.error('Failed to load tiers:', err);
        }
    };

    useEffect(() => {
        loadTiers();
        return () => abortController.current.abort();
    }, []);

    useEffect(() => {
        if (customer) {
            setFormData({
                name: customer.name,
                tier: customer.tier,
                state: customer.state,
                cidrBlocks: customer.cidrBlocks
            });
        } else {
            setFormData({
                name: '',
                tier: tiers.length > 0 ? tiers[0] : undefined,
                state: EnforcementState.ENFORCEMENT,
                cidrBlocks: []
            });
        }
        setErrors({});
        setTouched({});
    }, [open, customer, tiers]);

    const clearFieldError = (fieldName: string) => {
        if (errors[fieldName]) {
            setErrors(prev => {
                const newErrors = { ...prev };
                delete newErrors[fieldName];
                return newErrors;
            });
        }
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement> | SelectChangeEvent) => {
        const { name, value } = e.target;
        clearFieldError(name);

        if (name === 'tierName') {
            const tier = tiers.find((tier) => tier.name === value);
            setFormData(prev => ({
                ...prev,
                tier: tier,
            }));
        } else {
            setFormData(prev => ({ ...prev, [name]: value }));
        }
    };

    const handleBlur = (e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        const { name } = e.target;
        setTouched(prev => ({ ...prev, [name]: true }));

        // Validate the specific field on blur
        validateField(name);
    };

    const fieldValidators: Record<string, () => string | undefined> = {
        name: () => {
            if (formData.name === undefined) {
                return "Customer name is required";
            } else if (formData.name.trim() !== formData.name) {
                return "Customer name must not contain trailing whitespace";
            } else {
                return undefined;
            }
        },
        tierName: () => formData.tier?.name ? undefined : 'Tier selection is required',
        state: () => formData.state ? undefined : 'State is required',
        cidrBlocks: () => {
            if (formData.cidrBlocks && formData.cidrBlocks.length > 0) {
                const invalidEntries = formData.cidrBlocks.filter(cidr => !isValidCIDR(cidr.trim()));
                if (invalidEntries.length > 0) {
                    return `Invalid CIDR block(s): ${invalidEntries.join(', ')}`;
                }
            }
            return undefined;
        },
    };

    const validateField = (fieldName: string): string | undefined => {
        const validator = fieldValidators[fieldName];
        const error = validator?.();

        if (error) {
            setErrors(prev => ({ ...prev, [fieldName]: error }));
        }
        return error;
    };

    const isValidCIDR = (cidr: string): boolean => {
        const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/;
        const ipv6Regex = /^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(\/\d{1,3})?$/;
        return ipv4Regex.test(cidr) || ipv6Regex.test(cidr);
    };

    const handleCidrBlocksChange = (event: any, value: string[]) => {
        clearFieldError('cidrBlocks');

        // Validate all entries
        const invalidEntries = value.filter(cidr => !isValidCIDR(cidr.trim()));

        if (invalidEntries.length > 0) {
            setTouched(prev => ({ ...prev, cidrBlocks: true }));
            setErrors(prev => ({
                ...prev,
                cidrBlocks: `Invalid CIDR block(s): ${invalidEntries.join(', ')}. Please enter valid IPv4 or IPv6 CIDR notation.`
            }));
        }

        // Always update the value so the user can see and correct invalid entries
        setFormData(prev => ({
            ...prev,
            cidrBlocks: value.map(cidr => cidr.trim()),
        }));
    };

    const validateForm = (): boolean => {
        // Mark all fields as touched on submit
        setTouched({
            name: true,
            tierName: true,
            state: true,
            cidrBlocks: true,
        });

        const newErrors: Record<string, string> = {};
        for (const key of Object.keys(formData)) {
            const error = validateField(key);
            if (error !== undefined) {
                newErrors[key] = error;
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async () => {
        if (!validateForm()) {
            return;
        }

        setLoading(true);

        try {
            await onSubmit(formData);
            onClose();
        } catch (err: any) {
            setErrors({ submit: handleError(err) });
        } finally {
            setLoading(false);
        }
    };

    const isEditMode = !!customer;
    const title = isEditMode ? 'Edit Customer' : 'Create New Customer';

    return (
        <Dialog open={open} onClose={onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{title}</DialogTitle>
            <DialogContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
                {errors.submit && (
                  <Alert severity='error'>{errors.submit}</Alert>
                )}

                <TextField
                    label='Customer Name'
                    name='name'
                    value={formData.name}
                    onChange={handleChange}
                    onBlur={handleBlur}
                    fullWidth
                    placeholder='e.g., Acme Corp, TechStart Inc'
                    required={true}
                    disabled={loading}
                    error={touched.name && !!errors.name}
                    helperText={touched.name && errors.name}
                />

                <FormControl fullWidth disabled={loading} required={true} error={touched.tierName && !!errors.tierName}>
                    <InputLabel>Tier</InputLabel>
                    <Select
                        name='tierName'
                        value={formData.tier?.name ?? ''}
                        onChange={handleChange}
                        onBlur={(e) => {
                            setTouched(prev => ({ ...prev, tierName: true }));
                            validateField('tierName');
                        }}
                        label='Tier'
                    >
                        {tiers
                            .filter(tier => tier.tierType === 'NON_FREE')
                            .map(tier => (
                            <MenuItem key={tier.name} value={tier.name}>
                                {tier.name}
                            </MenuItem>
                        ))}
                    </Select>
                    {touched.tierName && errors.tierName && <FormHelperText>{errors.tierName}</FormHelperText>}
                </FormControl>

                <FormControl fullWidth disabled={loading} required={true} error={touched.state && !!errors.state}>
                    <InputLabel>State</InputLabel>
                    <Select
                        name='state'
                        value={formData.state}
                        onChange={handleChange}
                        onBlur={(e) => {
                            setTouched(prev => ({ ...prev, state: true }));
                            validateField('state');
                        }}
                        label='State'
                    >
                        {Object.keys(EnforcementState).map(key => (
                            <MenuItem key={key} value={EnforcementState[key as keyof typeof EnforcementState]}>
                                {key}
                            </MenuItem>
                        ))}
                    </Select>
                    {touched.state && errors.state && <FormHelperText>{errors.state}</FormHelperText>}
                </FormControl>

                <Autocomplete
                    multiple
                    freeSolo
                    limitTags={5}
                    disabled={loading}
                    options={[]}
                    value={formData.cidrBlocks || []}
                    onChange={handleCidrBlocksChange}
                    onBlur={() => {
                        setTouched(prev => ({ ...prev, cidrBlocks: true }));
                        validateField('cidrBlocks');
                    }}
                    renderInput={(params) => (
                        <TextField
                            {...params}
                            label='CIDR Blocks'
                            placeholder='e.g., 192.168.1.0/24'
                            error={touched.cidrBlocks && !!errors.cidrBlocks}
                            helperText={(touched.cidrBlocks && errors.cidrBlocks) || (
                              <>Enter CIDR blocks and press <Code>Enter</Code> to add each one</>
                            )}
                        />
                    )}
                />

              </Box>
            </DialogContent>

            <DialogActions sx={{ p: 2 }}>
                <Button onClick={onClose} disabled={loading}>
                    Cancel
                </Button>
                <Button
                    onClick={handleSubmit}
                    variant='contained'
                    disabled={loading || Object.keys(errors).length > 0}
                    startIcon={loading ? <CircularProgress size={20} /> : undefined}
                >
                    {isEditMode ? 'Update' : 'Create'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
