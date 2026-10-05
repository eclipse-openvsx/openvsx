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

import {
    FunctionComponent,
    SyntheticEvent,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useRef,
    useState
} from 'react';
import {
    Alert,
    Autocomplete,
    Avatar,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    IconButton,
    InputBase,
    MenuItem,
    Paper,
    Select,
    Stack,
    Typography
} from '@mui/material';
import type { AutocompleteInputChangeReason, AutocompleteRenderInputParams } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import PersonIcon from '@mui/icons-material/Person';
import { useParams, useNavigate } from 'react-router';
import { UserRelationships } from '../../../extension-registry-types';
import { ErrorResponse } from '../../../server-request';
import { MainContext } from '../../../context';
import { AccessControlDetails } from './access-control-details';
import { SearchListContainer } from '../search-list-container';
import { handleError as formatError } from '../../../utils';
import { AdminDashboardRoutes } from '../admin-dashboard-routes';
import { useDebouncedCallback } from '../../../hooks/use-debounced-callback';
import { useAdminUserSearch } from '../../../hooks/use-admin-user-search';

const ROLE_FILTER_OPTIONS = [
    { value: '', label: 'Any role' },
    { value: 'admin', label: 'Admin' },
    { value: 'privileged', label: 'Privileged' },
    { value: 'none', label: 'No role' }
];

// How close to the bottom of the dropdown (in px) the user must scroll before the next page loads.
const LOAD_MORE_THRESHOLD = 200;

const roleIcon = (role: string | undefined) =>
    role ? <AdminPanelSettingsIcon fontSize='small' /> : <PersonIcon fontSize='small' />;

export const AccessControl: FunctionComponent = () => {
    const { login: loginParam } = useParams<{ login?: string }>();
    const navigate = useNavigate();
    const { pageSettings } = useContext(MainContext);

    const [searchText, setSearchText] = useState(loginParam ?? '');
    const [inputValue, setInputValue] = useState(loginParam ?? '');
    const [roleFilter, setRoleFilter] = useState('');
    const [selected, setSelected] = useState<UserRelationships | null>(null);
    const [dirty, setDirty] = useState(false);
    // The switch waiting on the discard confirmation. Held as a thunk, hence the extra arrow in
    // setPendingSwitch - useState would otherwise call a function argument as an initializer.
    const [pendingSwitch, setPendingSwitch] = useState<(() => void) | null>(null);

    const debouncedSetSearch = useDebouncedCallback(setSearchText);

    const { data, isFetching, isFetchingNextPage, error, hasNextPage, fetchNextPage } = useAdminUserSearch(
        searchText,
        roleFilter
    );

    const users = useMemo(() => data?.pages.flatMap(page => page.content) ?? [], [data]);
    const listLoading = isFetching && !isFetchingNextPage;

    // Guards the deep-link resolution below against re-selecting a user clearSelection just
    // dismissed: navigate() only updates loginParam a render or two later, so in between,
    // loginParam still names the dismissed user while selected is already null - exactly the
    // condition this effect otherwise treats as "resolve this deep link".
    const dismissedParamRef = useRef<string | null>(null);

    // Resolve a deep-linked user once the matching page has loaded.
    useEffect(() => {
        if (!loginParam) {
            dismissedParamRef.current = null;
            return;
        }
        if (selected || loginParam === dismissedParamRef.current) {
            return;
        }
        const match = users.find(u => u.user.loginName === loginParam);
        if (match) {
            setSelected(match);
        }
    }, [loginParam, selected, users]);

    const clearSelection = useCallback(() => {
        if (selected || loginParam) {
            dismissedParamRef.current = loginParam ?? null;
            setSelected(null);
            navigate(AdminDashboardRoutes.ACCESS_CONTROL, { replace: true });
        }
    }, [selected, loginParam, navigate]);

    // Nothing may replace the selection while the details card holds unsaved edits without the
    // admin saying so - the card is unmounted on a switch, taking the edits with it.
    const guarded = (change: () => void) => {
        if (dirty) {
            setPendingSwitch(() => change);
        } else {
            change();
        }
    };

    const handleSelect = (_event: SyntheticEvent, value: UserRelationships | null) => {
        if (!value) {
            guarded(clearSelection);
            return;
        }
        guarded(() => {
            setSelected(value);
            setInputValue(value.user.loginName);
            navigate(`${AdminDashboardRoutes.ACCESS_CONTROL}/${encodeURIComponent(value.user.loginName)}`, {
                replace: true
            });
        });
    };

    const handleInputChange = (_event: SyntheticEvent, value: string, reason: AutocompleteInputChangeReason) => {
        setInputValue(value);
        // 'reset' fires when the input syncs to the selected option's label — nothing else to do.
        if (reason === 'reset') {
            return;
        }
        // Typing only searches while there are unsaved edits; the selection survives until an option
        // is actually picked, which is where the confirmation belongs. Asking per keystroke would not.
        if (!dirty) {
            clearSelection();
        }
        if (reason === 'clear') {
            setSearchText('');
        } else {
            debouncedSetSearch(value);
        }
    };

    const handleClear = () => {
        guarded(() => {
            setInputValue('');
            setSearchText('');
            clearSelection();
        });
    };

    const handleDiscard = () => {
        pendingSwitch?.();
        setPendingSwitch(null);
    };

    const loadMoreOnScroll = (event: SyntheticEvent) => {
        const listbox = event.currentTarget as HTMLElement;
        const reachedBottom = listbox.scrollHeight - listbox.scrollTop - listbox.clientHeight < LOAD_MORE_THRESHOLD;
        if (reachedBottom && hasNextPage && !isFetchingNextPage) {
            void fetchNextPage();
        }
    };

    const searchIconColor = pageSettings?.themeType === 'dark' ? '#111111' : '#ffffff';

    const renderInput = (params: AutocompleteRenderInputParams) => (
        <Paper ref={params.InputProps.ref} elevation={3} sx={{ display: 'flex', width: '100%', alignItems: 'stretch' }}>
            <InputBase
                sx={{ flex: 1, pl: 1 }}
                placeholder='Search by login or display name...'
                inputProps={params.inputProps}
                endAdornment={
                    <Stack direction='row' alignItems='center' spacing={0.5} sx={{ pr: 0.5 }}>
                        {inputValue && (
                            <IconButton size='small' onClick={handleClear} aria-label='Clear search'>
                                <ClearIcon fontSize='small' />
                            </IconButton>
                        )}
                    </Stack>
                }
            />
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    bgcolor: 'secondary.main',
                    borderRadius: '0 4px 4px 0',
                    p: 1
                }}>
                <SearchIcon sx={{ color: searchIconColor }} />
            </Box>
        </Paper>
    );

    return (
        <Box>
            <SearchListContainer
                searchContainer={[
                    <Box
                        key='access-control-search'
                        sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' } }}>
                        <Autocomplete
                            sx={{ flex: 2, mr: { md: 1 }, mb: { xs: 2, md: 0 } }}
                            options={users}
                            value={selected}
                            onChange={handleSelect}
                            inputValue={inputValue}
                            onInputChange={handleInputChange}
                            getOptionLabel={option => option.user.loginName}
                            isOptionEqualToValue={(option, value) =>
                                option.user.loginName === value.user.loginName &&
                                option.user.provider === value.user.provider
                            }
                            filterOptions={x => x}
                            autoHighlight
                            clearOnBlur={false}
                            handleHomeEndKeys
                            forcePopupIcon={false}
                            loading={listLoading}
                            loadingText='Searching…'
                            noOptionsText='No users matched the current filters.'
                            ListboxProps={{ onScroll: loadMoreOnScroll }}
                            renderInput={renderInput}
                            renderOption={(props, option) => {
                                const { user } = option;
                                return (
                                    <Box
                                        component='li'
                                        {...props}
                                        key={`${user.provider}/${user.loginName}`}
                                        sx={{ display: 'flex', gap: 0.6, alignItems: 'center' }}>
                                        <Avatar variant='rounded' src={user.avatarUrl} sx={{ width: 32, height: 32 }} />
                                        <Box sx={{ minWidth: 0, flex: 1 }}>
                                            <Typography variant='body2' noWrap>
                                                {user.loginName}
                                            </Typography>
                                            <Typography variant='caption' color='text.secondary' noWrap>
                                                {user.fullName || '—'}
                                            </Typography>
                                        </Box>
                                        {user.role ? (
                                            <Chip
                                                icon={roleIcon(user.role)}
                                                label={user.role || 'none'}
                                                size='small'
                                                color='default'
                                                variant='outlined'
                                            />
                                        ) : null}
                                    </Box>
                                );
                            }}
                        />
                        <Paper elevation={3} sx={{ flex: 1, display: 'flex' }}>
                            <Select
                                value={roleFilter}
                                onChange={e => setRoleFilter(e.target.value)}
                                displayEmpty
                                // Without this, MUI's default 'outlined' variant makes Select pass a
                                // `notched` prop to the custom `input` below; InputBase doesn't consume
                                // it and it leaks onto the DOM as an invalid boolean attribute. The border
                                // here comes from the wrapping Paper, not from outlined chrome anyway.
                                variant='standard'
                                input={<InputBase sx={{ flex: 1, pl: 1 }} />}>
                                {ROLE_FILTER_OPTIONS.map(o => (
                                    <MenuItem key={o.value} value={o.value}>
                                        {o.label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </Paper>
                    </Box>
                ]}
                listContainer={null}
                loading={listLoading}
            />
            <Box
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 2,
                    minHeight: { xs: 360, md: 'calc(100vh - 220px)' }
                }}>
                {error && <Alert severity='error'>{formatError(error as Error | Partial<ErrorResponse>)}</Alert>}

                {selected ? (
                    <AccessControlDetails
                        key={`${selected.user.provider}/${selected.user.loginName}`}
                        entry={selected}
                        onDirtyChange={setDirty}
                    />
                ) : loginParam && !isFetching ? (
                    <Alert severity='info'>No user found for “{loginParam}”.</Alert>
                ) : (
                    <Paper
                        variant='outlined'
                        sx={{
                            flex: 1,
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            textAlign: 'center',
                            gap: 1,
                            p: 4,
                            color: 'text.secondary',
                            borderStyle: 'dashed'
                        }}>
                        <PersonIcon sx={{ fontSize: 56, opacity: 0.3 }} />
                        <Typography>Search for a user and select one to view their details.</Typography>
                    </Paper>
                )}
            </Box>

            <Dialog open={pendingSwitch !== null} onClose={() => setPendingSwitch(null)} maxWidth='xs' fullWidth>
                <DialogTitle>Discard unsaved changes?</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Unsaved role and permission changes for {selected?.user.loginName} will be lost.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setPendingSwitch(null)}>Keep editing</Button>
                    <Button onClick={handleDiscard} color='error'>
                        Discard
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
