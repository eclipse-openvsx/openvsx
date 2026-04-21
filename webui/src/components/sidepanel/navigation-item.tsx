/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, PropsWithChildren, ReactNode, useContext, useRef, useState } from 'react';
import {
    Collapse, List, ListItemButton, ListItemIcon, ListItemText,
    Popover, Tooltip,
} from '@mui/material';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import { useNavigate } from 'react-router';
import { SidebarContext } from './sidepanel';

const EXPANDED_CONTEXT = { collapsed: false };

export const NavigationItem: FunctionComponent<PropsWithChildren<NavigationProps>> = props => {
    const [groupExpanded, setGroupExpanded] = useState(false);
    const [popoverOpen, setPopoverOpen] = useState(false);
    const anchorRef = useRef<HTMLDivElement>(null);
    const { collapsed } = useContext(SidebarContext);
    const navigate = useNavigate();

    const isGroup = !!props.children;

    const handleClick = () => {
        if (isGroup) {
            if (collapsed) {
                setPopoverOpen(true);
            } else {
                setGroupExpanded(prev => !prev);
            }
        } else if (props.route) {
            navigate(props.route);
        }
    };

    const button = (
        <ListItemButton
            ref={anchorRef}
            selected={props.active}
            onClick={handleClick}
            sx={{
                minHeight: 48,
                px: 2.5,
                justifyContent: collapsed ? 'center' : 'initial',
            }}
        >
            {props.icon && (
                <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 'auto' : 3, justifyContent: 'center' }}>
                    {props.icon}
                </ListItemIcon>
            )}
            {!collapsed && <ListItemText primary={props.label} />}
            {!collapsed && isGroup && (groupExpanded ? <ExpandLess /> : <ExpandMore />)}
        </ListItemButton>
    );

    return (
        <>
            {collapsed ? (
                <Tooltip title={props.label} placement='right'>
                    {button}
                </Tooltip>
            ) : (
                button
            )}

            {/* Inline expand for groups when sidebar is open */}
            {!collapsed && isGroup && (
                <Collapse in={groupExpanded} timeout='auto' unmountOnExit>
                    <List sx={{ pl: 2 }} disablePadding>
                        {props.children}
                    </List>
                </Collapse>
            )}

            {/* Floating popover for groups when sidebar is collapsed */}
            {isGroup && (
                <Popover
                    open={popoverOpen}
                    anchorEl={anchorRef.current}
                    onClose={() => setPopoverOpen(false)}
                    anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
                    transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                    disableRestoreFocus
                    elevation={2}
                >
                    <SidebarContext.Provider value={EXPANDED_CONTEXT}>
                        <List disablePadding onClick={() => setPopoverOpen(false)}>
                            {props.children}
                        </List>
                    </SidebarContext.Provider>
                </Popover>
            )}
        </>
    );
};

export interface NavigationProps {
    route?: string;
    icon?: ReactNode;
    label: string;
    active?: boolean;
}