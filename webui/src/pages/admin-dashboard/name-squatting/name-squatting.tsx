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

import { FC } from 'react';
import { Box, Tab, Tabs, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import { useSearchParams } from 'react-router';
import { AllowlistTab } from './allowlist-tab';
import { CollisionsTab } from './collisions-tab';
import { NamespacesTab } from './namespaces-tab';

type NameSquattingTab = 'collisions' | 'namespaces' | 'allowlist';

const TABS: NameSquattingTab[] = ['collisions', 'namespaces', 'allowlist'];

const DESCRIPTIONS: Record<NameSquattingTab, string> = {
    collisions:
        'Active extensions in different namespaces whose name or display name match or closely resemble each other, ' +
        'grouped under the one published first. Published first is not proof of being the original: someone may ' +
        'publish an extension here before its author does. The most recently flagged come first, followed by display ' +
        'names shared across namespaces.',
    namespaces:
        'Namespaces whose names differ by a typo or only in separators, the one with more downloads as target. ' +
        'Popular targets shows the pairs whose target is far more downloaded than the look-alike; All pairs adds ' +
        'the quieter ones. A namespace created in the last minute may not show yet.',
    allowlist: 'Pairs admins decided are not squatting. Removing an allow reports the pair again while the names match.'
};

// Same gutters as the Extension Scans page.
const Page = styled(Box)(({ theme }) => ({
    padding: theme.spacing(0, 2, 3)
}));

/**
 * Extensions in different namespaces whose names match or resemble each other, namespaces whose names
 * resemble each other, and the pairs admins allowed. The tab is kept in the URL so a link can open it.
 */
export const NameSquatting: FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const tab = TABS.find(candidate => candidate === searchParams.get('tab')) ?? 'collisions';

    return (
        <Page>
            <Typography variant='h5' component='h1' gutterBottom>
                Name Squatting
            </Typography>
            <Typography color='text.secondary' sx={{ mb: 2 }}>
                {DESCRIPTIONS[tab]}
            </Typography>
            <Tabs
                value={tab}
                onChange={(_event, next: NameSquattingTab) =>
                    setSearchParams(next === 'collisions' ? {} : { tab: next }, { replace: true })
                }
                sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
                <Tab value='collisions' label='Collisions' id='name-squatting-tab-collisions' />
                <Tab value='namespaces' label='Namespaces' id='name-squatting-tab-namespaces' />
                <Tab value='allowlist' label='Allowlist' id='name-squatting-tab-allowlist' />
            </Tabs>
            <Box role='tabpanel' aria-labelledby={`name-squatting-tab-${tab}`}>
                {tab === 'collisions' && <CollisionsTab />}
                {tab === 'namespaces' && <NamespacesTab />}
                {tab === 'allowlist' && <AllowlistTab />}
            </Box>
        </Page>
    );
};
