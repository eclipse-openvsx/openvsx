/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, PropsWithChildren } from 'react';
import { Divider, Drawer, IconButton, List } from '@mui/material';
import { styled } from '@mui/material/styles';
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";

export const DrawerHeader = styled('div')(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    padding: theme.spacing(0, 1),
    // necessary for content to be below app bar
    ...theme.mixins.toolbar,
    justifyContent: 'flex-end',
}));

export const Sidepanel: FunctionComponent<PropsWithChildren<SidepanelProps>> = props => {
    const width = props.width;

    return (
        <Drawer
            sx={{
                width: width,
                flexShrink: 0,
                '& .MuiDrawer-paper': {
                    width: width,
                    boxSizing: 'border-box',
                },
            }}
            variant='persistent'
            anchor='left'
            open={props.open}
        >
            <DrawerHeader>
                <IconButton onClick={props.handleDrawerClose}>
                    <ChevronLeftIcon />
                </IconButton>
            </DrawerHeader>
            <Divider />
            <List>
                {props.children}
            </List>
        </Drawer>
    );
};

interface SidepanelProps {
    width: number;
    open: boolean;
    handleDrawerClose: () => void;
}
