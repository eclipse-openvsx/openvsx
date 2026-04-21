/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidepanel } from '../../components/sidepanel/sidepanel';
import { NavigationItem } from '../../components/sidepanel/navigation-item';
import { isNavGroup, NavEntry } from './nav-types';

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export interface AdminSidepanelProps {
    items: NavEntry[];
}

export const AdminSidepanel: FunctionComponent<AdminSidepanelProps> = ({ items }) => {
    const [open, setOpen] = useState(true);
    const { pathname } = useLocation();

    return (
        <Sidepanel open={open} onToggle={() => setOpen(prev => !prev)}>
            {items.map((entry) => {
                if (isNavGroup(entry)) {
                    return (
                        <NavigationItem key={entry.name} label={entry.name} icon={entry.icon}>
                            {entry.children.map((child) => (
                                <NavigationItem
                                    key={child.path}
                                    active={pathname.startsWith(child.path)}
                                    label={child.name}
                                    icon={child.icon}
                                    route={child.path}
                                />
                            ))}
                        </NavigationItem>
                    );
                }
                return (
                    <NavigationItem
                        key={entry.path}
                        active={pathname.startsWith(entry.path)}
                        label={entry.name}
                        icon={entry.icon}
                        route={entry.path}
                    />
                );
            })}
        </Sidepanel>
    );
};
