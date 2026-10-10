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

import { FunctionComponent } from 'react';
import { Box } from '@mui/material';
import { Banner } from './banner';
import { SanitizedMarkdown } from './sanitized-markdown';
import { useSiteSettings } from './use-site-settings';
import { useLocalStorage } from '../hooks/use-local-storage';

/**
 * The banner an admin configures in the runtime settings, shown when the page settings
 * don't bring a banner of their own. A dismissal is remembered by the banner's dismiss
 * token, so correcting a typo doesn't show it to everyone again.
 */
export const RegistryBanner: FunctionComponent = () => {
    const { data: settings } = useSiteSettings();
    const [dismissed, setDismissed] = useLocalStorage<string | null>('openvsx-banner-dismissed', null);

    // The registry leaves the banner keys out entirely while the banner is switched off. Values are
    // checked rather than trusted: the banner renders above every page, so a surprise from an
    // unknown registry must not be able to throw here.
    const rawMessage = settings?.bannerMessage;
    const message = typeof rawMessage === 'string' ? rawMessage.trim() : '';
    const dismissId = settings?.bannerDismissId ?? '';
    const severity = settings?.bannerSeverity === 'warning' ? 'warning' : 'info';
    return (
        <Banner
            open={message.length > 0 && dismissed !== dismissId}
            showDismissButton
            dismissButtonOnClick={() => setDismissed(dismissId)}
            color={severity}>
            {/* The message is Markdown; drop the paragraph margins so it sits on the banner's line. */}
            <Box sx={{ '& p': { my: 0 } }}>
                <SanitizedMarkdown content={message} linkify={false} />
            </Box>
        </Banner>
    );
};
