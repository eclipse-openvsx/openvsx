/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ReactNode, useContext } from 'react';
import { useParams } from 'react-router';
import { MainContext } from '../../../context';
import { useUserCustomers } from '../customers/use-user-customers';
import { useTrustedPublishingStatus } from '../trusted-publishing/use-trusted-publishers';
import PersonOutlinedIcon from '@mui/icons-material/PersonOutlined';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import GridViewOutlinedIcon from '@mui/icons-material/GridViewOutlined';
import SpeedOutlinedIcon from '@mui/icons-material/SpeedOutlined';

export interface SettingsTab {
    value: string;
    label: string;
    icon: ReactNode;
}

const SETTINGS_TABS: SettingsTab[] = [
    { value: 'profile', label: 'Profile', icon: <PersonOutlinedIcon /> },
    { value: 'tokens', label: 'Access Tokens', icon: <KeyOutlinedIcon /> },
    { value: 'trusted-publishers', label: 'Trusted Publishers', icon: <VerifiedUserOutlinedIcon /> },
    { value: 'extensions', label: 'Extensions', icon: <GridViewOutlinedIcon /> },
    { value: 'customers', label: 'Rate Limiting', icon: <SpeedOutlinedIcon /> }
];

/**
 * A tab a deployment adds through `PageSettings.elements.userSettingsTabs`, after the built-in
 * ones. A `value` equal to a built-in tab's is ignored. `useIsApplicable` is called as a hook on
 * every render, so the list must not change over the page's lifetime.
 */
export interface UserSettingsTab extends SettingsTab {
    element: ReactNode;
    useIsApplicable?: () => boolean;
}

const builtInValues = new Set([...SETTINGS_TABS.map(tab => tab.value), 'namespaces']);

/** The contributed tabs that do not shadow a built-in one. */
export const useContributedTabs = (): UserSettingsTab[] => {
    const { pageSettings } = useContext(MainContext);
    return (pageSettings.elements.userSettingsTabs ?? []).filter(tab => !builtInValues.has(tab.value));
};

/**
 * The settings tabs applicable to the current user: Rate Limiting only for customer members, and
 * Trusted Publishers only where the registry has the feature on. An idle or failed status query
 * leaves `enabled` undefined, so the gate fails closed.
 */
export const useSettingsTabs = (): SettingsTab[] => {
    const contributed = useContributedTabs();
    const contributedApplicable = contributed.map(tab => tab.useIsApplicable?.() ?? true);
    const hasCustomers = (useUserCustomers().data ?? []).length > 0;
    const trustedPublishingEnabled = useTrustedPublishingStatus().data?.enabled ?? false;
    const isApplicable = (tab: SettingsTab): boolean => {
        switch (tab.value) {
            case 'customers':
                return hasCustomers;
            case 'trusted-publishers':
                return trustedPublishingEnabled;
            default:
                return true;
        }
    };
    return [...SETTINGS_TABS.filter(isApplicable), ...contributed.filter((_, i) => contributedApplicable[i])];
};

/** Active settings tab; deep routes carry no :tab param, so derive it from what they do carry. */
export const useActiveSettingsTab = (): string | undefined => {
    const { tab, namespace, extension } = useParams();
    if (tab != null) {
        return tab;
    }
    if (extension != null) {
        return 'extensions';
    }
    if (namespace != null) {
        return 'namespaces';
    }
    return undefined;
};
