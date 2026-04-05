/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, PropsWithChildren, ReactNode, useState } from 'react';
import { ListItemButton, ListItemText, Collapse, List, ListItemIcon } from '@mui/material';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import { useNavigate } from 'react-router';

export const NavigationItem: FunctionComponent<PropsWithChildren<NavigationProps>> = props => {
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();

    const handleClick = () => {
        if (props.children) {
            setOpen(!open);
        } else if (props.route) {
            props.onOpenRoute?.call(this, props.route);
            navigate(props.route);
        }
    };

    return (<>
        <ListItemButton sx={ props.active ? { bgcolor: 'action.selected' } : null } onClick={handleClick}>
            {
                props.icon && <ListItemIcon>{props.icon}</ListItemIcon>
            }
            <ListItemText primary={props.label} />
            {props.children && (open ? <ExpandLess /> : <ExpandMore />)}
        </ListItemButton>
        {
            props.children &&
            <Collapse in={open} timeout='auto' unmountOnExit>
                <List sx={{ pl: 4 }}>
                    {props.children}
                </List>
            </Collapse>
        }
    </>);
};

export interface NavigationProps {
    route?: string;
    icon?: ReactNode;
    label: string;
    active?: boolean;
    onOpenRoute?: (route: string) => void;
}