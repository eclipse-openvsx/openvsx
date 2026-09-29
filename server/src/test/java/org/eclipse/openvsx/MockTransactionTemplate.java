/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx;

import java.io.Serial;

import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class MockTransactionTemplate extends TransactionTemplate {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public <T> T execute(TransactionCallback<T> action) throws TransactionException {
        return action.doInTransaction(null);
    }

    @Override
    public void afterPropertiesSet() {
        // Method override to prevent IllegalArgumentException from being thrown
    }
}
