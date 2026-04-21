/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { createContext, FunctionComponent, PropsWithChildren, useMemo } from 'react';
import { Divider, Drawer, IconButton, List } from '@mui/material';
import { styled, Theme, CSSObject } from '@mui/material/styles';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { DrawerHeader } from './drawer-header';

export const DRAWER_WIDTH = 240;
export const COLLAPSED_WIDTH = 57;

export const SidebarContext = createContext<{ collapsed: boolean }>({ collapsed: false });

const openedMixin = (theme: Theme): CSSObject => ({
    width: DRAWER_WIDTH,
    transition: theme.transitions.create('width', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.enteringScreen,
    }),
    overflowX: 'hidden',
});

const closedMixin = (theme: Theme): CSSObject => ({
    width: COLLAPSED_WIDTH,
    transition: theme.transitions.create('width', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    overflowX: 'hidden',
});

const StyledDrawer = styled(Drawer, { shouldForwardProp: (prop) => prop !== 'open' })(
    ({ theme, open }) => ({
        width: DRAWER_WIDTH,
        flexShrink: 0,
        whiteSpace: 'nowrap',
        boxSizing: 'border-box',
        ...(open ? {
            ...openedMixin(theme),
            '& .MuiDrawer-paper': openedMixin(theme),
        } : {
            ...closedMixin(theme),
            '& .MuiDrawer-paper': closedMixin(theme),
        }),
    })
);

export const Sidepanel: FunctionComponent<PropsWithChildren<SidepanelProps>> = ({ open, onToggle, children }) => {
    const contextValue = useMemo(() => ({ collapsed: !open }), [open]);
    return (
        <SidebarContext.Provider value={contextValue}>
            <StyledDrawer variant='permanent' anchor='left' open={open}>
                <DrawerHeader>
                    <IconButton onClick={onToggle} aria-label={open ? 'collapse sidebar' : 'expand sidebar'}>
                        {open ? <ChevronLeftIcon /> : <ChevronRightIcon />}
                    </IconButton>
                </DrawerHeader>
                <Divider />
                <List disablePadding>
                    {children}
                </List>
            </StyledDrawer>
        </SidebarContext.Provider>
    );
};

export interface SidepanelProps {
    open: boolean;
    onToggle: () => void;
}
