/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, ReactElement } from 'react';
import { Tabs, Tab, useTheme, useMediaQuery } from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import { createRoute } from '../../utils';
import { UserSettingsRoutes } from './user-settings-routes';

export const UserSettingTabs = (): ReactElement => {

    const theme = useTheme();
    const isATablet = useMediaQuery(theme.breakpoints.down('md'));
    const isAMobile = useMediaQuery(theme.breakpoints.down('sm'));
    const { tab } = useParams();
    const navigate = useNavigate();

    const handleChange = (event: ChangeEvent, newTab: string) => {
        navigate(generateRoute(newTab));
    };

    const generateRoute = (tab: string) => {
        return createRoute([UserSettingsRoutes.ROOT, tab]);
    };

    return (
        <Tabs
            value={tab}
            onChange={handleChange}
            orientation={isATablet ? 'horizontal' : 'vertical'}
            centered={isAMobile ? true : false}
            indicatorColor='secondary'
        >
            <Tab value='profile' label='Profile' />
            <Tab value='tokens' label='Access Tokens' />
            <Tab value='namespaces' label='Namespaces' />
            <Tab value='extensions' label='Extensions' />
        </Tabs>
    );
};