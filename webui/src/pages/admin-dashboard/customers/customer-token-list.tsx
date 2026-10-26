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

import { FunctionComponent, useState } from 'react';
import {
    Box,
    Typography,
    Divider,
    List,
    ListItem,
    ListItemText,
    IconButton,
    Paper,
    Button,
    type PaperProps
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { Customer } from '../../../extension-registry-types';
import { GenerateTokenDialog } from '../../../components/generate-token-dialog';
import { Timestamp } from '../../../components/timestamp';
import { useCreateCustomerToken, useCustomerTokens, useDeleteCustomerToken } from './use-customers';

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export const CustomerTokenList: FunctionComponent<CustomerTokenListProps> = props => {
    const [dialogOpen, setDialogOpen] = useState(false);

    const { data: tokens = [] } = useCustomerTokens(props.customer.name);
    const { mutateAsync: createToken } = useCreateCustomerToken(props.customer.name);
    const { mutate: deleteToken } = useDeleteCustomerToken(props.customer.name);

    const handleGenerate = async (description: string): Promise<string> => {
        const token = await createToken(description);
        return token.value ?? '';
    };

    return (
        <Paper {...sectionPaperProps}>
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    mb: 1,
                    flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' }
                }}>
                <Typography variant='h6'>Tokens</Typography>
                <Button size='small' startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
                    Generate Token
                </Button>
            </Box>
            <Divider sx={{ mb: 1 }} />
            {tokens.length === 0 ? (
                <Typography variant='body2' color='text.secondary' sx={{ py: 1 }}>
                    No rate limiting tokens for this customer.
                </Typography>
            ) : (
                <List dense disablePadding>
                    {tokens.map(token => (
                        <ListItem
                            key={token.id}
                            secondaryAction={
                                <IconButton
                                    edge='end'
                                    size='small'
                                    color='error'
                                    onClick={() => deleteToken(token.id)}
                                    title='Delete token'>
                                    <DeleteIcon fontSize='small' />
                                </IconButton>
                            }>
                            <ListItemText
                                primary={
                                    <Typography
                                        sx={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                        {token.description || 'N/A'}
                                    </Typography>
                                }
                                secondary={
                                    <Typography variant='body2'>
                                        Created: <Timestamp value={token.createdTimestamp} />
                                    </Typography>
                                }
                            />
                        </ListItem>
                    ))}
                </List>
            )}

            <GenerateTokenDialog
                open={dialogOpen}
                onClose={() => setDialogOpen(false)}
                onGenerate={handleGenerate}
                title='Generate token'
            />
        </Paper>
    );
};

export interface CustomerTokenListProps {
    customer: Customer;
}
