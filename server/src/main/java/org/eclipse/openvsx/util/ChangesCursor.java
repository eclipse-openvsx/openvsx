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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * A position in the registry changes feed: the sort key of the last entry a consumer has processed.
 * <p>
 * Carries the transition instant <em>and</em> the id of the entry, which is the whole key the feed is
 * ordered by. Both are needed to resume: transitions written by one batch operation share an instant, so
 * a position expressed as an instant alone cannot say whether the entries carrying it have been processed.
 * Resuming after such a position would skip the rest of them, resuming at it would report them again.
 * <p>
 * Handed to consumers as an opaque string so that they neither depend on the ordering nor have to
 * combine two request parameters correctly. The encoding is deliberately plain rather than signed or
 * encrypted: it reveals nothing that the feed does not report anyway, and a cursor made up by a consumer
 * is no more dangerous than the equivalent {@code since} value.
 */
public record ChangesCursor(LocalDateTime changedAt, long id) {

    /**
     * Separates the two parts. Not a character an ISO-8601 date and time can contain, so the timestamp
     * cannot be confused with the id however many optional components it carries.
     */
    private static final String SEPARATOR = "_";

    /**
     * The opaque form to hand to a consumer.
     */
    public String encode() {
        var plain = changedAt + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads back a cursor produced by {@link #encode()}.
     *
     * @throws IllegalArgumentException if the value was not produced by {@link #encode()}, so that a
     *         hand-edited or truncated cursor is rejected rather than silently resuming from somewhere
     *         else in the feed
     */
    public static ChangesCursor decode(String value) {
        String plain;
        try {
            plain = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exc) {
            throw new IllegalArgumentException("not a base64url value: " + value, exc);
        }

        var separator = plain.lastIndexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("missing the '" + SEPARATOR + "' separator: " + value);
        }

        try {
            return new ChangesCursor(
                    LocalDateTime.parse(plain.substring(0, separator)),
                    Long.parseLong(plain.substring(separator + 1)));
        } catch (DateTimeParseException | NumberFormatException exc) {
            throw new IllegalArgumentException("not a position in the changes feed: " + value, exc);
        }
    }
}
