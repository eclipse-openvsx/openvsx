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

import { ButtonBase } from '@mui/material';
import { alpha, styled } from '@mui/material/styles';
import { accentHover, focusOutline, glassSurface } from './page-primitives';

/**
 * Clickable pill primitive: a translucent glass surface flipping to an accent fill when
 * selected — the same treatment as the extension detail page's sticky tabs.
 */
export const Pill = styled(ButtonBase, {
    shouldForwardProp: prop => prop !== 'isSelected'
})<{ isSelected?: boolean }>(({ theme, isSelected }) => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.4375rem',
    flexShrink: 0,
    overflow: 'hidden',
    ...glassSurface(theme),
    ...(isSelected ? { backgroundColor: alpha(theme.palette.secondary.main, 0.7) } : {}),
    border: `1px solid ${isSelected ? theme.palette.secondary.main : theme.palette.divider}`,
    color: isSelected ? theme.palette.secondary.contrastText : theme.palette.text.secondary,
    fontSize: '0.8125rem',
    fontWeight: isSelected ? 600 : 500,
    padding: '0.4375rem 0.8125rem',
    borderRadius: theme.shape.borderRadiusPill,
    whiteSpace: 'nowrap',
    fontFamily: 'inherit',
    transition: 'border-color 0.14s, color 0.14s, background 0.14s',
    ...(isSelected ? {} : accentHover(theme)),
    ...focusOutline(theme)
}));
