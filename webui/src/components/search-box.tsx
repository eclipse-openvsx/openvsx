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

import { FormEvent, forwardRef, InputHTMLAttributes } from 'react';
import { Box, ButtonBase } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import SearchIcon from '@mui/icons-material/Search';
import { focusOutline, focusRing } from './page-primitives';
import { MONO_FONT } from '../default/theme';

const SearchBoxForm = styled('form')({
    maxWidth: '41.25rem',
    marginInline: 'auto'
});

const SearchBoxWrap = styled(Box)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '0.8125rem',
    backgroundColor: theme.palette.surface2,
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadiusCard,
    height: '3.25rem',
    paddingLeft: '1rem',
    paddingRight: '0.25rem',
    [theme.breakpoints.down('sm')]: {
        height: '3rem',
        paddingLeft: '0.875rem',
        gap: '0.625rem'
    },
    boxShadow: 'var(--shadow)',
    transition: 'border-color 0.2s ease, box-shadow 0.3s ease',
    '&:focus-within': focusRing(theme, `0 18px 70px -10px ${alpha(theme.palette.secondary.main, 0.45)}`)
}));

const SearchBoxSlash = styled('span')(({ theme }) => ({
    fontFamily: MONO_FONT,
    color: theme.palette.secondary.light,
    fontSize: '1.25rem',
    flexShrink: 0,
    userSelect: 'none'
}));

const SearchBoxInput = styled('input')(({ theme }) => ({
    flex: 1,
    height: '100%',
    border: 'none',
    outline: 'none',
    background: 'none',
    color: theme.palette.text.primary,
    fontSize: '0.9375rem',
    fontFamily: MONO_FONT,
    '&::placeholder': { color: theme.palette.text.disabled }
}));

const SearchBoxSubmitButton = styled(ButtonBase)(({ theme }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    height: '2.75rem',
    padding: '0 1rem',
    borderRadius: theme.shape.borderRadius,
    overflow: 'hidden',
    backgroundColor: theme.palette.secondary.main,
    color: theme.palette.secondary.contrastText,
    fontSize: '0.8125rem',
    fontWeight: 400,
    flexShrink: 0,
    transition: 'background 0.14s',
    [theme.breakpoints.down('sm')]: {
        height: '2.5rem',
        padding: '0 0.875rem'
    },
    '&:hover': { backgroundColor: theme.palette.secondary.dark },
    ...focusOutline(theme)
}));

const SearchBoxSubmitLabel = styled('span')(({ theme }) => ({
    [theme.breakpoints.down('sm')]: { display: 'none' }
}));

export interface SearchBoxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onSubmit'> {
    /** Called with the trimmed query when a non-blank search is submitted. */
    onSearch: (query: string) => void;
    viewTransitionName?: string;
}

/**
 * The large search field of the home hero and the 404 page. Holds no search state: input props pass
 * through, so callers can control it (`value`/`onChange`) or leave it uncontrolled (`defaultValue`).
 */
export const SearchBox = forwardRef<HTMLInputElement, SearchBoxProps>(function SearchBox(
    { onSearch, viewTransitionName, ...inputProps },
    ref
) {
    const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        const query = String(new FormData(e.currentTarget).get('q') ?? '').trim();
        if (query) onSearch(query);
    };

    return (
        <SearchBoxForm onSubmit={handleSubmit}>
            <SearchBoxWrap style={{ viewTransitionName }}>
                <SearchBoxSlash>/</SearchBoxSlash>
                <SearchBoxInput
                    ref={ref}
                    aria-label='Search extensions'
                    placeholder='search extensions…'
                    {...inputProps}
                    name='q'
                />
                <SearchBoxSubmitButton type='submit' aria-label='Search'>
                    <SearchIcon sx={{ fontSize: '1.125rem' }} />
                    <SearchBoxSubmitLabel>search</SearchBoxSubmitLabel>
                </SearchBoxSubmitButton>
            </SearchBoxWrap>
        </SearchBoxForm>
    );
});
