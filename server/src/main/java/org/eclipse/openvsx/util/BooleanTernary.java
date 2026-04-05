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

package org.eclipse.openvsx.util;

public enum BooleanTernary {
    TRUE,
    FALSE,
    UNKNOWN;

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public static BooleanTernary ofBoolean(Boolean value) {
        if (value == null) {
            return BooleanTernary.UNKNOWN;
        } else if (value) {
            return BooleanTernary.TRUE;
        } else {
            return BooleanTernary.FALSE;
        }
    }
}
