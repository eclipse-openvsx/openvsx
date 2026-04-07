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

import { FunctionComponent, useEffect, useState, useContext, useRef } from 'react';
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
import { MainContext } from '../../../context';
import { Customer, isError, RateLimitToken } from '../../../extension-registry-types';
import { GenerateTokenDialog } from '../../../components/generate-token-dialog';
import { Timestamp } from "../../../components/timestamp";

const sectionPaperProps: PaperProps = { elevation: 1, sx: { p: 3, mb: 3 } };

export const CustomerTokenList: FunctionComponent<CustomerTokenListProps> = props => {
    const { service, handleError } = useContext(MainContext);
    const [tokens, setTokens] = useState<RateLimitToken[]>([]);
    const [dialogOpen, setDialogOpen] = useState(false);
    const abortController = useRef<AbortController>(new AbortController());

    useEffect(() => {
        fetchTokens();
    }, [props.customer]);

    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);

    const fetchTokens = async () => {
        try {
            const result = await service.admin.getCustomerRateLimitTokens(abortController.current, props.customer.name);
            setTokens([...result]);
        } catch (err) {
            handleError(err);
        }
    };

    const handleGenerate = async (description: string): Promise<string> => {
        const token = await service.admin.createCustomerRateLimitToken(abortController.current, props.customer.name, description);
        await fetchTokens();
        return token.value ?? '';
    };

    const handleDelete = async (tokenId: number) => {
        try {
            const result = await service.admin.deleteCustomerRateLimitToken(abortController.current, props.customer.name, tokenId);
            if (isError(result)) {
                throw result;
            }
            await fetchTokens();
        } catch (err) {
            handleError(err);
        }
    };

    return <Paper {...sectionPaperProps}>
        <Box
            sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                mb: 1,
                flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'row' }
            }}
        >
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
                            <IconButton edge='end' size='small' color='error' onClick={() => handleDelete(token.id)} title='Delete token'>
                                <DeleteIcon fontSize='small' />
                            </IconButton>
                        }
                    >
                        <ListItemText
                            primary={
                                <Typography sx={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis' }}>{token.description}</Typography>
                            }
                            secondary={
                                <Typography variant='body2'>Created: <Timestamp value={token.createdTimestamp}/></Typography>
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
            onError={handleError}
            title='Generate token'
        />
    </Paper>;
};

export interface CustomerTokenListProps {
    customer: Customer;
}
