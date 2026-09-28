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
package org.eclipse.openvsx.settings;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.util.ErrorResultException;

/**
 * Checks a settings update before it is stored, so the public endpoint only ever serves values a
 * reader can use - a severity the UI can render, a dismiss token a client can compare.
 * <p>
 * The banner message is Markdown, and the message rules are a coarse first line only - they refuse
 * HTML tags and executable link destinations outright, which no banner needs. What actually makes
 * the message safe to render is the sanitizer at the point of rendering ({@code SanitizedMarkdown}
 * runs DOMPurify), and any consumer of the public endpoint has to sanitize for itself.
 */
final class SettingsValidator {

    static final int MAX_MESSAGE_LENGTH = 1000;

    private static final Set<String> SEVERITIES = Set.of("info", "warning");
    private static final Pattern DISMISS_ID = Pattern.compile("[A-Za-z0-9-]{0,64}");
    /** A tag opener, so ordinary prose like "latency <2s" or "queue > 500" still passes. */
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z!?]");
    private static final List<String> EXECUTABLE_LINKS = List.of("](javascript:", "](vbscript:", "](data:");
    /** Whitespace and control characters, which split a scheme without breaking it for a browser. */
    private static final Pattern IGNORED_IN_LINK = Pattern.compile("[\\s\\p{Cntrl}\\u200B-\\u200D\\uFEFF]");

    private SettingsValidator() {
    }

    /** A null is a setting the caller isn't touching, so it has nothing to check. */
    static void validate(SettingsJson settings) {
        var severity = settings.getBannerSeverity();
        if (severity != null && !SEVERITIES.contains(severity)) {
            throw badRequest("Unsupported banner severity: " + severity);
        }

        var dismissId = settings.getBannerDismissId();
        if (dismissId != null && !DISMISS_ID.matcher(dismissId).matches()) {
            throw badRequest("Banner dismiss id must be at most 64 letters, digits or dashes.");
        }

        var message = settings.getBannerMessage();
        if (message != null) {
            validateBannerMessage(message);
        }
    }

    private static void validateBannerMessage(String message) {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw badRequest("Banner message must be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        if (HTML_TAG.matcher(message).find()) {
            throw badRequest("Banner message may not contain HTML. Use Markdown, e.g. [label](https://example.org).");
        }

        var collapsed = IGNORED_IN_LINK.matcher(message).replaceAll("").toLowerCase();
        for (var link : EXECUTABLE_LINKS) {
            if (collapsed.contains(link)) {
                throw badRequest("Banner message may not link to '" + link.substring(2) + "'.");
            }
        }
    }

    private static ErrorResultException badRequest(String message) {
        return new ErrorResultException(message, HttpStatus.BAD_REQUEST);
    }
}
