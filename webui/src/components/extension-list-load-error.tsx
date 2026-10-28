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

import { styled } from '@mui/material/styles';
import { FunctionComponent } from 'react';
import { ListLoadError, ListLoadErrorProps } from './list-load-error';

// With `showDivider`, a hairline fading out at both ends sets the notice apart from the cards above it.
const Root = styled('div', { shouldForwardProp: prop => prop !== 'showDivider' })<{ showDivider?: boolean }>(
    ({ theme, showDivider }) => ({
        padding: '2rem 0 2.5rem',
        ...(showDivider && {
            '&::before': {
                content: '""',
                display: 'block',
                height: '1px',
                marginBottom: '1.5rem',
                background: `linear-gradient(to right, transparent, ${theme.palette.border2} 25%, ${theme.palette.border2} 75%, transparent)`
            }
        })
    })
);

const Notice = styled(ListLoadError)({
    justifyContent: 'center',
    padding: '2.5rem 1rem 0',
    textAlign: 'center'
});

/** {@link ListLoadError} laid out to end, or stand in for, the extension results. */
export const ExtensionListLoadError: FunctionComponent<ExtensionListLoadErrorProps> = ({ showDivider, ...props }) => (
    <Root showDivider={showDivider}>
        <Notice {...props} />
    </Root>
);

export interface ExtensionListLoadErrorProps extends Omit<ListLoadErrorProps, 'className'> {
    /** Rule the notice off from cards shown above it. */
    showDivider?: boolean;
}
