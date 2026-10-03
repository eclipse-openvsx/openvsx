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
package org.eclipse.openvsx;

import org.mockito.Mockito;

import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.settings.ExtensionSizeLimitService;

public final class TestSizeLimits {

    private TestSizeLimits() {
    }

    /**
     * A size-limit service behaving like a registry with no admin override and no per-namespace
     * overrides: every limit is the configured {@code ovsx.publishing.max-content-size} default.
     */
    public static ExtensionSizeLimitService atConfigDefault() {
        var defaultSize = new PublishingConfig().getMaxContentSize();
        var limits = Mockito.mock(ExtensionSizeLimitService.class);
        // Lenient: this is a shared fixture, and no single test exercises every accessor.
        Mockito.lenient().when(limits.getCeiling()).thenReturn(defaultSize);
        Mockito.lenient().when(limits.getDefaultLimit()).thenReturn(defaultSize);
        Mockito.lenient()
                .when(limits.resolveLimit(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(defaultSize);
        return limits;
    }
}
