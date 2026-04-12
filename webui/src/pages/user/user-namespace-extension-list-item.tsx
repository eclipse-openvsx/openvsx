/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import type { MouseEvent, ReactNode } from "react";
import { useContext, FunctionComponent, useState, useEffect, useRef } from 'react';
import { Extension } from '../../extension-registry-types';
import { Paper, Typography, Box, styled, IconButton } from '@mui/material';
import { Link as RouteLink, useNavigate } from 'react-router-dom';
import { MainContext } from '../../context';
import { createRoute } from '../../utils';
import { Timestamp } from '../../components/timestamp';
import { ExtensionDetailRoutes } from '../extension-detail/extension-detail-routes';
import DeleteIcon from '@mui/icons-material/Delete';
import { UserSettingsRoutes } from './user-settings-routes';

const getOpacity = (extension: Extension) => {
    if (extension.deprecated) {
        return 0.5;
    } else if (extension.active === false) {
        return 0.75;
    } else {
        return 1;
    }
};

const noOverflow = {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    ml: 0.5
};

const Paragraph = styled(Box)({
    display: 'flex',
    justifyContent: 'space-between',
});

export const UserNamespaceExtensionListItem: FunctionComponent<UserNamespaceExtensionListItemProps> = props => {
    const { pageSettings, service } = useContext(MainContext);
    const [icon, setIcon] = useState<string | undefined>(undefined);
    const { extension } = props;
    const route = extension && createRoute([ExtensionDetailRoutes.ROOT, extension.namespace, extension.name]) || '';
    const deleteRoute = extension && createRoute([UserSettingsRoutes.EXTENSIONS, extension.namespace, extension.name, 'delete']) || '';
    const inactive = extension.active === false;
    const abortController = useRef<AbortController>(new AbortController());
    const navigate = useNavigate();
    useEffect(() => {
        return () => {
            abortController.current.abort();
        };
    }, []);
    useEffect(() => {
        if (icon) {
            URL.revokeObjectURL(icon);
        }

        service.getExtensionIcon(abortController.current, extension).then(setIcon);
    }, [extension]);

    const renderStatus = (): ReactNode => {
        if (extension.reviewStatus === 'under_review') {
            return <Box mt={0.25}>
                <Typography variant='body2' sx={{ fontWeight: 600 }}>Under review</Typography>
                <Typography variant='body2' sx={{ color: 'text.secondary' }}>
                    {extension.reviewMessage ?? 'Your extension is being reviewed. Please contact support for details.'}
                </Typography>
            </Box>;
        }

        if (extension.reviewStatus === 'rejected') {
            return <Box mt={0.25}>
                <Typography variant='body2' sx={{ fontWeight: 600, color: 'error.main' }}>Rejected</Typography>
                <Typography variant='body2' sx={{ color: 'text.secondary' }}>
                    {extension.reviewMessage ?? 'Your extension could not be published.'}
                </Typography>
            </Box>;
        }

        if (inactive) {
            return <Box mt={0.25}>
                Deactivated
            </Box>;
        }

        if (extension.timestamp) {
            return <Paragraph mt={0.25}>
                <span>Published:</span>
                <Timestamp
                    value={extension.timestamp}
                    sx={noOverflow} />
            </Paragraph>;
        }

        return null;
    };

    const status = renderStatus();

    const gotoDeleteRoute = (e: MouseEvent) => {
        e.preventDefault();
        navigate(deleteRoute);
    };

    return (
        extension ? (
            <RouteLink to={route} style={{ textDecoration: 'none' }}>
                <Paper
                    elevation={3}
                    title={`${extension.namespace}.${extension.name} ${extension.version} ${inactive ? '(deactivated)' : ''}`}
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        p: 1,
                        opacity: getOpacity(extension),
                        filter: extension.deprecated ? 'grayscale(100%)' : null
                    }}>
                    <Box
                        component='img'
                        src={icon ?? pageSettings?.urls.extensionDefaultIcon ?? ''}
                        alt={extension.displayName ?? extension.name}
                        sx={{
                            flex: '0 0 15%',
                            display: 'block',
                            mr: 2,
                            width: '3rem',
                            maxHeight: '4rem',
                        }}
                    />
                    <Box component='div' sx={{ flex: '1', overflow: 'hidden' }}>
                        <Paragraph>
                            <Typography variant='h6' noWrap sx={{ fontSize: '1.15rem' }}>
                                {extension.displayName ?? extension.name}
                            </Typography>
                            {props.canDelete && deleteRoute && <IconButton onClick={gotoDeleteRoute}>
                                <DeleteIcon color='error' sx={{ fontSize: '1.15rem' }}/>
                            </IconButton>}
                        </Paragraph>
                        <Paragraph>
                            <span>Version:</span>
                            <Box component='span' sx={noOverflow}>{extension.version}</Box>
                        </Paragraph>
                        {status}
                    </Box>
                </Paper>
            </RouteLink>
        )
            : null
    );
};

export interface UserNamespaceExtensionListItemProps {
    extension: Extension;
    canDelete?: boolean
}