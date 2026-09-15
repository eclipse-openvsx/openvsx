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

import { FunctionComponent, useEffect, useRef } from 'react';
import { SvgIconProps } from '@mui/material';
import { Pill } from './pill';

export interface CategoryPillProps {
    label: string;
    icon: FunctionComponent<SvgIconProps>;
    isSelected?: boolean;
    onClick: () => void;
}

export const CategoryPill: FunctionComponent<CategoryPillProps> = ({ label, icon: Icon, isSelected, onClick }) => {
    const ref = useRef<HTMLButtonElement>(null);

    // Keep the selected pill visible when the row overflows (deep links, home tiles).
    useEffect(() => {
        if (isSelected) {
            ref.current?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
        }
    }, [isSelected]);

    return (
        <Pill ref={ref} isSelected={isSelected} aria-pressed={!!isSelected} onClick={onClick}>
            <Icon sx={{ fontSize: '1rem', flexShrink: 0, color: isSelected ? 'inherit' : 'secondary.main' }} />
            {label}
        </Pill>
    );
};
