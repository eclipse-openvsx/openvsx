/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, FunctionComponent, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Box, TextField, Typography, Grid, Button, IconButton, Slider, Stack, Dialog, DialogActions, DialogTitle,
    DialogContent, InputAdornment, Select, MenuItem, Paper, SelectChangeEvent } from '@mui/material';
import { CheckCircleOutline } from '@mui/icons-material';
import BusinessIcon from '@mui/icons-material/Business';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import GitHubIcon from '@mui/icons-material/GitHub';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import PersonIcon from '@mui/icons-material/Person';
import RotateLeftIcon from '@mui/icons-material/RotateLeft';
import RotateRightIcon from '@mui/icons-material/RotateRight';
import TwitterIcon from '@mui/icons-material/Twitter';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import CloseIcon from '@mui/icons-material/Close';
import { MainContext } from '../../context';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { Namespace, NamespaceDetails, isError } from '../../extension-registry-types';
import Dropzone from 'react-dropzone';
import AvatarEditor, { Position, type AvatarEditorRef } from 'react-avatar-editor';
import _ from 'lodash';
import { styled, Theme } from '@mui/material/styles';

const getColor = (isFocused: boolean, isDragAccept: boolean, isDragReject: boolean) => {
    if (isDragAccept) {
        return 'success.main';
    } else if (isDragReject) {
        return 'error.main';
    } else if (isFocused) {
        return 'secondary.main';
    } else {
        return 'text.primary';
    }
};

const DropzoneDiv = styled('div')(({ theme }: { theme: Theme }) => ({
    gridRow: 1,
    gridColumn: 1,
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(3),
    borderWidth: 2,
    borderRadius: 2,
    borderStyle: 'dashed',
    backgroundColor: theme.palette.background.default,
    color: theme.palette.text.primary,
    outline: 'none',
    transition: 'border .24s ease-in-out',
    '&:hover + $avatarButtons': {
        display: 'flex'
    }
}));

const GridIconItem = styled(Grid)({
    display: 'flex',
    alignItems: 'center'
});

export const UserNamespaceDetails: FunctionComponent<UserNamespaceDetailsProps> = props => {
    const INPUT_DISPLAY_NAME = 'display-name';
    const INPUT_DESCRIPTION = 'description';
    const INPUT_WEBSITE = 'website';
    const INPUT_SUPPORT_LINK = 'support-link';
    const INPUT_LINKEDIN = 'linkedin';
    const INPUT_GITHUB = 'github';
    const INPUT_TWITTER = 'twitter';
    const LINKED_IN_PERSONAL = 'in';
    const LINKED_IN_COMPANY = 'company';

    const abortController = useRef<AbortController>(new AbortController());
    const editor = useRef<AvatarEditorRef>(null);

    const context = useContext(MainContext);
    const [currentDetails, setCurrentDetails] = useState<NamespaceDetails>();
    const [newDetails, setNewDetails] = useState<NamespaceDetails>();
    const [detailsUpdated, setDetailsUpdated] = useState<boolean>(false);
    const [bannerNamespaceName, setBannerNamespaceName] = useState<string>('');
    const [loading, setLoading] = useState<boolean>(true);
    const [dropzoneFile, setDropzoneFile] = useState<File>();
    const [logoPreview, setLogoPreview] = useState<string>();
    const [editing, setEditing] = useState<boolean>(false);
    const [editorScale, setEditorScale] = useState<number>(1);
    const [editorScaleAdjusted, setEditorScaleAdjusted] = useState<number>(1);
    const [editorRotation, setEditorRotation] = useState<number>(0);
    const [editorPosition, setEditorPosition] = useState<Position>();
    const [prevEditorScale, setPrevEditorScale] = useState<number>(1);
    const [prevEditorRotation, setPrevEditorRotation] = useState<number>(0);
    const [prevEditorPosition, setPrevEditorPosition] = useState<Position>();
    const [linkedInAccountType, setLinkedInAccountType] = useState<string>(LINKED_IN_PERSONAL);

    const noChanges = useMemo(() => {
        const isFalsy = (x: unknown) => !!x === false;
        return _.isEqual(_.omitBy(currentDetails, isFalsy), _.omitBy(newDetails, isFalsy));
    }, [currentDetails, newDetails]);

    useEffect(() => {
        getNamespaceDetails();
        return () => abortController.current.abort();
    }, []);

    useEffect(() => {
        setLoading(true);
        getNamespaceDetails();
    }, [props.namespace]);

    const resetLogoPreview = () => {
        if (logoPreview) {
            URL.revokeObjectURL(logoPreview);
        }
        setLogoPreview(undefined);
    };

    const getNamespaceDetails = async (): Promise<void> => {
        if (!props.namespace.name) {
            resetLogoPreview();
            setCurrentDetails(undefined);
            setNewDetails(undefined);
            setLoading(false);
            return;
        }

        try {
            const details = await context.service.getNamespaceDetails(abortController.current, props.namespace.name);
            if (isError(details)) {
                throw details;
            }

            let linkedInAccountType = LINKED_IN_PERSONAL;
            const linkedin = details.socialLinks.linkedin;
            if (linkedin) {
                const linkedinPath = linkedin.split('/');
                details.socialLinks.linkedin = linkedinPath[linkedinPath.length - 1];
                linkedInAccountType = linkedinPath[linkedinPath.length - 2];
            }

            const github = details.socialLinks.github;
            if (github) {
                details.socialLinks.github = github.substring(github.lastIndexOf('/') + 1);
            }

            const twitter = details.socialLinks.twitter;
            if (twitter) {
                details.socialLinks.twitter = twitter.substring(twitter.lastIndexOf('/') + 1);
            }

            setCurrentDetails(copy(details));
            setNewDetails(copy(details));
            setLinkedInAccountType(linkedInAccountType);
            resetLogoPreview();
            setLoading(false);
        } catch (err) {
            context.handleError(err);
            setLoading(false);
        } finally {
            setDetailsUpdated(false);
        }
    };

    const copy = (arg: NamespaceDetails): NamespaceDetails => {
        return JSON.parse(JSON.stringify(arg));
    };

    const setNamespaceDetails = async () => {
        if (!newDetails) {
            return;
        }

        setLoading(true);
        setDetailsUpdated(false);
        try {
            const details = copy(newDetails);
            details.socialLinks.linkedin = details.socialLinks.linkedin
                ? `https://www.linkedin.com/${linkedInAccountType}/${details.socialLinks.linkedin}`
                : undefined;

            details.socialLinks.github = details.socialLinks.github
                ? 'https://github.com/' + details.socialLinks.github
                : undefined;

            details.socialLinks.twitter = details.socialLinks.twitter
                ? 'https://twitter.com/' + details.socialLinks.twitter
                : undefined;

            const result = await context.service.setNamespaceDetails(abortController.current, props.namespace.detailsUrl, details);
            if (isError(result)) {
                throw result;
            }

            if (logoPreview) {
                const logoFile = await (await fetch(logoPreview)).blob();
                await context.service.setNamespaceLogo(abortController.current, props.namespace.detailsUrl, logoFile, details.logo as string);
                await getNamespaceDetails();
            } else {
                setCurrentDetails(copy(newDetails));
            }

            setDetailsUpdated(true);
            setBannerNamespaceName(details.displayName || details.name);
        } catch (err) {
            context.handleError(err);
        } finally {
            setLoading(false);
        }
    };

    const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
        if (!newDetails) {
            return;
        }

        const input = event.target;
        const details = copy(newDetails);
        switch (input.name) {
            case INPUT_DISPLAY_NAME:
                details.displayName = input.value;
                break;
            case INPUT_DESCRIPTION:
                details.description = input.value;
                break;
            case INPUT_WEBSITE:
                details.website = input.value;
                break;
            case INPUT_SUPPORT_LINK:
                details.supportLink = input.value;
                break;
            case INPUT_LINKEDIN:
                if (input.value.startsWith('https://www.linkedin.com/')) {
                    if (input.value.lastIndexOf('/') === input.value.length - 1) {
                        input.value = input.value.substring(0, input.value.length - 1);
                    }

                    const linkedinPath = input.value.split('/');
                    details.socialLinks.linkedin = linkedinPath[linkedinPath.length - 1];
                    const linkedInAccountType = linkedinPath[linkedinPath.length - 2];
                    setLinkedInAccountType(linkedInAccountType);
                } else {
                    details.socialLinks.linkedin = input.value;
                }
                break;
            case INPUT_GITHUB:
                if (input.value.startsWith('https://github.com/')) {
                    if (input.value.lastIndexOf('/') === input.value.length - 1) {
                        input.value = input.value.substring(0, input.value.length - 1);
                    }

                    details.socialLinks.github = input.value.substring(input.value.lastIndexOf('/') + 1);
                } else {
                    details.socialLinks.github = input.value;
                }
                break;
            case INPUT_TWITTER:
                if (input.value.startsWith('https://twitter.com/')) {
                    if (input.value.lastIndexOf('/') === input.value.length - 1) {
                        input.value = input.value.substring(0, input.value.length - 1);
                    }

                    details.socialLinks.twitter = input.value.substring(input.value.lastIndexOf('/') + 1);
                } else {
                    details.socialLinks.twitter = input.value;
                }
                break;
        }

        setNewDetails(details);
    };

    const handleSelectChange = (event: SelectChangeEvent<string>) => setLinkedInAccountType(event.target.value);

    const handleDrop = <T extends File>(acceptedFiles: T[]) => {
        const file = acceptedFiles[0];
        if (file.type !== 'image/png' && file.type != 'image/jpeg') {
            context.handleError(new Error(`Unsupported file type '${file.type}'`));
            return;
        }

        setDropzoneFile(file);
        setEditing(true);
        setEditorScale(1);
        setEditorScaleAdjusted(1);
        setEditorRotation(0);
        setEditorPosition(undefined);
    };

    const handleFileDialogOpen = () => {
        setDropzoneFile(undefined);
        resetLogoPreview();
    };

    const rotateLeft = () => setEditorRotation(editorRotation - 90);
    const rotateRight = () => setEditorRotation(editorRotation + 90);

    const handleEditorScaleChange = (event: Event, value: number | number[]) => {
        setEditorScale((typeof value === 'number') ? value : value[0]);
        setEditorScaleAdjusted(adjustScale(editorScale));
    };

    const handleCancelEditLogo = () => {
        setEditorScale(prevEditorScale);
        setEditorScaleAdjusted(adjustScale(prevEditorScale));
        setEditorRotation(prevEditorRotation);
        setEditorPosition(prevEditorPosition);
        setEditing(false);
    };

    const handleApplyLogo = () => {
        const canvasScaled = editor.current?.getImageScaledToCanvas();
        canvasScaled?.toBlob(async (blob) => {
            if (blob) {
                if (logoPreview) {
                    URL.revokeObjectURL(logoPreview);
                }
                setLogoPreview(URL.createObjectURL(blob));

                if (newDetails) {
                    const details = copy(newDetails);
                    details.logo = dropzoneFile!.name;
                    setNewDetails(details);
                }
            }
        });
        setEditing(false);
    };

    const adjustScale = (x: number) => {
        return x < 1 ? (0.5 + (x / 2)) : x;
    };

    const percentageLabelFormat = (value: number) => {
        return `${Math.round(value * 100)}%`;
    };

    const deleteLogo = () => {
        resetLogoPreview();
        if (newDetails) {
            const details = copy(newDetails);
            details.logo = undefined;
            setNewDetails(details);
        }
    };

    const editLogo = () => {
        setPrevEditorScale(editorScale);
        setPrevEditorRotation(editorRotation);
        setPrevEditorPosition(editorPosition);
        setEditing(true);
    };

    const handleEditorPositionChange = (editorPosition: Position) => setEditorPosition(editorPosition);

    const isDropzoneDisabled = (): boolean => {
        return logoPreview !== undefined || (newDetails !== undefined && newDetails.logo !== undefined);
    };

    const handleClose = () => setDetailsUpdated(false);

    if (!newDetails) {
        return <DelayedLoadIndicator loading={loading} />;
    }

    const successColor = context.pageSettings.themeType === 'dark' ? '#fff' : '#000';
    return <>
        <Dialog
            open={editing}
            onClose={() => setEditing(false)} >
            <DialogTitle >
                Edit namespace logo
            </DialogTitle>
            <DialogContent sx={{ overflowY: 'unset' }}>
                <Grid container spacing={2}>
                    <Grid item xs={12} sx={{ display: 'flex' }}>
                        <AvatarEditor
                            style={{ margin: '0 auto' }}
                            ref={editor}
                            image={dropzoneFile ?? ''}
                            width={120}
                            height={120}
                            border={8}
                            color={[200, 200, 200, 0.6]}
                            scale={editorScaleAdjusted}
                            rotate={editorRotation}
                            position={editorPosition}
                            onPositionChange={handleEditorPositionChange}
                        />
                    </Grid>
                    <Grid item xs={12}>
                        <Grid container spacing={2}>
                            <Grid item><ZoomOutIcon/></Grid>
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
                                    onChange={handleEditorScaleChange}/>
                            </Grid>
                            <Grid item><ZoomInIcon/></Grid>
                        </Grid>
                    </Grid>
                    <Grid item xs={12} sx={{ display: 'flex' }}>
                        <Stack direction='row' spacing={2} sx={{ margin: '0 auto' }}>
                            <IconButton onClick={rotateLeft} title='Rotate image counter-clockwise'>
                                <RotateLeftIcon/>
                            </IconButton>
                            <IconButton onClick={rotateRight} title='Rotate image clockwise'>
                                <RotateRightIcon/>
                            </IconButton>
                        </Stack>
                    </Grid>
                </Grid>
            </DialogContent>
            <DialogActions>
                <Button
                    variant='contained'
                    color='primary'
                    onClick={handleCancelEditLogo} >
                    Cancel
                </Button>
                <Button
                    autoFocus
                    onClick={handleApplyLogo} >
                    Apply logo
                </Button>
            </DialogActions>
        </Dialog>
        <Grid container spacing={2}>
            <Grid item xs={12}>
                <Typography variant='h5'>Details</Typography>
            </Grid>
            {   detailsUpdated
                ? <Grid item xs={12} alignItems='center'>
                    <Paper
                        sx={{
                            mb: 2,
                            p: 2,
                            flex: 1,
                            display: 'flex',
                            flexDirection: 'row',
                            alignItems: 'center',
                            color: successColor,
                            bgcolor: `success.${context.pageSettings.themeType}`
                        }}
                    >
                        <CheckCircleOutline fontSize='large' />
                        <Typography variant='body1' sx={{ ml: 1 }}>The details of the &ldquo;{bannerNamespaceName}&rdquo; namespace were updated.</Typography>
                        <IconButton sx={{ ml: 'auto' }} onClick={handleClose}><CloseIcon /></IconButton>
                    </Paper>
                </Grid>
                : null
            }
            <Grid item xs={4} alignItems='center'>
                <Dropzone
                    onDrop={handleDrop}
                    onFileDialogOpen={handleFileDialogOpen}
                    noClick={isDropzoneDisabled()}
                    noKeyboard={isDropzoneDisabled()}
                    noDrag={isDropzoneDisabled()}
                    maxFiles={1}
                    maxSize={4 * 1024 * 1024}>
                    {({ getRootProps, getInputProps, isFocused, isDragAccept, isDragReject }) => (
                        <Box component='section' sx={{ display: 'grid' }}>
                            <DropzoneDiv
                                {...getRootProps({ isFocused, isDragAccept, isDragReject })}
                                style={{ borderColor: getColor(isFocused, isDragAccept, isDragReject) }}
                            >
                                <input {...getInputProps({ accept: 'image/jpeg,image/png', multiple: false })} />
                                <img src={logoPreview ?? newDetails?.logo ?? context.pageSettings.urls.extensionDefaultIcon}/>
                            </DropzoneDiv>
                            { logoPreview || newDetails?.logo ?
                                <Box
                                    component='div'
                                    sx={{
                                        gridRow: 1,
                                        gridColumn: 1,
                                        mb: -6,
                                        display: 'flex',
                                        opacity: 0,
                                        height: '100%',
                                        justifyContent: 'flex-end',
                                        '&:hover': {
                                            opacity: 1
                                        }
                                    }}
                                >
                                    { logoPreview ?
                                        <IconButton onClick={editLogo} title='Edit logo' sx={{ height: 'fit-content' }}>
                                            <EditIcon/>
                                        </IconButton>
                                        : null
                                    }
                                    <IconButton onClick={deleteLogo} title='Delete logo' sx={{ height: 'fit-content' }}>
                                        <DeleteIcon/>
                                    </IconButton>
                                </Box>
                                : null
                            }
                        </Box>
                    )}
                </Dropzone>
            </Grid>
            <Grid item xs={8}>
                <Grid container spacing={2}>
                    <Grid item xs={12}>
                        <TextField fullWidth
                            label='Display name'
                            name={INPUT_DISPLAY_NAME}
                            value={ newDetails.displayName ?? '' }
                            onChange={ handleInputChange } />
                    </Grid>
                    <Grid item xs={12}>
                        <TextField fullWidth
                            multiline
                            rows={3}
                            margin='dense'
                            variant='outlined'
                            label='Description'
                            name={INPUT_DESCRIPTION}
                            value={ newDetails.description ?? '' }
                            onChange={ handleInputChange } />
                    </Grid>
                </Grid>
            </Grid>
            <Grid item xs={12}>
                <TextField fullWidth
                        label='Website'
                        type='url'
                        name={INPUT_WEBSITE}
                        value={ newDetails.website ?? '' }
                        onChange={ handleInputChange } />
            </Grid>
            <Grid item xs={12}>
                <TextField fullWidth
                    label='Support link'
                    type='url'
                    name={INPUT_SUPPORT_LINK}
                    value={ newDetails.supportLink ?? '' }
                    onChange={ handleInputChange } />
            </Grid>
            <Grid item xs={12}>
                <Grid container spacing={2}>
                    <GridIconItem item>
                        <LinkedInIcon titleAccess='LinkedIn profile'/>
                    </GridIconItem>
                    <Grid item xs>
                        <TextField fullWidth
                            name={INPUT_LINKEDIN}
                            value={ newDetails.socialLinks.linkedin ?? '' }
                            onChange={ handleInputChange }
                            InputProps={{ startAdornment: <InputAdornment position='start'>
                                <Select
                                    value={ linkedInAccountType }
                                    onChange={ handleSelectChange }
                                    sx={{
                                        '& .MuiSelect-select': {
                                            py: 1.75
                                        },
                                        '&.Mui-focused': {
                                            '& .MuiOutlinedInput-notchedOutline': {
                                                border: 0
                                            }
                                        },
                                        '& .MuiOutlinedInput-notchedOutline': {
                                            border: 0
                                        }
                                    }}
                                >
                                    <MenuItem value={ LINKED_IN_PERSONAL } selected={true}><PersonIcon sx={{ color: 'text.secondary' }} titleAccess='Personal profile'/></MenuItem>
                                    <MenuItem value={ LINKED_IN_COMPANY }><BusinessIcon sx={{ color: 'text.secondary' }} titleAccess='Company profile'/></MenuItem>
                                </Select>
                                <Typography color='textSecondary'>https://www.linkedin.com/{linkedInAccountType}/</Typography>
                            </InputAdornment> }}/>
                    </Grid>
                </Grid>
            </Grid>
            <Grid item xs={12}>
                <Grid container spacing={2}>
                    <GridIconItem item>
                        <GitHubIcon titleAccess='GitHub profile'/>
                    </GridIconItem>
                    <Grid item xs>
                        <TextField fullWidth
                            name={INPUT_GITHUB}
                            value={ newDetails.socialLinks.github ?? '' }
                            onChange={ handleInputChange }
                            InputProps={{ startAdornment: <InputAdornment position='start'>https://github.com/</InputAdornment> }}/>
                    </Grid>
                </Grid>
            </Grid>
            <Grid item xs={12}>
                <Grid container spacing={2}>
                    <GridIconItem item>
                        <TwitterIcon titleAccess='Twitter profile'/>
                    </GridIconItem>
                    <Grid item xs>
                        <TextField fullWidth
                            name={INPUT_TWITTER}
                            value={ newDetails.socialLinks.twitter ?? '' }
                            onChange={ handleInputChange }
                            InputProps={{ startAdornment: <InputAdornment position='start'>https://twitter.com/</InputAdornment> }}/>
                    </Grid>
                </Grid>
            </Grid>
            <Grid item xs={12} sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button sx={{ ml: { xs: 2, sm: 2, md: 2, lg: 0, xl: 0 } }} variant='outlined' disabled={noChanges} onClick={setNamespaceDetails}>
                    Save Namespace Details
                </Button>
            </Grid>
        </Grid>
    </>;
};

export interface UserNamespaceDetailsProps {
    namespace: Namespace;
}