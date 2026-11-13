/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, MouseEvent, useState } from 'react';
import { Button, Link, Menu, MenuItem, Typography } from '@mui/material';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import { INSTALL_CLIENTS } from './extension-detail-install-clients';

export const ExtensionDetailInstallMenu: FunctionComponent<ExtensionDetailInstallMenuProps> = props => {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const extensionId = encodeURIComponent(`${props.namespace}.${props.name}`);

    return (
        <>
            <Button
                variant='outlined'
                color='secondary'
                sx={{ mt: 2 }}
                aria-haspopup='menu'
                aria-expanded={Boolean(anchorEl)}
                endIcon={<ArrowDropDownIcon />}
                onClick={(event: MouseEvent<HTMLButtonElement>) => setAnchorEl(event.currentTarget)}>
                Install
            </Button>
            <Menu
                open={Boolean(anchorEl)}
                anchorEl={anchorEl}
                transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                sx={{ mt: 1 }}
                onClose={() => setAnchorEl(null)}>
                {INSTALL_CLIENTS.map(client => (
                    <MenuItem
                        key={client.scheme}
                        component={Link}
                        href={`${client.scheme}:extension/${extensionId}`}
                        onClick={() => setAnchorEl(null)}
                        sx={{ textDecoration: 'none' }}>
                        <Typography variant='body2' color='text.primary'>
                            {client.name}
                        </Typography>
                    </MenuItem>
                ))}
            </Menu>
        </>
    );
};

export interface ExtensionDetailInstallMenuProps {
    namespace: string;
    name: string;
}
