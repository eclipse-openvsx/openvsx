/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent } from 'react';
import { Box, Card, CardActionArea, CardContent, Divider, Grid, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { isNavGroup, NavEntry, RouteEntry } from './nav-types';

interface NavSection {
    groupName?: string;
    entries: RouteEntry[];
}

/** Preserve the original group structure: ungrouped items come first as a section without a title. */
function buildSections(items: NavEntry[]): NavSection[] {
    const ungrouped: RouteEntry[] = items.filter((e): e is RouteEntry => !isNavGroup(e));
    const groups: NavSection[] = items
        .filter(isNavGroup)
        .map(group => ({ groupName: group.name, entries: group.children }));

    return ungrouped.length > 0 ? [{ entries: ungrouped }, ...groups] : groups;
}

const NavCard: FunctionComponent<{ entry: RouteEntry }> = ({ entry }) => {
    const navigate = useNavigate();
    return (
        <Card variant='outlined' sx={{ height: '100%' }}>
            <CardActionArea
                onClick={() => navigate(entry.path)}
                sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', py: 3, px: 2 }}
            >
                <Box sx={{ fontSize: 40, color: 'primary.main', mb: 1, display: 'flex' }}>
                    {entry.icon}
                </Box>
                <CardContent sx={{ textAlign: 'center', p: 1 }}>
                    <Typography variant='h6' gutterBottom>{entry.name}</Typography>
                    {entry.description && (
                        <Typography variant='body2' color='text.secondary'>
                            {entry.description}
                        </Typography>
                    )}
                </CardContent>
            </CardActionArea>
        </Card>
    );
};

export interface WelcomeProps {
    items: NavEntry[];
}

export const Welcome: FunctionComponent<WelcomeProps> = ({ items }) => {
    const sections = buildSections(items);

    return (
        <Box sx={{ py: 4, px: 2 }}>
            <Typography variant='h5' gutterBottom>
                Welcome to the Admin Dashboard
            </Typography>
            <Typography variant='body1' color='text.secondary' sx={{ mb: 4 }}>
                Select a section below to get started
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                {sections.map((section, i) => (
                    <Box key={section.groupName ?? '__root__'}>
                        {section.groupName && (
                            <>
                                <Typography variant='overline' color='text.secondary' sx={{ mb: 1, display: 'block' }}>
                                    {section.groupName}
                                </Typography>
                                <Divider sx={{ mb: 2 }} />
                            </>
                        )}
                        {!section.groupName && i > 0 && <Divider sx={{ mb: 2 }} />}
                        <Grid container spacing={3}>
                            {section.entries.map(entry => (
                                <Grid item key={entry.path} xs={12} sm={6} md={4} lg={3}>
                                    <NavCard entry={entry} />
                                </Grid>
                            ))}
                        </Grid>
                    </Box>
                ))}
            </Box>
        </Box>
    );
};
