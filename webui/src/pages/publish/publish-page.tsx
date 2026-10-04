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

import { ChangeEvent, FunctionComponent, useContext, useRef } from 'react';
import { Helmet } from 'react-helmet-async';
import { useNavigate } from 'react-router';
import { Box, ButtonBase, Link, Typography } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import FileUploadOutlinedIcon from '@mui/icons-material/FileUploadOutlined';
import { MainContext } from '../../context';
import { usePublishQueue } from '../../context/publish-queue-context';
import { usePublishDrop } from '../../components/publish/use-publish-drop';
import { useRegistryValue } from '../../hooks/use-registry-value';
import { PageContainer } from '../../components/page-container';
import { Eyebrow, cardSurface, focusOutline } from '../../components/page-primitives';
import { BackButton, IconTile } from '../user/settings/settings-primitives';
import { PublishQueueStrip } from '../../components/publish/publish-queue-strip';
import { UserSettingsRoutes } from '../user/user-settings-routes';
import { LoginComponent } from '../../default/login';
import { MONO_FONT } from '../../default/theme';
import { formatFileSize } from '../../utils';

// The queue lives inside this frame, under the prompt, so everything about publishing
// sits in one place. Only the prompt is clickable — the queue holds links of its own.
const DropArea = styled(Box, { shouldForwardProp: prop => prop !== 'over' })<{ over?: boolean }>(({ theme, over }) => ({
    padding: '1.25rem',
    border: `1.5px dashed ${over ? theme.palette.secondary.main : theme.palette.divider}`,
    borderRadius: theme.shape.borderRadiusCard,
    backgroundColor: over ? alpha(theme.palette.secondary.main, 0.06) : theme.palette.surface2,
    transition: 'border-color 0.15s, background-color 0.15s',
    '@media (hover: hover)': {
        '&:hover': { borderColor: theme.palette.secondary.main }
    }
}));

const DropPrompt = styled(ButtonBase)(({ theme }) => ({
    width: '100%',
    flexDirection: 'column',
    gap: '0.75rem',
    padding: '4.75rem 1.5rem',
    borderRadius: `${theme.shape.borderRadius}px`,
    fontFamily: 'inherit',
    ...focusOutline(theme)
}));

const CommandBlock = styled(Box)(({ theme }) => ({
    ...cardSurface(theme),
    backgroundColor: theme.palette.surface2,
    padding: '1rem 1.125rem',
    fontFamily: MONO_FONT,
    fontSize: '0.8125rem',
    color: theme.palette.text.secondary,
    whiteSpace: 'pre-line',
    overflowX: 'auto'
}));

const PUBLISH_COMMANDS = 'npm install -g ovsx\novsx publish my-extension.vsix -p <token>';

export const PublishPage: FunctionComponent = () => {
    const { pageSettings, user, loginProviders } = useContext(MainContext);
    const { publish, items } = usePublishQueue();
    const { over, dropProps } = usePublishDrop();
    const navigate = useNavigate();
    const fileInput = useRef<HTMLInputElement>(null);
    // The default, not the ceiling: this page cannot know which namespace a package belongs to, and
    // quoting the ceiling would promise a size most namespaces cannot publish. The queue gates on the
    // ceiling, so an override holder is never blocked by the smaller number shown here.
    const maxSize = useRegistryValue(version => version.maxExtensionSize);

    const onFilesChosen = (event: ChangeEvent<HTMLInputElement>) => {
        publish(Array.from(event.target.files ?? []));
        // Allow re-selecting the same package later.
        event.target.value = '';
    };

    return (
        <>
            <Helmet>
                <title>Publish an extension – {pageSettings.pageTitle}</title>
            </Helmet>
            <PageContainer>
                {user ? (
                    <BackButton
                        disableRipple
                        onClick={() => navigate(UserSettingsRoutes.EXTENSIONS)}
                        startIcon={<ArrowBackIcon sx={{ fontSize: '0.9375rem' }} />}
                        sx={{ mb: '1.75rem' }}>
                        Back to extensions
                    </BackButton>
                ) : null}
                <Box sx={{ mb: '2.5rem' }}>
                    <Typography component='h1' sx={{ fontSize: '1.75rem', fontWeight: 800, letterSpacing: '-0.02em' }}>
                        Publish an extension
                    </Typography>
                    <Typography sx={{ fontSize: '0.9375rem', color: 'text.secondary', mt: '0.375rem' }}>
                        Drop your <code>.vsix</code> packages here, or pick them below.
                    </Typography>
                </Box>
                {user ? (
                    <>
                        <DropArea over={over} {...dropProps}>
                            <DropPrompt onClick={() => fileInput.current?.click()}>
                                <IconTile sx={{ width: '3rem', height: '3rem', color: 'secondary.main' }}>
                                    <FileUploadOutlinedIcon />
                                </IconTile>
                                <Typography sx={{ fontSize: '1rem', fontWeight: 700 }}>
                                    Drag &amp; drop your extensions here
                                </Typography>
                                <Typography sx={{ fontSize: '0.875rem', color: 'text.disabled' }}>
                                    or click to select <code>.vsix</code> packages
                                    {maxSize ? ` — typically up to ${formatFileSize(maxSize)} each` : null}
                                </Typography>
                            </DropPrompt>
                            {items.length > 0 ? (
                                <Box
                                    sx={{
                                        mt: '1.25rem',
                                        pt: '1.25rem',
                                        borderTop: '1px dashed',
                                        borderColor: 'divider'
                                    }}>
                                    <PublishQueueStrip />
                                </Box>
                            ) : null}
                        </DropArea>
                        <Box
                            component='input'
                            ref={fileInput}
                            type='file'
                            multiple
                            aria-label='Extension packages'
                            accept='application/vsix,.vsix'
                            onChange={onFilesChosen}
                            sx={{ display: 'none' }}
                        />
                    </>
                ) : (
                    <Typography sx={{ fontSize: '0.9375rem' }}>
                        Please{' '}
                        {loginProviders ? (
                            <LoginComponent
                                loginProviders={loginProviders}
                                renderButton={(href, onClick) => (
                                    <Link color='secondary' href={href} onClick={onClick}>
                                        log in
                                    </Link>
                                )}
                            />
                        ) : (
                            'log in'
                        )}{' '}
                        to publish an extension.
                    </Typography>
                )}
                <Box sx={{ mt: '2.5rem' }}>
                    <Eyebrow sx={{ mb: '0.75rem' }}>Or publish from the command line</Eyebrow>
                    <CommandBlock component='pre'>{PUBLISH_COMMANDS}</CommandBlock>
                </Box>
            </PageContainer>
        </>
    );
};
