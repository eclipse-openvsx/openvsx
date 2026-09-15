/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import { ChangeEvent, ForwardedRef, forwardRef, KeyboardEvent, useCallback, useId, useRef } from 'react';
import { IconButton, InputBase, InputBaseComponentProps, Box } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import { MONO_FONT } from '../default/theme';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Close';
import { focusRing, MonoSlash } from './page-primitives';

interface ExtensionSearchfieldProps {
    onSearchChanged: (s: string) => void;
    onSearchSubmit?: (s: string) => void;
    searchQuery?: string;
    placeholder: string;
    hideIconButton?: boolean;
    error?: boolean;
    autoFocus?: boolean;
    viewTransitionName?: string;
    inputProps?: InputBaseComponentProps;
}

const SearchWrap = styled(Box, {
    shouldForwardProp: prop => prop !== 'hasError'
})<{ hasError?: boolean }>(({ theme, hasError }) => ({
    flex: 2,
    display: 'flex',
    alignItems: 'center',
    gap: '0.625rem',
    border: hasError ? '2px solid' : '1px solid',
    borderColor: hasError ? theme.palette.error.main : theme.palette.divider,
    borderRadius: theme.shape.borderRadiusCard,
    height: '2.8rem',
    padding: '0 0.8125rem',
    backgroundColor: alpha(theme.palette.surface2, 0.7),
    backdropFilter: 'blur(2px)',
    transition: 'border-color 0.18s, box-shadow 0.18s',
    '&:focus-within': focusRing(theme)
}));

const SearchInput = styled(InputBase)(({ theme }) => ({
    flex: 1,
    fontFamily: MONO_FONT,
    fontSize: '0.9375rem',
    color: theme.palette.text.primary,
    // iOS Safari zooms the viewport on focus when the field's font-size is < 16px;
    // keep it at 16px on mobile to suppress that (desktop stays compact at 15px).
    [theme.breakpoints.down('sm')]: { fontSize: '1rem' },
    '& input::placeholder': { color: theme.palette.text.primary, opacity: 0.7 },
    '& input::-webkit-search-cancel-button': { display: 'none' }
}));

export const ExtensionSearchfield = forwardRef(
    (props: ExtensionSearchfieldProps, ref: ForwardedRef<HTMLInputElement>) => {
        const inputRef = useRef<HTMLInputElement | null>(null);
        const inputId = useId();

        // Keep the forwarded ref and the internal ref (used by the clear button) in sync.
        const setInputRef = useCallback(
            (node: HTMLInputElement | null) => {
                inputRef.current = node;
                if (typeof ref === 'function') {
                    ref(node);
                } else if (ref) {
                    ref.current = node;
                }
            },
            [ref]
        );

        const handleSearchChange = (event: ChangeEvent<HTMLInputElement>) => {
            props.onSearchChanged(event.target.value);
        };

        const handleSearchButtonClick = () => {
            if (props.onSearchSubmit) {
                props.onSearchSubmit(props.searchQuery ?? '');
            }
        };

        const handleClear = () => {
            props.onSearchChanged('');
            inputRef.current?.focus();
        };

        // Merged into inputProps because InputBase spreads inputProps after its own
        // onKeyDown, so a caller-supplied handler would silently replace Enter-submit.
        const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
            props.inputProps?.onKeyDown?.(event);
            if (event.key === 'Enter' && props.onSearchSubmit && !event.defaultPrevented) {
                props.onSearchSubmit(props.searchQuery ?? '');
            }
        };

        return (
            <SearchWrap
                hasError={props.error}
                style={props.viewTransitionName ? { viewTransitionName: props.viewTransitionName } : undefined}>
                <MonoSlash>/</MonoSlash>
                <SearchInput
                    inputRef={setInputRef}
                    autoFocus={props.autoFocus ?? false}
                    value={props.searchQuery}
                    onChange={handleSearchChange}
                    placeholder={props.placeholder}
                    id={inputId}
                    type='search'
                    inputMode='search'
                    inputProps={{ ...props.inputProps, onKeyDown: handleKeyDown }}
                />
                <label htmlFor={inputId} className='visually-hidden'>
                    Search for Name, Tags or Description
                </label>
                {props.searchQuery && (
                    <IconButton
                        aria-label='Clear search'
                        onClick={handleClear}
                        size='small'
                        sx={{
                            color: 'text.disabled',
                            p: '0.25rem',
                            flexShrink: 0,
                            transition: 'color 0.14s',
                            '&:hover': { color: 'text.primary' }
                        }}>
                        <ClearIcon sx={{ fontSize: '1.125rem' }} />
                    </IconButton>
                )}
                {!props.hideIconButton && (
                    <IconButton
                        color='primary'
                        aria-label='Search'
                        onClick={handleSearchButtonClick}
                        sx={{
                            bgcolor: 'secondary.main',
                            color: 'secondary.contrastText',
                            // sx multiplies by theme.shape.borderRadius
                            borderRadius: 1,
                            p: '0.375rem',
                            flexShrink: 0,
                            transition: 'background 0.14s',
                            '&:hover': { bgcolor: 'secondary.dark' }
                        }}>
                        <SearchIcon fontSize='small' />
                    </IconButton>
                )}
            </SearchWrap>
        );
    }
);

ExtensionSearchfield.displayName = 'ExtensionSearchfield';
