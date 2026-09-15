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

import { FunctionComponent, useContext } from 'react';
import { Box, Skeleton, SxProps, Theme } from '@mui/material';
import { MainContext } from '../../context';
import { Extension, SearchEntry } from '../../extension-registry-types';
import { useInView } from '../../hooks/use-in-view';
import { useExtensionIcon } from './use-extension-icon';

/**
 * Renders an extension's icon: a skeleton while loading, then the icon or the configured default.
 *
 * The icon is fetched as a blob rather than given to an `<img>` as a URL, so the browser's own
 * lazy loading does not apply and a list would request every icon in it at once. Hold the request
 * back until the icon comes near the viewport; an extension without an icon has nothing to wait
 * for and shows the default straight away.
 */
export const ExtensionIcon: FunctionComponent<ExtensionIconProps> = ({ extension, alt, sx, pending }) => {
    const { pageSettings } = useContext(MainContext);
    const hasIcon = Boolean(extension.files?.icon);
    const [ref, inView] = useInView({ enabled: hasIcon });
    const load = inView || !hasIcon;
    // Enabled only where there is something to fetch: a disabled query is not "loading", so an
    // extension without an icon renders the default on its first frame rather than a skeleton.
    const { data: icon, isLoading } = useExtensionIcon(extension, hasIcon && inView);

    if (!load || isLoading || pending) {
        // Reset the Skeleton's default height so `aspectRatio` squares it from
        // the width; an explicit height in `sx` still wins.
        return (
            <Skeleton
                ref={ref}
                variant='rounded'
                sx={[{ height: 'auto', aspectRatio: '1 / 1' }, ...(Array.isArray(sx) ? sx : [sx])]}
            />
        );
    }

    return (
        <Box
            ref={ref}
            component='img'
            src={icon ?? pageSettings.urls.extensionDefaultIcon}
            alt={alt ?? extension.displayName ?? extension.name}
            sx={sx}
        />
    );
};

export interface ExtensionIconProps {
    extension: Extension | SearchEntry;
    /** Keep the skeleton up even though the lookup finished: the icon does not exist yet. */
    pending?: boolean;
    alt?: string;
    sx?: SxProps<Theme>;
}
