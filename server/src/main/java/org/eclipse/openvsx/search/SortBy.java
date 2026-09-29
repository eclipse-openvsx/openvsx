/******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
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
package org.eclipse.openvsx.search;

public class SortBy {
    public static final String RELEVANCE = "relevance";
    public static final String TIMESTAMP = "timestamp";
    public static final String RATING = "rating";
    public static final String DOWNLOADS = "downloadCount";
    public static final String OPTIONS = String
            .format("'%s', '%s', '%s' or '%s'", RELEVANCE, TIMESTAMP, RATING, DOWNLOADS);
}
