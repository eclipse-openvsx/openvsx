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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * The site-wide banner: four rows behind one value, public only while its switch is on.
 * <p>
 * The message is Markdown, and the rules in {@link #validate} are a coarse first line only - they
 * refuse HTML tags and executable link destinations outright, which no banner needs. What actually
 * makes the message safe to render is the sanitizer at the point of rendering
 * ({@code SanitizedMarkdown} runs DOMPurify), and any consumer of the public endpoint has to
 * sanitize for itself.
 */
@Component
public class BannerSetting implements WritableSetting<BannerSetting.Banner>, PublicSetting {

    public static final String NAME = "banner";
    public static final String KEY_ENABLED = "banner-enabled";
    public static final String KEY_MESSAGE = "banner-message";
    public static final String KEY_SEVERITY = "banner-severity";
    public static final String KEY_DISMISS_ID = "banner-dismiss-id";

    static final int MAX_MESSAGE_LENGTH = 1000;

    private static final Banner DEFAULT = new Banner(false, "", "info", "");

    private static final Set<String> SEVERITIES = Set.of("info", "warning");
    private static final Pattern DISMISS_ID = Pattern.compile("[A-Za-z0-9-]{0,64}");
    /** A tag opener, so ordinary prose like "latency <2s" or "queue > 500" still passes. */
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z!?]");
    /**
     * URL schemes are case-insensitive. {@code Pattern.CASE_INSENSITIVE} on its own folds ASCII
     * only, so the match holds whatever the JVM's default locale is - Turkish lowercases {@code I}
     * to a dotless {@code i}.
     */
    private static final Pattern EXECUTABLE_LINK = Pattern
            .compile("]\\((javascript|vbscript|data):", Pattern.CASE_INSENSITIVE);

    /** {@code dismissId} is the token a client stores when it dismisses this banner. */
    public record Banner(boolean enabled, String message, String severity, String dismissId) {}

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Banner read(SettingRows stored) {
        return new Banner(
                stored.asBoolean(KEY_ENABLED, DEFAULT.enabled()),
                stored.asString(KEY_MESSAGE, DEFAULT.message()),
                stored.asString(KEY_SEVERITY, DEFAULT.severity()),
                stored.asString(KEY_DISMISS_ID, DEFAULT.dismissId()));
    }

    @Override
    public Banner merge(Banner current, SettingRows update) {
        var message = update.asString(KEY_MESSAGE, current.message());
        return new Banner(
                update.asBoolean(KEY_ENABLED, current.enabled()),
                message,
                update.asString(KEY_SEVERITY, current.severity()),
                dismissIdFor(current, message, update));
    }

    /**
     * Keeps the stored token unless the caller sends a new one, so correcting the message doesn't
     * bring the banner back for everyone who dismissed it. A banner appearing where there was none
     * is a new one to dismiss, whatever token the caller echoed back - hence the branch order.
     */
    private static String dismissIdFor(Banner current, String newMessage, SettingRows update) {
        if (current.message().isBlank() && !newMessage.isBlank()) {
            return UUID.randomUUID().toString();
        }

        var supplied = update.asString(KEY_DISMISS_ID, "");
        return supplied.isBlank() ? current.dismissId() : supplied;
    }

    /** Each rule is skipped while its own field is unchanged, so a stale value can't block an unrelated save. */
    @Override
    public void validate(Banner value, Banner current) {
        if (!value.severity().equals(current.severity()) && !SEVERITIES.contains(value.severity())) {
            throw WritableSetting.reject("Unsupported banner severity: " + value.severity());
        }
        if (!value.dismissId().equals(current.dismissId()) && !DISMISS_ID.matcher(value.dismissId()).matches()) {
            throw WritableSetting.reject("Banner dismiss id must be at most 64 letters, digits or dashes.");
        }
        if (!value.message().equals(current.message())) {
            validateMessage(value.message());
        }
    }

    private static void validateMessage(String message) {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw WritableSetting.reject("Banner message must be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        if (HTML_TAG.matcher(message).find()) {
            throw WritableSetting
                    .reject("Banner message may not contain HTML. Use Markdown, e.g. [label](https://example.org).");
        }
        if (EXECUTABLE_LINK.matcher(message).find()) {
            throw WritableSetting
                    .reject("Banner message may not link to a javascript:, vbscript: or data: destination.");
        }
    }

    @Override
    public Map<String, Object> toRows(Banner value) {
        var rows = new LinkedHashMap<String, Object>();
        rows.put(KEY_ENABLED, value.enabled());
        rows.put(KEY_MESSAGE, value.message());
        rows.put(KEY_SEVERITY, value.severity());
        rows.put(KEY_DISMISS_ID, value.dismissId());
        return rows;
    }

    /**
     * All four rows with the defaults filled in, so a published banner always has the same shape. A
     * banner drafted with the switch down isn't public yet, switch included.
     */
    @Override
    public Map<String, Object> publicView(SettingRows stored) {
        var banner = read(stored);
        return banner.enabled() ? toRows(banner) : Map.of();
    }

    /** The message body would blow the 512-character admin log column on its own. */
    @Override
    public String describe(String rowKey, Object value) {
        return KEY_MESSAGE.equals(rowKey) ? rowKey : WritableSetting.super.describe(rowKey, value);
    }
}
