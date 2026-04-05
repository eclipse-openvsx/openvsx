/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useEffect, useState, useRef } from 'react';
import { Typography, Box, Container, Grid, Link, Divider } from '@mui/material';
import GitHubIcon from '@mui/icons-material/GitHub';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import TwitterIcon from '@mui/icons-material/Twitter';
import { useParams } from 'react-router-dom';
import { ExtensionListItem } from '../extension-list/extension-list-item';
import { MainContext } from '../../context';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { NamespaceDetails, isError, UrlString } from '../../extension-registry-types';

export const NamespaceDetail: FunctionComponent = () => {
    const [loading, setLoading] = useState(true);
    const [truncateReadMore, setTruncateReadMore] = useState(true);
    const [showReadMore, setShowReadMore] = useState(false);
    const [namespaceDetails, setNamespaceDetails] = useState<Readonly<NamespaceDetails>>();
    const [notFoundError, setNotFoundError] = useState('');

    const { name } = useParams();
    const { pageSettings, service, handleError } = useContext(MainContext);

    const abortController = useRef<AbortController>(new AbortController());
    useEffect(() => {
        updateNamespaceDetails(name as string);
        return () => {
            abortController.current.abort();
        };
    }, []);

    useEffect(() => {
        setNamespaceDetails(undefined);
        setLoading(true);
        updateNamespaceDetails(name as string);
    }, [name]);

    const updateNamespaceDetails = async(name: string): Promise<void> => {
        try {
            const namespaceDetails = await service.getNamespaceDetails(abortController.current, name);
            if (isError(namespaceDetails)) {
                throw namespaceDetails;
            }

            setNamespaceDetails(namespaceDetails);
            setLoading(false);
            setTruncateReadMore(true);
        } catch (err) {
            if (err && err.status === 404) {
                setNotFoundError(`Namespace Not Found: ${name}`);
            } else {
                handleError(err);
            }

            setLoading(false);
        }
    };

    const readMore = () => {
        setTruncateReadMore(false);
    };

    const displayLink = (link: UrlString) => {
        return link.replace(/https?:\/\//, '');
    };

    const renderHeaderTags = (name: string, namespaceDetails?: NamespaceDetails): ReactNode => {
        const { namespaceHeadTags: NamespaceHeadTagsComponent } = pageSettings.elements;
        return <>
            { NamespaceHeadTagsComponent
                ? <NamespaceHeadTagsComponent namespaceDetails={namespaceDetails} name={name} pageSettings={pageSettings}/>
                : null
            }
        </>;
    };

    const renderNotFound = (): ReactNode => {
        return <>
            {
                notFoundError ?
                <Box p={4}>
                    <Typography variant='h5'>
                        {notFoundError}
                    </Typography>
                </Box>
                : null
            }
        </>;
    };

    const calculateShowReadMore = (el: HTMLElement) => {
        const showReadMore = truncateReadMore && el && (el.scrollHeight > el.offsetHeight || el.scrollWidth > el.offsetWidth);
        setShowReadMore(showReadMore);
    };

    const renderNamespaceDetails = (namespaceDetails: NamespaceDetails, truncateReadMore: boolean): ReactNode => {
        return <>
            <Box sx={{ bgcolor: 'neutral.dark' }}>
                <Container maxWidth='xl'>
                    <Box sx={{ display: 'flex', alignItems: 'center', flexDirection: 'column', py: 4, px: 0 }}>
                        <Grid container>
                            <Grid item>
                                <Box
                                    component='img'
                                    src={namespaceDetails.logo ?? pageSettings.urls.extensionDefaultIcon}
                                    sx={{
                                        height: '7.5rem',
                                        maxWidth: '9rem',
                                        mr: { xs: 0, sm: 0, md: '2rem', lg: '2rem', xl: '2rem' },
                                        pt: 1
                                    }}
                                    alt={namespaceDetails.displayName} />
                            </Grid>
                            <Grid item xs={7}>
                                <Grid container spacing={2}>
                                    <Grid item xs={12}>
                                        <Typography variant='h5'>{namespaceDetails.displayName}</Typography>
                                    </Grid>
                                    <Grid item xs={12} sx={{ pr: '0 !important' }}>
                                        {
                                            namespaceDetails.description
                                            ? <Box>
                                                <Typography
                                                    ref={calculateShowReadMore}
                                                    sx={ truncateReadMore
                                                        ? {
                                                            overflow: "hidden",
                                                            textOverflow: "ellipsis",
                                                            display: "-webkit-box",
                                                            WebkitLineClamp: "2",
                                                            WebkitBoxOrient: "vertical"
                                                        }
                                                        : {}
                                                    }>
                                                    { namespaceDetails.description }
                                                </Typography>
                                                { showReadMore ? <Link color='secondary' underline='hover' component='button' onClick={readMore}>Read more</Link> : null }
                                            </Box>
                                            : null
                                        }
                                    </Grid>
                                    <Grid item xs={12}>
                                        <Grid container spacing={2}>
                                            {
                                                namespaceDetails.website
                                                    ? <Grid item><Link color='secondary' underline='hover' target='_blank' href={namespaceDetails.website}>{displayLink(namespaceDetails.website)}</Link></Grid>
                                                    : null
                                            }
                                            {
                                                namespaceDetails.website && namespaceDetails.supportLink
                                                    ? <Grid item><Divider orientation='vertical' sx={{ height: '100%' }} /></Grid>
                                                    : null
                                            }
                                            {
                                                namespaceDetails.supportLink
                                                    ? <Grid item><Link color='secondary' underline='hover' target='_blank' href={namespaceDetails.supportLink}>{displayLink(namespaceDetails.supportLink)}</Link></Grid>
                                                    : null
                                            }
                                        </Grid>
                                    </Grid>
                                    <Grid item xs={12}>
                                        <Grid container spacing={2}>
                                            {
                                                namespaceDetails.socialLinks.linkedin
                                                ? <Grid item><Link target='_blank' color='text.primary' href={namespaceDetails.socialLinks.linkedin}><LinkedInIcon/></Link></Grid>
                                                : null
                                            }
                                            {
                                                namespaceDetails.socialLinks.github
                                                ? <Grid item><Link target='_blank' color='text.primary' href={namespaceDetails.socialLinks.github}><GitHubIcon/></Link></Grid>
                                                : null
                                            }
                                            {
                                                namespaceDetails.socialLinks.twitter
                                                ? <Grid item><Link target='_blank' color='text.primary' href={namespaceDetails.socialLinks.twitter}><TwitterIcon/></Link></Grid>
                                                : null
                                            }
                                        </Grid>
                                    </Grid>
                                </Grid>
                            </Grid>
                        </Grid>
                    </Box>
                </Container>
            </Box>
            { namespaceDetails.extensions ?
                <Container maxWidth='xl'>
                    <Grid container spacing={2} sx={{ justifyContent: 'center', pt: 6 }}>
                        {
                            namespaceDetails.extensions.map((ext, idx) => (
                                <ExtensionListItem
                                    idx={idx}
                                    extension={ext}
                                    filterSize={10}
                                    key={`${ext.namespace}.${ext.name}`} />
                            ))
                        }
                    </Grid>
                </Container>
                : null
            }
        </>;
    };

    return <>
        { renderHeaderTags(name as string, namespaceDetails) }
        <DelayedLoadIndicator loading={loading} />
        {
            namespaceDetails
                ? renderNamespaceDetails(namespaceDetails, truncateReadMore)
                : renderNotFound()
        }
    </>;
};