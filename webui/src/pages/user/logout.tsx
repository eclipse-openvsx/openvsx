/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */

import { PropsWithChildren, useContext, forwardRef } from 'react';
import { MainContext } from '../../context';
import { useCsrfToken } from './use-csrf-token';

export const LogoutForm = forwardRef<HTMLFormElement, PropsWithChildren>(({ children }, ref) => {
    const { service } = useContext(MainContext);
    const { data: csrfToken } = useCsrfToken();

    return (
        <form ref={ref} method='post' action={service.getLogoutUrl()} style={{ display: 'contents' }}>
            {csrfToken ? <input name='_csrf' type='hidden' value={csrfToken.value} /> : null}
            {children}
        </form>
    );
});

LogoutForm.displayName = 'LogoutForm';
