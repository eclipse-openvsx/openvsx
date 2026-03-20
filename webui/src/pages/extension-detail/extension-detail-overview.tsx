/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { FunctionComponent, ReactNode, useContext, useEffect, useState, useRef, useMemo } from 'react';
import { Box, Theme, Typography, Button, Link, NativeSelect, SxProps, styled, Grid, Stack } from '@mui/material';
import { Link as RouteLink, useNavigate, useParams } from 'react-router-dom';
import HomeIcon from '@mui/icons-material/Home';
import GitHubIcon from '@mui/icons-material/GitHub';
import BugReportIcon from '@mui/icons-material/BugReport';
import QuestionAnswerIcon from '@mui/icons-material/QuestionAnswer';
import { MainContext } from '../../context';
import { addQuery, createRoute, getTargetPlatformDisplayName, getEngineDisplayName } from '../../utils';
import { DelayedLoadIndicator } from '../../components/delayed-load-indicator';
import { SanitizedMarkdown } from '../../components/sanitized-markdown';
import { Timestamp } from '../../components/timestamp';
import { Extension, ExtensionReference, VERSION_ALIASES } from '../../extension-registry-types';
import { ExtensionListRoutes } from '../extension-list/extension-list-routes';
import { ExtensionDetailRoutes } from './extension-detail-routes';
import { ExtensionDetailDownloadsMenu } from './extension-detail-downloads-menu';

export const ExtensionDetailOverview: FunctionComponent<ExtensionDetailOverviewProps> = props => {

    const [loading, setLoading] = useState(true);
    const [readme, setReadme] = useState('');
    const { pageSettings, service, handleError } = useContext(MainContext);
    const params = useParams();
    const navigate = useNavigate();
    const abortController = useRef<AbortController>(new AbortController());

    const worksWithEngines = useMemo(() => {
        const engines = props.extension.engines;
        if (engines == null) {
            return null;
        }

        const data = Object.keys(engines)
            .map((engine) => ({
                key: engine,
                name: getEngineDisplayName(engine),
                version: engines[engine]
            }))
            .filter((d) => d.name); // only display engines with a known display name

        return (<>
            <Grid item xs='auto'>
                <Stack spacing={0.5}>
                    {
                        data.map((d) => <Box component='span' key={d.key} sx={{ color: 'primary.dark', fontWeight: 'fontWeightBold' }}>{d.name}:</Box>)
                    }
                </Stack>
            </Grid>
            <Grid item xs>
                <Stack spacing={0.5}>
                    {
                        data.map((d) => <Box component='span' key={d.key}>{d.version}</Box>)
                    }
                </Stack>
            </Grid>
        </>);
    }, [props.extension.engines]);

    const worksWithTargetPlatforms = useMemo(() => {
        return (<Grid item xs={12}>
            <Box component='span' sx={{ color: 'primary.dark', fontWeight: 'fontWeightBold' }}>Target Platforms:{' '}</Box>
            {
                Object.keys(props.extension.downloads).map((targetPlatform, index) => {
                    const displayName = getTargetPlatformDisplayName(targetPlatform);
                    return displayName ? <span key={targetPlatform}>{index > 0 ? ', ' : ''}{displayName}</span> : null;
                })
            }
        </Grid>);
    }, [props.extension.downloads]);

    useEffect(() => {
        updateReadme();
        return () => {
            abortController.current.abort();
        };
    }, []);

    useEffect(() => {
        setLoading(true);
        updateReadme();
    }, [props.extension.namespace, props.extension.name, props.extension.version]);

    const updateReadme = async (): Promise<void> => {
        if (props.extension.files.readme) {
            try {
                const readme = await service.getExtensionReadme(abortController.current, props.extension);
                setReadme(readme);
                setLoading(false);
            } catch (err) {
                handleError(err);
                setLoading(false);
            }
        } else {
            setReadme('## No README available');
            setLoading(false);
        }
    };

    const renderVersionSection = (): ReactNode => {
        const { extension } = props;
        const allVersions = Object.keys(extension.allVersions)
            .filter(version => VERSION_ALIASES.indexOf(version) < 0);
        return <>
            <Typography variant='h6'>Version</Typography>
            {
                allVersions.length === 1 ?
                    <Typography variant='body1' display='inline'>{allVersions[0]}</Typography>
                    :
                    <NativeSelect
                        name='Version'
                        value={extension.version}
                        onChange={event => props.selectVersion(event.target.value)}
                        inputProps={{ 'aria-label': 'Version' }} >
                        {
                            allVersions.map(version => <option key={version}>{version}</option>)
                        }
                    </NativeSelect>
            }
            {
                extension.preRelease ?
                    <Box component='span' sx={{ color: 'primary.dark', fontStyle: 'italic', ml: 2, p: '4px' }}>(pre-release version)</Box>
                    : ''
            }
            {
                extension.timestamp ?
                    <Box mt={1} mb={1}>
                        Published <Timestamp value={extension.timestamp} />
                    </Box>
                    : null
            }
        </>;
    };

    const renderIdentifierSection = (): ReactNode => {
        return <>
            <Typography variant='h6'>Unique Identifier</Typography>
            <Typography variant='body2'><code>{`${extension.namespace}.${extension.name}`}</code></Typography>
        </>;
    };

    const renderAliasesSection = (otherAliases: string[], sx: SxProps<Theme>): ReactNode => {
        const { extension } = props;
        const aliasButtons = otherAliases.length ?
            otherAliases.map(alias => {
                const arr = [ExtensionDetailRoutes.ROOT, extension.namespace, extension.name];
                if (params.target) {
                    arr.push(params.target);
                }
                if (alias !== 'latest') {
                    arr.push(alias);
                }

                const route = createRoute(arr);
                return <Button
                    sx={sx}
                    size='small'
                    variant='outlined'
                    key={alias}
                    title={`Switch to version with "${alias}" alias`}
                    onClick={() => navigate(route)}
                >
                    {alias}
                </Button>;
            }) : '';
        return <>
            <Typography variant='h6'>Version Alias{extension.versionAlias.length > 1 ? 'es' : ''}</Typography>
            {
                extension.versionAlias.map((alias, idx) =>
                    <Box component='span' key={alias} sx={{ color: 'primary.dark', fontWeight: 'fontWeightBold' }}>{idx > 0 ? ', ' : ''}{alias}</Box>
                )
            }
            {
                aliasButtons ? <>
                    {extension.versionAlias.length > 0 ? ' ' : ''}Switch to {aliasButtons}
                </> : ''
            }
        </>;
    };

    const renderButtonList = (kind: 'category' | 'search', title: string, arr: string[], sx: SxProps<Theme>): ReactNode => {
        const filtered = Array.from(new Set(arr)).sort((a, b) => a.localeCompare(b));
        return <>
            <Typography variant='h6'>{title}</Typography>
            {
                filtered.map((buttonLabel: string) =>
                    <Button
                        sx={sx}
                        size='small'
                        variant='outlined'
                        key={buttonLabel}
                        title={
                            kind === 'category'
                                ? `Search for extensions in "${buttonLabel}" category`
                                : `Search for extensions containing "${buttonLabel}"`
                        }
                        onClick={() => {
                            const route = addQuery(ExtensionListRoutes.MAIN, [{ key: kind, value: buttonLabel }]);
                            navigate(route);
                        }} >
                        {buttonLabel}
                    </Button>)
            }
        </>;
    };

    const renderResourceLink = (label: string, resourceLink: SxProps<Theme>, href?: string): ReactNode => {
        if (!href || !(href.startsWith('http') || href.startsWith('mailto'))) {
            return '';
        }
        let icon: ReactNode;
        if (label === 'Homepage') {
            icon = <HomeIcon fontSize='small' />;
        } else if (label === 'Repository' && href.startsWith('https://github.com/')) {
            icon = <GitHubIcon fontSize='small' />;
        } else if (label === 'Bugs') {
            icon = <BugReportIcon fontSize='small' />;
        } else if (label === 'Q\'n\'A') {
            icon = <QuestionAnswerIcon fontSize='small' />;
        }
        return <Box>
            <Link href={href} target='_blank' variant='body2' color='secondary' underline='hover' sx={resourceLink}>
                {icon}&nbsp;{label}
            </Link>
        </Box>;
    };

    const StyledRouteLink = styled(RouteLink)(({ theme }: { theme: Theme }) => ({
        textDecoration: 'none',
        color: theme.palette.text.primary,
        '&:hover': {
            textDecoration: 'underline'
        }
    }));

    const renderExtensionRef = (ref: ExtensionReference): ReactNode => {
        return <Box key={`${ref.namespace}.${ref.extension}`}>
            <StyledRouteLink to={createRoute([ExtensionDetailRoutes.ROOT, ref.namespace, ref.extension])}>
                {ref.namespace}.{ref.extension}
            </StyledRouteLink>
        </Box>;
    };

    if (!readme) {
        return <DelayedLoadIndicator loading={loading} />;
    }

    const { extension } = props;
    const tagButton = {
        fontWeight: 'normal',
        textTransform: 'none',
        mr: 0.5,
        mb: 0.5,
        padding: '1px 6px'
    };
    const resourceLink = {
        display: 'flex',
        alignItems: 'center',
        mt: 0.5
    };
    const resourcesGroup = {
        display: 'flex',
        flexDirection: 'column',
        flex: { xs: 'none', sm: 'none', md: 1, lg: 1, xl: 'none' },
        mb: { xs: 2, sm: 2, md: 0, lg: 0, xl: 2 }
    };

    const ClaimNamespace = pageSettings.elements.claimNamespace;
    const ReportAbuse = pageSettings.elements.reportAbuse;
    const DownloadTerms = pageSettings.elements.downloadTerms;
    const otherAliases = Object.keys(extension.allVersions)
        .filter(version => extension.versionAlias.indexOf(version) < 0 && VERSION_ALIASES.indexOf(version) >= 0);
    // filter internal tags
    const tags = extension.tags?.filter(t => !t.startsWith('__'));

    let downloadButton: ReactNode = null;
    if (extension.downloadable && extension.downloads && Object.keys(extension.downloads).length > 1) {
        downloadButton = <ExtensionDetailDownloadsMenu downloads={extension.downloads} />;
    } else if (extension.downloadable && extension.downloads && Object.keys(extension.downloads).length == 1) {
        downloadButton = <Button variant='contained' color='secondary' sx={{ mt: 2 }}
            href={extension.downloads[Object.keys(extension.downloads)[0]]}
        >
            Download
        </Button>;
    }

    return <Box
        sx={{
            display: 'flex',
            mt: 2,
            flexDirection: {
                xs: 'column-reverse',
                sm: 'column-reverse',
                md: 'column-reverse',
                lg: 'column-reverse',
                xl: 'row'
            }
        }}
    >
        <Box flex={5} overflow='auto'>
            <SanitizedMarkdown content={readme} />
        </Box>
        <Box
            sx={{
                flex: 1,
                display: 'flex',
                width: '100%',
                minWidth: '290px',
                mb: { xs: 2, sm: 2, md: 2, lg: 2, xl: 0 },
                ml: { xs: 0, sm: 0, md: 0, lg: 0, xl: '4.8rem' },
                flexDirection: { xs: 'column', sm: 'column', md: 'row', lg: 'row', xl: 'column' }
            }}
        >
            <Box sx={resourcesGroup}>
                <Box>
                    {renderVersionSection()}
                </Box>
                {
                    (otherAliases.length || extension.versionAlias.length) ? <Box>{renderAliasesSection(otherAliases, tagButton)}</Box> : ''
                }
            </Box>

            <Box sx={resourcesGroup}>
                <Box>
                    {renderIdentifierSection()}
                </Box>
            </Box>

            <Box sx={resourcesGroup}>
                {
                    extension.categories && extension.categories.length > 0 ?
                        <Box>
                            {renderButtonList('category', 'Categories', extension.categories, tagButton)}
                        </Box>
                        : null
                }
                {
                    tags && tags.length > 0 ?
                        <Box mt={extension.categories && extension.categories.length > 0 ? 2 : 0}>
                            {renderButtonList('search', 'Tags', tags, tagButton)}
                        </Box>
                        : null
                }
            </Box>
            {
                extension.downloads ?
                    <Box sx={resourcesGroup}>
                        <Box>
                            <Typography variant='h6'>Works With</Typography>
                            <Grid container spacing={0.5}>
                                {worksWithEngines}
                                {worksWithTargetPlatforms}
                            </Grid>
                        </Box>
                    </Box>
                    : null
            }
            <Box sx={resourcesGroup}>
                <Box>
                    <Typography variant='h6'>Resources</Typography>
                    {renderResourceLink('Homepage', resourceLink, extension.homepage)}
                    {renderResourceLink('Repository', resourceLink, extension.repository)}
                    {renderResourceLink('Bugs', resourceLink, extension.bugs)}
                    {renderResourceLink('Q\'n\'A', resourceLink, extension.qna)}
                    {downloadButton}
                    {
                        DownloadTerms && extension.downloadable && extension.downloads && Object.keys(extension.downloads).length > 0
                            ? <DownloadTerms />
                            : null
                    }
                </Box>
                {
                    extension.bundledExtensions !== undefined && extension.bundledExtensions.length > 0 ?
                        <Box mt={2}>
                            <Typography variant='h6'>Bundled Extensions</Typography>
                            {extension.bundledExtensions!.map(ref => renderExtensionRef(ref))}
                        </Box>
                        : null
                }
                {
                    extension.dependencies !== undefined && extension.dependencies.length > 0 ?
                        <Box mt={2}>
                            <Typography variant='h6'>Dependencies</Typography>
                            {extension.dependencies!.map(ref => renderExtensionRef(ref))}
                        </Box>
                        : null
                }
                <Box mt={2}>
                    {ClaimNamespace ? <ClaimNamespace extension={extension} sx={resourceLink} /> : ''}
                    {ReportAbuse ? <ReportAbuse extension={extension} sx={resourceLink} /> : ''}
                </Box>
            </Box>
        </Box>
    </Box>;
};

export interface ExtensionDetailOverviewProps {
    extension: Extension;
    selectVersion: (version: string) => void;
}
