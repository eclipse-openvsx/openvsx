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
 * What could be read about one cache.
 * <p>
 * A cache belongs to exactly one manager, and the two together are what name it: cache names are
 * unique only within a manager, so two managers may each hold a different cache called the same
 * thing. Registering one cache instance with two managers is a wiring bug - it would show up twice
 * and clearing it through one would silently empty the other - and {@code CacheInfoService} logs it
 * rather than presenting it as a cache with two ways in.
 * <p>
 * Every measurement is nullable because what is readable depends on the implementation behind the
 * cache: a Redis-backed cache cannot report its entry count, statistics are only counted where the
 * cache was configured to record them, and a rate is left out until something has been asked of the
 * cache. A null means "not available here", which is a different statement from zero.
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
