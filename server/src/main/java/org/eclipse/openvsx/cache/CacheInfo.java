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
package org.eclipse.openvsx.cache;

import org.jspecify.annotations.Nullable;

/**
 * What could be read about one cache. A cache is identified by its manager and its name together,
 * because the same name can appear under more than one manager - {@code settings} is registered on
 * both the file and the local cache manager.
 * <p>
 * Every measurement is nullable because what is readable depends on the implementation behind the
 * cache: a Redis-backed cache reports neither size nor statistics, and statistics are only counted
 * where the cache was configured to record them. A null means "not available here", which is a
 * different statement from zero.
 */
public record CacheInfo(
        String manager,
        String name,
        String implementation,
        @Nullable Long entries,
        @Nullable Long hits,
        @Nullable Long misses,
        @Nullable Double hitRate,
        @Nullable Long evictions
) {}
