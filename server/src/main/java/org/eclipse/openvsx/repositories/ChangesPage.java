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

package org.eclipse.openvsx.repositories;

import java.util.List;

import org.eclipse.openvsx.json.ChangeEntryJson;
import org.eclipse.openvsx.util.ChangesCursor;

/**
 * One page of the registry changes feed.
 * <p>
 * Deliberately not a {@link org.springframework.data.domain.Page}: paging through the feed is by position
 * rather than by offset, and a page carries no total. Counting the entries matching a request means
 * counting an append-only log that only grows, on every request, while what a consumer following the feed
 * actually needs to know is whether to ask again straight away -- which is what {@link #hasMore()} says.
 *
 * @param changes the entries, oldest first
 * @param nextCursor where to resume, i.e. the position of the last entry in {@link #changes()}. Falls back
 *        to the position the request was made from when the page is empty, so that a consumer always has
 *        somewhere to resume from, and is {@code null} only when an empty page was requested without one.
 * @param hasMore whether more entries match the request beyond this page
 */
public record ChangesPage(List<ChangeEntryJson> changes, ChangesCursor nextCursor, boolean hasMore) {}
