/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, useContext, useState, useEffect, useRef } from 'react';
import { Link as RouteLink } from 'react-router-dom';
import { Paper, Typography, Box, Grid, Fade } from '@mui/material';
import SaveAltIcon from '@mui/icons-material/SaveAlt';
import { MainContext } from '../../context';
import { ExtensionDetailRoutes } from '../extension-detail/extension-detail-routes';
import { SearchEntry } from '../../extension-registry-types';
import { ExtensionRatingStars } from '../extension-detail/extension-rating-stars';
import { createRoute } from '../../utils';

export const ExtensionListItem: FunctionComponent<ExtensionListItemProps> = props => {
    const [icon, setIcon] = useState<string>();
    const context = useContext(MainContext);
    const abortController = useRef<AbortController>(new AbortController());

    useEffect(() => {
        updateChanges();
        return () => {
            abortController.current.abort();
            if (icon) {
                URL.revokeObjectURL(icon);
            }
        };
    }, []);

    useEffect(() => {
        updateChanges();
    }, [props.extension.namespace, props.extension.name, props.extension.version]);

    const updateChanges = async (): Promise<void> => {
        if (icon) {
            URL.revokeObjectURL(icon);
        }
        try {
            const icon = await context.service.getExtensionIcon(abortController.current, props.extension);
            setIcon(icon);
        } catch (err) {
            context.handleError(err);
        }
    };

    const { extension, filterSize, idx } = props;
    const route = createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name]);
    const numberFormat = new Intl.NumberFormat(undefined, { notation: 'compact', compactDisplay: 'short' } as any);
    const downloadCountFormatted = numberFormat.format(extension.downloadCount ?? 0);
    return <Fade in={true} timeout={{ enter: ((filterSize + idx) % filterSize) * 200 }}>
        <Grid item xs={12} sm={3} md={2} title={extension.displayName ?? extension.name} sx={{ maxWidth: '14.875rem', minWidth: '11.875rem' }}>
            <RouteLink to={route} style={{ textDecoration: 'none' }}>
                <Paper
                    elevation={3}
                    sx={{
                        py: 3,
                        px: 2,
                        '& > *': {
                            '&:not(:last-child)': {
                                marginBottom: '.5rem',
                            }
                        },
                        opacity: extension.deprecated ? 0.5 : undefined,
                        filter: extension.deprecated ? 'grayscale(100%)' : undefined
                    }}
                >
                    <Box display='flex' justifyContent='center' alignItems='center' width='100%' height={80}>
                        <Box
                            component='img'
                            src={icon ?? context.pageSettings.urls.extensionDefaultIcon}
                            alt={extension.displayName ?? extension.name}
                            sx={{ width: '4.5rem', maxHeight: '5.4rem' }}
                        />
                    </Box>
                    <Box display='flex' justifyContent='center'>
                        <Typography variant='h6' noWrap style={{ fontSize: '1.15rem' }}>
                            {extension.displayName ?? extension.name}
                        </Typography>
                    </Box>
                    <Box display='flex' justifyContent='space-between'>
                        <Typography component='div' variant='caption' noWrap={true} align='left'>
                            {extension.namespace}
                        </Typography>
                        <Typography component='div' variant='caption' noWrap={true} align='right'>
                            {extension.version}
                        </Typography>
                    </Box>
                    <Box display='flex' justifyContent='center'>
                        <ExtensionRatingStars number={extension.averageRating ?? 0} fontSize='small' />
                        &nbsp;
                        {downloadCountFormatted != "0" && <><SaveAltIcon /> {downloadCountFormatted}</>}
                    </Box>
                </Paper>
            </RouteLink>
        </Grid>
    </Fade>;
};

export interface ExtensionListItemProps {
    extension: SearchEntry;
    idx: number;
    filterSize: number;
}