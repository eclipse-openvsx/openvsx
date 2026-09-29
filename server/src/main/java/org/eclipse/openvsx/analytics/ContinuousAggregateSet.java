/******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
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

import java.util.List;

/**
 * Continuous aggregates the backfill must refresh. Each bean of this type contributes the names it
 * owns, and the refresher collects them all.
 */
@FunctionalInterface
public interface ContinuousAggregateSet {

    /** Continuous aggregate names; trusted identifiers, validated by the refresher. */
    List<String> aggregateNames();
}
