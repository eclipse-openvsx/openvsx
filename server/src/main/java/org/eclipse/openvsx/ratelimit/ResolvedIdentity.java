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
package org.eclipse.openvsx.ratelimit;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.Tier;

public record ResolvedIdentity(
        @NonNull String ipAddress,
        @NonNull String cacheKey,
        @Nullable Customer customer,
        @Nullable Tier freeTier,
        @Nullable Tier safetyTier
) {
    public boolean isCustomer() {
        return customer != null;
    }

    public @NonNull Customer getCustomer() {
        if (isCustomer()) {
            return customer;
        } else {
            throw new RuntimeException("No customer associated with this identity.");
        }
    }
}
