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
package org.eclipse.openvsx.analytics;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * One bucket of an aggregated download series. {@code group} is the value of the requested
 * grouping dimension, or null when grouping by {@link DownloadSeriesGroupBy#NONE}.
 */
public record DownloadSeriesRow(Instant bucketStart, @Nullable String group, long count) {}
