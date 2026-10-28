/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useEffect, useState } from 'react';
import { Typography, Box, Container, Link, Divider } from '@mui/material';
import GitHubIcon from '@mui/icons-material/GitHub';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import TwitterIcon from '@mui/icons-material/Twitter';
import { useParams } from 'react-router';
import { ExtensionCard } from '../../components/extension-card';
import { ExtensionGrid } from '../../components/page-primitives';
import { MainContext } from '../../context';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { NamespaceDetails, UrlString } from '../../extension-registry-types';
import { isNotFound } from '../../server-request';
import { useNamespaceDetails } from '../../components/namespace/use-namespace-details';

export const NamespaceDetail: FunctionComponent = () => {
    const [truncateReadMore, setTruncateReadMore] = useState(true);
    const [showReadMore, setShowReadMore] = useState(false);

    const { name } = useParams();
    const { pageSettings } = useContext(MainContext);
    const { data: namespaceDetails, error: detailsError, isLoading } = useNamespaceDetails(name as string);
    const notFound = isNotFound(detailsError);

    useEffect(() => {
        setTruncateReadMore(true);
    }, [name]);

    const readMore = () => {
        setTruncateReadMore(false);
    };

    const displayLink = (link: UrlString) => {
        return link.replace(/https?:\/\//, '');
    };

    const renderHeaderTags = (name: string, namespaceDetails?: NamespaceDetails): ReactNode => {
        const { namespaceHeadTags: NamespaceHeadTagsComponent } = pageSettings.elements;
        return (
            <>
                {NamespaceHeadTagsComponent ? (
                    <NamespaceHeadTagsComponent
                        namespaceDetails={namespaceDetails}
                        name={name}
                        pageSettings={pageSettings}
                    />
                ) : null}
            </>
        );
    };

    const renderNotFound = (): ReactNode => {
        return (
            <>
                {notFound ? (
                    <Box p={4}>
                        <Typography variant='h5'>{`Namespace Not Found: ${name}`}</Typography>
                    </Box>
                ) : null}
            </>
        );
    };

    const calculateShowReadMore = (el: HTMLElement) => {
        const showReadMore =
            truncateReadMore && el && (el.scrollHeight > el.offsetHeight || el.scrollWidth > el.offsetWidth);
        setShowReadMore(showReadMore);
    };

    const renderNamespaceDetails = (namespaceDetails: NamespaceDetails, truncateReadMore: boolean): ReactNode => {
        const { website, supportLink, socialLinks } = namespaceDetails;
        return (
            <>
                <Box sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
                    <Container maxWidth='xl' sx={{ py: { xs: '2rem', sm: '3rem' } }}>
                        <Box
                            sx={{
                                display: 'flex',
                                flexDirection: { xs: 'column', sm: 'row' },
                                alignItems: { xs: 'flex-start', sm: 'center' },
                                gap: { xs: '1.5rem', sm: '2rem' }
                            }}>
                            <Box
                                component='img'
                                src={namespaceDetails.logo ?? pageSettings.urls.extensionDefaultIcon}
                                alt={namespaceDetails.displayName}
                                sx={{ width: '7.5rem', height: '7.5rem', objectFit: 'contain', flexShrink: 0 }}
                            />
                            <Box sx={{ minWidth: 0, flex: 1 }}>
                                <Typography variant='h5' sx={{ fontWeight: 700 }}>
                                    {namespaceDetails.displayName}
                                </Typography>
                                {namespaceDetails.description ? (
                                    <Box sx={{ mt: '0.5rem' }}>
                                        <Typography
                                            color='text.secondary'
                                            ref={calculateShowReadMore}
                                            sx={
                                                truncateReadMore
                                                    ? {
                                                          overflow: 'hidden',
                                                          textOverflow: 'ellipsis',
                                                          display: '-webkit-box',
                                                          WebkitLineClamp: '2',
                                                          WebkitBoxOrient: 'vertical'
                                                      }
                                                    : {}
                                            }>
                                            {namespaceDetails.description}
                                        </Typography>
                                        {showReadMore ? (
                                            <Link
                                                color='secondary'
                                                underline='hover'
                                                component='button'
                                                onClick={readMore}>
                                                Read more
                                            </Link>
                                        ) : null}
                                    </Box>
                                ) : null}
                                {website || supportLink ? (
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            flexWrap: 'wrap',
                                            gap: '0.75rem',
                                            mt: '1rem'
                                        }}>
                                        {website ? (
                                            <Link color='secondary' underline='hover' target='_blank' href={website}>
                                                {displayLink(website)}
                                            </Link>
                                        ) : null}
                                        {website && supportLink ? <Divider orientation='vertical' flexItem /> : null}
                                        {supportLink ? (
                                            <Link
                                                color='secondary'
                                                underline='hover'
                                                target='_blank'
                                                href={supportLink}>
                                                {displayLink(supportLink)}
                                            </Link>
                                        ) : null}
                                    </Box>
                                ) : null}
                                {socialLinks.linkedin || socialLinks.github || socialLinks.twitter ? (
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: '1rem', mt: '1rem' }}>
                                        {socialLinks.linkedin ? (
                                            <Link target='_blank' color='text.primary' href={socialLinks.linkedin}>
                                                <LinkedInIcon />
                                            </Link>
                                        ) : null}
                                        {socialLinks.github ? (
                                            <Link target='_blank' color='text.primary' href={socialLinks.github}>
                                                <GitHubIcon />
                                            </Link>
                                        ) : null}
                                        {socialLinks.twitter ? (
                                            <Link target='_blank' color='text.primary' href={socialLinks.twitter}>
                                                <TwitterIcon />
                                            </Link>
                                        ) : null}
                                    </Box>
                                ) : null}
                            </Box>
                        </Box>
                    </Container>
                </Box>
                {namespaceDetails.extensions ? (
                    <Container maxWidth='xl' sx={{ py: { xs: '2rem', sm: '3rem' } }}>
                        <ExtensionGrid>
                            {namespaceDetails.extensions.map((ext, idx) => (
                                <ExtensionCard
                                    extension={ext}
                                    fadeDelayMs={(idx % 10) * 200}
                                    key={`${ext.namespace}.${ext.name}`}
                                />
                            ))}
                        </ExtensionGrid>
                    </Container>
                ) : null}
            </>
        );
    };

    return (
        <>
            {renderHeaderTags(name as string, namespaceDetails)}
            <DelayedLoadIndicator loading={isLoading} />
            {namespaceDetails ? renderNamespaceDetails(namespaceDetails, truncateReadMore) : renderNotFound()}
        </>
    );
};
