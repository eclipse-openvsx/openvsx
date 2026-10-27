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

import { ChangeEvent, FunctionComponent, ReactNode, useContext, useRef, useState } from 'react';
import {
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Grid,
    IconButton,
    Menu,
    MenuItem,
    Skeleton,
    Slider,
    Stack
} from '@mui/material';
import { styled } from '@mui/material/styles';
import EditIcon from '@mui/icons-material/Edit';
import RotateLeftIcon from '@mui/icons-material/RotateLeft';
import RotateRightIcon from '@mui/icons-material/RotateRight';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import AvatarEditor, { Position, type AvatarEditorRef } from 'react-avatar-editor';
import { MainContext } from '../../context';
import { Namespace } from '../../extension-registry-types';
import { useNamespaceDetails, useUpdateNamespaceDetails, useUpdateNamespaceLogo } from './use-namespace-details';
import { EmptyPlaceholder } from '../../pages/user/settings/settings-primitives';

// Square empty state standing in for a missing logo; same footprint as the image
// so the Edit button keeps its place.
const LogoPlaceholder = styled(EmptyPlaceholder)({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    aspectRatio: '1 / 1',
    padding: '1rem'
});

/**
 * Namespace logo sidebar: shows the logo (or a "no logo" placeholder) and, for
 * owners, a GitHub-style Edit menu whose upload/removal apply immediately,
 * independent of the details form.
 */
export const NamespaceLogo: FunctionComponent<NamespaceLogoProps> = props => {
    const context = useContext(MainContext);
    const editor = useRef<AvatarEditorRef>(null);
    const fileInput = useRef<HTMLInputElement>(null);

    const detailsQuery = useNamespaceDetails(props.namespace.name);
    const details = detailsQuery.data;
    const uploadLogo = useUpdateNamespaceLogo();
    const updateDetails = useUpdateNamespaceDetails();

    const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
    const [logoFile, setLogoFile] = useState<File>();
    const [editing, setEditing] = useState<boolean>(false);
    const [editorScale, setEditorScale] = useState<number>(1);
    const [editorRotation, setEditorRotation] = useState<number>(0);
    const [editorPosition, setEditorPosition] = useState<Position>();

    const canEdit = Boolean(props.namespace.detailsUrl);

    const handleFileChosen = (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        // allow re-selecting the same file later
        event.target.value = '';
        if (!file) {
            return;
        }
        if (file.type !== 'image/png' && file.type !== 'image/jpeg') {
            context.handleError(new Error(`Unsupported file type '${file.type}'`), {
                title: 'Couldn’t update the namespace logo'
            });
            return;
        }

        setLogoFile(file);
        setEditorScale(1);
        setEditorRotation(0);
        setEditorPosition(undefined);
        setEditing(true);
    };

    const handleApplyLogo = () => {
        const canvasScaled = editor.current?.getImageScaledToCanvas();
        canvasScaled?.toBlob(blob => {
            if (!blob || !logoFile) {
                return;
            }
            uploadLogo.mutate({
                namespaceName: props.namespace.name,
                detailsUrl: props.namespace.detailsUrl,
                file: blob,
                fileName: logoFile.name
            });
        });
        setEditing(false);
    };

    const removeLogo = () => {
        setMenuAnchor(null);
        if (!details) {
            return;
        }

        // The update endpoint deletes the stored logo when the payload's logo is
        // empty; send only the editable fields, unchanged. Blank social links must
        // be dropped — the server validates any non-null value as a full URL.
        updateDetails.mutate({
            detailsUrl: props.namespace.detailsUrl,
            details: {
                name: details.name,
                displayName: details.displayName,
                description: details.description,
                website: details.website,
                supportLink: details.supportLink,
                socialLinks: {
                    linkedin: details.socialLinks.linkedin || undefined,
                    github: details.socialLinks.github || undefined,
                    twitter: details.socialLinks.twitter || undefined
                },
                logo: undefined
            }
        });
    };

    const adjustScale = (x: number) => (x < 1 ? 0.5 + x / 2 : x);
    const percentageLabelFormat = (value: number) => `${Math.round(value * 100)}%`;
    const handleEditorScaleChange = (_event: Event, value: number | number[]) =>
        setEditorScale(typeof value === 'number' ? value : value[0]);

    const renderLogo = (): ReactNode => {
        if (detailsQuery.isLoading) {
            return <Skeleton variant='rounded' sx={{ width: '100%', height: 'auto', aspectRatio: '1 / 1' }} />;
        }
        if (!details?.logo) {
            return <LogoPlaceholder>No logo</LogoPlaceholder>;
        }
        return (
            <Box
                component='img'
                src={details.logo}
                alt={`${details.displayName || props.namespace.name} logo`}
                sx={{
                    display: 'block',
                    width: '100%',
                    aspectRatio: '1 / 1',
                    objectFit: 'contain',
                    borderRadius: theme => `${theme.shape.borderRadiusCard}px`,
                    border: '1px solid',
                    borderColor: 'divider'
                }}
            />
        );
    };

    return (
        <Box sx={{ position: 'relative' }}>
            {renderLogo()}
            {canEdit ? (
                <>
                    <Button
                        variant='outlined'
                        size='small'
                        startIcon={<EditIcon sx={{ fontSize: '0.8125rem' }} />}
                        onClick={event => setMenuAnchor(event.currentTarget)}
                        sx={{
                            position: 'absolute',
                            bottom: '0.5rem',
                            left: '0.5rem',
                            bgcolor: 'background.paper',
                            boxShadow: 'var(--shadow)',
                            '&:hover': { bgcolor: 'background.paper' }
                        }}>
                        Edit
                    </Button>
                    <Menu open={Boolean(menuAnchor)} anchorEl={menuAnchor} onClose={() => setMenuAnchor(null)}>
                        <MenuItem
                            onClick={() => {
                                setMenuAnchor(null);
                                fileInput.current?.click();
                            }}>
                            Upload a logo…
                        </MenuItem>
                        {details?.logo ? (
                            <MenuItem onClick={removeLogo} sx={{ color: 'error.main' }}>
                                Remove logo
                            </MenuItem>
                        ) : null}
                    </Menu>
                    <Box
                        component='input'
                        ref={fileInput}
                        type='file'
                        accept='image/png,image/jpeg'
                        onChange={handleFileChosen}
                        sx={{ display: 'none' }}
                    />
                    <Dialog open={editing} onClose={() => setEditing(false)}>
                        <DialogTitle>Edit namespace logo</DialogTitle>
                        <DialogContent sx={{ overflowY: 'unset' }}>
                            <Grid container spacing={2}>
                                <Grid item xs={12} sx={{ display: 'flex' }}>
                                    <AvatarEditor
                                        style={{ margin: '0 auto' }}
                                        ref={editor}
                                        image={logoFile ?? ''}
                                        width={120}
                                        height={120}
                                        border={8}
                                        color={[200, 200, 200, 0.6]}
                                        scale={adjustScale(editorScale)}
                                        rotate={editorRotation}
                                        position={editorPosition}
                                        onPositionChange={setEditorPosition}
                                    />
                                </Grid>
                                <Grid item xs={12}>
                                    <Grid container spacing={2}>
                                        <Grid item>
                                            <ZoomOutIcon />
                                        </Grid>
                                        <Grid item xs>
                                            <Slider
                                                min={0}
                                                max={2}
                                                step={0.01}
                                                scale={adjustScale}
                                                color='secondary'
                                                valueLabelDisplay='auto'
                                                valueLabelFormat={percentageLabelFormat}
                                                value={editorScale}
                                                onChange={handleEditorScaleChange}
                                            />
                                        </Grid>
                                        <Grid item>
                                            <ZoomInIcon />
                                        </Grid>
                                    </Grid>
                                </Grid>
                                <Grid item xs={12} sx={{ display: 'flex' }}>
                                    <Stack direction='row' spacing={2} sx={{ margin: '0 auto' }}>
                                        <IconButton
                                            onClick={() => setEditorRotation(editorRotation - 90)}
                                            title='Rotate image counter-clockwise'>
                                            <RotateLeftIcon />
                                        </IconButton>
                                        <IconButton
                                            onClick={() => setEditorRotation(editorRotation + 90)}
                                            title='Rotate image clockwise'>
                                            <RotateRightIcon />
                                        </IconButton>
                                    </Stack>
                                </Grid>
                            </Grid>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setEditing(false)}>Cancel</Button>
                            <Button variant='contained' color='secondary' autoFocus onClick={handleApplyLogo}>
                                Apply logo
                            </Button>
                        </DialogActions>
                    </Dialog>
                </>
            ) : null}
        </Box>
    );
};

export interface NamespaceLogoProps {
    namespace: Namespace;
}
