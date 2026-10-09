/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, FunctionComponent, useContext, useEffect, useState } from 'react';
import {
    Box,
    Link,
    TextField,
    Typography,
    Button,
    InputAdornment,
    Select,
    MenuItem,
    SelectChangeEvent
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { MainContext } from '../../context';
import { DelayedLoadIndicator } from '../delayed-load-indicator';
import { Namespace, NamespaceDetails } from '../../extension-registry-types';
import { styled } from '@mui/material/styles';
import { SaveButton } from '../save-button';
import { DetailRow, DetailsCard, DetailsGroupLabel } from '../details-card';
import { useSavedFlash } from '../../hooks/use-saved-flash';
import { SectionTitleRow, SettingsSectionTitle } from '../../pages/user/settings/settings-primitives';
import { useNamespaceDetails, useUpdateNamespaceDetails } from './use-namespace-details';

// One field (or field group) per row, padded like a DetailRow so editing lines
// up with the read-only preview it replaces.
const FormRow = styled(Box)({
    padding: '0.875rem 1.125rem'
});

/** Last path segment of a URL, tolerating a trailing slash: the social account handle. */
const lastPathSegment = (url: string): string => {
    const trimmed = url.endsWith('/') ? url.slice(0, -1) : url;
    return trimmed.substring(trimmed.lastIndexOf('/') + 1);
};

/** Splits a LinkedIn URL into the handle and the account type ('in' or 'company'). */
const parseLinkedInUrl = (url: string): { handle: string; accountType: string } => {
    const path = (url.endsWith('/') ? url.slice(0, -1) : url).split('/');
    return { handle: path[path.length - 1], accountType: path[path.length - 2] };
};

/** Placeholder shown for a details field the namespace hasn't filled in yet. */
const NOT_SET = '—';

/** Deep equality that treats empty and missing values alike, at every level. */
const equalIgnoringEmpty = (a: unknown, b: unknown): boolean => {
    if (!a || !b || typeof a !== 'object' || typeof b !== 'object') {
        return (a || undefined) === (b || undefined);
    }
    const x = a as Record<string, unknown>;
    const y = b as Record<string, unknown>;
    return [...new Set([...Object.keys(x), ...Object.keys(y)])].every(key => equalIgnoringEmpty(x[key], y[key]));
};

export const NamespaceDetailsForm: FunctionComponent<NamespaceDetailsFormProps> = props => {
    const INPUT_DISPLAY_NAME = 'display-name';
    const INPUT_DESCRIPTION = 'description';
    const INPUT_WEBSITE = 'website';
    const INPUT_SUPPORT_LINK = 'support-link';
    const INPUT_LINKEDIN = 'linkedin';
    const INPUT_GITHUB = 'github';
    const INPUT_TWITTER = 'twitter';
    const LINKED_IN_PERSONAL = 'in';
    const LINKED_IN_COMPANY = 'company';

    const context = useContext(MainContext);
    const detailsQuery = useNamespaceDetails(props.namespace.name);
    const updateDetails = useUpdateNamespaceDetails();

    const [currentDetails, setCurrentDetails] = useState<NamespaceDetails>();
    const [newDetails, setNewDetails] = useState<NamespaceDetails>();
    const [editMode, setEditMode] = useState<boolean>(false);
    const { saved, flash } = useSavedFlash(1200, () => setEditMode(false));
    const [linkedInAccountType, setLinkedInAccountType] = useState<string>(LINKED_IN_PERSONAL);

    const copy = (arg: NamespaceDetails): NamespaceDetails => {
        return JSON.parse(JSON.stringify(arg));
    };

    // Seed the editable copies from the fetched details. The social URLs are
    // reduced to handles for the form; all transforms work on copies so the
    // cached query data stays untouched.
    const seedFromDetails = (fetched: NamespaceDetails) => {
        const details = copy(fetched);
        const { linkedin, github, twitter } = details.socialLinks;

        let linkedInAccountType = LINKED_IN_PERSONAL;
        if (linkedin) {
            const parsed = parseLinkedInUrl(linkedin);
            details.socialLinks.linkedin = parsed.handle;
            linkedInAccountType = parsed.accountType;
        }
        if (github) {
            details.socialLinks.github = lastPathSegment(github);
        }
        if (twitter) {
            details.socialLinks.twitter = lastPathSegment(twitter);
        }

        setCurrentDetails(copy(details));
        setNewDetails(copy(details));
        setLinkedInAccountType(linkedInAccountType);
    };

    const fetchedDetails = detailsQuery.data;
    useEffect(() => {
        if (fetchedDetails) {
            seedFromDetails(fetchedDetails);
        }
    }, [fetchedDetails]);

    useEffect(() => {
        setEditMode(false);
    }, [props.namespace.name]);

    // Only the editable fields — echoing server-computed data back (extensions,
    // logoBytes) breaks the update endpoint. Social handles expand to full URLs.
    const buildDetailsPayload = (source: NamespaceDetails): NamespaceDetails => ({
        name: source.name,
        displayName: source.displayName,
        description: source.description,
        website: source.website,
        supportLink: source.supportLink,
        socialLinks: {
            linkedin: source.socialLinks.linkedin
                ? `https://www.linkedin.com/${linkedInAccountType}/${source.socialLinks.linkedin}`
                : undefined,
            github: source.socialLinks.github ? 'https://github.com/' + source.socialLinks.github : undefined,
            twitter: source.socialLinks.twitter ? 'https://twitter.com/' + source.socialLinks.twitter : undefined
        },
        logo: source.logo
    });

    // Compared as payloads, so only what Save would send counts as a change.
    const noChanges =
        !currentDetails ||
        !newDetails ||
        equalIgnoringEmpty(buildDetailsPayload(currentDetails), buildDetailsPayload(newDetails));

    const saveDetails = async () => {
        if (!newDetails) {
            return;
        }

        try {
            await updateDetails.mutateAsync({
                detailsUrl: props.namespace.detailsUrl,
                details: buildDetailsPayload(newDetails)
            });
            setCurrentDetails(copy(newDetails));
            flash();
        } catch (err) {
            context.handleError(err);
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
            // Pasting a full profile URL reduces it to the handle.
            case INPUT_LINKEDIN:
                if (input.value.startsWith('https://www.linkedin.com/')) {
                    const { handle, accountType } = parseLinkedInUrl(input.value);
                    details.socialLinks.linkedin = handle;
                    setLinkedInAccountType(accountType);
                } else {
                    details.socialLinks.linkedin = input.value;
                }
                break;
            case INPUT_GITHUB:
                details.socialLinks.github = input.value.startsWith('https://github.com/')
                    ? lastPathSegment(input.value)
                    : input.value;
                break;
            case INPUT_TWITTER:
                details.socialLinks.twitter = input.value.startsWith('https://twitter.com/')
                    ? lastPathSegment(input.value)
                    : input.value;
                break;
        }

        setNewDetails(details);
    };

    const handleSelectChange = (event: SelectChangeEvent<string>) => setLinkedInAccountType(event.target.value);

    // Discard field edits by re-seeding from the cached details.
    const cancelEditing = () => {
        if (fetchedDetails) {
            seedFromDetails(fetchedDetails);
        }
        setEditMode(false);
    };

    const canEdit = Boolean(props.namespace.detailsUrl);

    // The server 500s on relative URLs, so only absolute ones may be submitted.
    const isAbsoluteUrl = (value: string | undefined, allowMailto = false): boolean => {
        if (!value) {
            return true;
        }
        try {
            const protocol = new URL(value).protocol;
            return protocol === 'http:' || protocol === 'https:' || (allowMailto && protocol === 'mailto:');
        } catch {
            return false;
        }
    };
    // Social links are sent as https://<site>/<handle>, so a handle must be a single path segment.
    const isValidHandle = (value?: string): boolean => !value || /^[^\s/]+$/.test(value);

    const websiteValid = isAbsoluteUrl(newDetails?.website);
    const supportLinkValid = isAbsoluteUrl(newDetails?.supportLink, true);
    const linkedinValid = isValidHandle(newDetails?.socialLinks.linkedin);
    const githubValid = isValidHandle(newDetails?.socialLinks.github);
    const twitterValid = isValidHandle(newDetails?.socialLinks.twitter);
    const formValid = websiteValid && supportLinkValid && linkedinValid && githubValid && twitterValid;

    // Read-only notice shown instead of the Edit affordance; for unverified
    // namespaces the "Claim ownership" phrase links to the claiming docs.
    const renderReadOnlyNotice = () => {
        const claimUrl = context.pageSettings.urls.namespaceAccessInfo;
        return (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.625rem', mb: '1.25rem' }}>
                {props.namespace.verified ? (
                    <LockOutlinedIcon sx={{ fontSize: '1rem', color: 'text.disabled', flexShrink: 0 }} />
                ) : (
                    <WarningAmberIcon sx={{ fontSize: '1rem', color: 'warningAccent', flexShrink: 0 }} />
                )}
                <Typography sx={{ fontSize: '0.8125rem', color: 'text.disabled', lineHeight: 1.5 }}>
                    {props.namespace.verified ? (
                        'Only namespace owners can edit these details.'
                    ) : (
                        <>
                            This namespace isn&apos;t verified yet.{' '}
                            {claimUrl ? (
                                <Link
                                    color='secondary'
                                    underline='hover'
                                    target='_blank'
                                    href={claimUrl}
                                    sx={{ fontWeight: 600 }}>
                                    Claim ownership
                                </Link>
                            ) : (
                                'Claim ownership'
                            )}{' '}
                            to verify it and unlock editing its details.
                        </>
                    )}
                </Typography>
            </Box>
        );
    };

    // Read-only view shown by default; the form replaces it while editing.
    // Every field shows up even when empty — that's what makes it obvious how
    // much filling in a claimed namespace unlocks.
    const renderLinkRow = (label: string, url: string | undefined) => (
        <DetailRow key={label} label={label} empty={!url}>
            {url ? (
                <Link color='secondary' underline='hover' target='_blank' href={url}>
                    {url.replace(/https?:\/\//, '')}
                </Link>
            ) : (
                NOT_SET
            )}
        </DetailRow>
    );

    const renderHandleRow = (label: string, handle: string | undefined) => (
        <DetailRow key={label} label={label} empty={!handle}>
            {handle || NOT_SET}
        </DetailRow>
    );

    const renderPreview = (details: NamespaceDetails) => (
        <DetailsCard>
            <DetailRow label='Display name'>{details.displayName || details.name}</DetailRow>
            <DetailRow label='Description' empty={!details.description}>
                {details.description || NOT_SET}
            </DetailRow>
            <DetailsGroupLabel>Links</DetailsGroupLabel>
            {renderLinkRow('Website', details.website)}
            {renderLinkRow('Support', details.supportLink)}
            <DetailsGroupLabel>Social accounts</DetailsGroupLabel>
            {renderHandleRow('LinkedIn', details.socialLinks.linkedin)}
            {renderHandleRow('GitHub', details.socialLinks.github)}
            {renderHandleRow('Twitter / X', details.socialLinks.twitter)}
        </DetailsCard>
    );

    const renderForm = (details: NamespaceDetails) => (
        <DetailsCard>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    label='Display name'
                    name={INPUT_DISPLAY_NAME}
                    value={details.displayName ?? ''}
                    onChange={handleInputChange}
                    inputProps={{ maxLength: 32 }}
                />
            </FormRow>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    multiline
                    minRows={3}
                    maxRows={8}
                    label='Description'
                    placeholder='What is this namespace about?'
                    name={INPUT_DESCRIPTION}
                    value={details.description ?? ''}
                    onChange={handleInputChange}
                />
            </FormRow>
            <DetailsGroupLabel>Links</DetailsGroupLabel>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    label='Website'
                    type='url'
                    placeholder='https://example.com'
                    name={INPUT_WEBSITE}
                    value={details.website ?? ''}
                    onChange={handleInputChange}
                    error={!websiteValid}
                    helperText={!websiteValid ? 'Enter a full URL, including https://' : undefined}
                />
            </FormRow>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    label='Support link'
                    type='url'
                    placeholder='https:// or mailto:'
                    name={INPUT_SUPPORT_LINK}
                    value={details.supportLink ?? ''}
                    onChange={handleInputChange}
                    error={!supportLinkValid}
                    helperText={!supportLinkValid ? 'Enter a full URL, including https:// or mailto:' : undefined}
                />
            </FormRow>
            <DetailsGroupLabel>Social accounts</DetailsGroupLabel>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    label='LinkedIn'
                    placeholder='account name'
                    name={INPUT_LINKEDIN}
                    value={details.socialLinks.linkedin ?? ''}
                    onChange={handleInputChange}
                    error={!linkedinValid}
                    helperText={!linkedinValid ? 'Just the account name — no slashes or spaces' : undefined}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position='start'>
                                <Select
                                    variant='standard'
                                    disableUnderline
                                    value={linkedInAccountType}
                                    onChange={handleSelectChange}
                                    sx={{
                                        fontSize: '0.875rem',
                                        color: 'text.disabled',
                                        '& .MuiSelect-select': { py: 0 }
                                    }}>
                                    <MenuItem value={LINKED_IN_PERSONAL}>linkedin.com/in/</MenuItem>
                                    <MenuItem value={LINKED_IN_COMPANY}>linkedin.com/company/</MenuItem>
                                </Select>
                            </InputAdornment>
                        )
                    }}
                />
            </FormRow>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    label='GitHub'
                    placeholder='username'
                    name={INPUT_GITHUB}
                    value={details.socialLinks.github ?? ''}
                    onChange={handleInputChange}
                    error={!githubValid}
                    helperText={!githubValid ? 'Just the account name — no slashes or spaces' : undefined}
                    InputProps={{
                        startAdornment: <InputAdornment position='start'>github.com/</InputAdornment>
                    }}
                />
            </FormRow>
            <FormRow>
                <TextField
                    fullWidth
                    size='small'
                    label='Twitter / X'
                    placeholder='username'
                    name={INPUT_TWITTER}
                    value={details.socialLinks.twitter ?? ''}
                    onChange={handleInputChange}
                    error={!twitterValid}
                    helperText={!twitterValid ? 'Just the account name — no slashes or spaces' : undefined}
                    InputProps={{
                        startAdornment: <InputAdornment position='start'>twitter.com/</InputAdornment>
                    }}
                />
            </FormRow>
            <FormRow sx={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <Button onClick={cancelEditing}>Cancel</Button>
                <SaveButton
                    color='secondary'
                    saved={saved}
                    disabled={noChanges || !formValid || updateDetails.isPending}
                    onClick={saveDetails}>
                    Save namespace details
                </SaveButton>
            </FormRow>
        </DetailsCard>
    );

    if (!newDetails) {
        return <DelayedLoadIndicator loading={detailsQuery.isLoading} />;
    }

    return (
        <>
            {/* Reserve the button's height so the section doesn't shift in edit mode. */}
            <SectionTitleRow sx={{ minHeight: '2.375rem' }}>
                <SettingsSectionTitle component='h3' sx={{ m: 0 }}>
                    Details
                </SettingsSectionTitle>
                {canEdit && !editMode ? (
                    <Button
                        variant='outlined'
                        startIcon={<EditIcon sx={{ fontSize: '0.875rem' }} />}
                        onClick={() => setEditMode(true)}>
                        Edit
                    </Button>
                ) : null}
            </SectionTitleRow>
            {!canEdit ? renderReadOnlyNotice() : null}
            {editMode ? renderForm(newDetails) : renderPreview(newDetails)}
        </>
    );
};

export interface NamespaceDetailsFormProps {
    namespace: Namespace;
}
