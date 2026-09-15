/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.cdn;

import java.util.Collection;

/**
 * Sends a purge to one CDN. Providers differ in how they are addressed, not in what they are told:
 * the keys come from {@code org.eclipse.openvsx.web.SurrogateKey} and mean the same thing to each.
 */
public interface CdnPurgeClient {

    /**
     * Drops every cached response carrying one of {@code keys}. Throws if the CDN could not be told,
     * so that the caller can retry: a purge that is silently lost leaves stale responses served
     * until they expire on their own.
     */
    void purge(Collection<String> keys);

    /**
     * Drops everything this CDN holds for the registry. The blunt instrument, for when the CDN is
     * known to be stale and what it is stale about is not: every reader's next request goes to the
     * origin.
     */
    void purgeAll();
}
