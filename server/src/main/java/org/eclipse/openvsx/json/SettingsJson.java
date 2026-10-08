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
package org.eclipse.openvsx.json;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The runtime settings an admin reads and writes. Property names are the keys the settings are
 * stored under and the public settings endpoint serves, so both speak the same vocabulary.
 * <p>
 * Every field is boxed: a null means the caller left the setting alone, so a client that knows
 * nothing about a setting cannot reset it by omitting it.
 */
@Schema(name = "Settings", description = "Runtime settings of the registry")
@JsonInclude(Include.NON_NULL)
public class SettingsJson extends ResultJson {

    public static final String READ_ONLY = "read-only";
    public static final String MAX_EXTENSION_SIZE = "max-extension-size";
    public static final String MAX_OVERRIDE_SIZE = "max-override-size";
    public static final String BANNER_ENABLED = "banner-enabled";
    public static final String BANNER_MESSAGE = "banner-message";
    public static final String BANNER_SEVERITY = "banner-severity";
    public static final String BANNER_DISMISS_ID = "banner-dismiss-id";

    @JsonProperty(READ_ONLY)
    @Schema(description = "Blocks write operations while keeping browsing, search and downloads available")
    private @Nullable Boolean readOnly;

    @JsonProperty(MAX_EXTENSION_SIZE)
    @Schema(
        description = "Default max extension package size in bytes, applied when no namespace/extension override exists"
    )
    private @Nullable Long maxExtensionSize;

    @JsonProperty(MAX_OVERRIDE_SIZE)
    @Schema(
        description = "Read-only: the hard ceiling neither an override nor the default size can be raised past",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    private @Nullable Long maxOverrideSize;

    @JsonProperty(BANNER_ENABLED)
    @Schema(description = "Whether the site banner is shown. A message can be drafted while this is off.")
    private @Nullable Boolean bannerEnabled;

    @JsonProperty(BANNER_MESSAGE)
    @Schema(
        description = "Site banner text, as Markdown. Untrusted input: sanitize it before rendering it as HTML."
    )
    private @Nullable String bannerMessage;

    @JsonProperty(BANNER_SEVERITY)
    @Schema(description = "Site banner tone", allowableValues = { "info", "warning" })
    private @Nullable String bannerSeverity;

    @JsonProperty(BANNER_DISMISS_ID)
    @Schema(
        description = "Token a client stores when it dismisses the banner. It changes only when an admin asks for "
                + "the banner to be shown again, so correcting the message leaves dismissals in place."
    )
    private @Nullable String bannerDismissId;

    /** Reads the rows a setting reports back into the payload; anything unknown to this DTO is left out. */
    public static SettingsJson of(Map<String, Object> rows) {
        var json = new SettingsJson();
        json.readOnly = bool(rows, READ_ONLY);
        json.maxExtensionSize = rows.get(MAX_EXTENSION_SIZE) instanceof Number value ? value.longValue() : null;
        json.bannerEnabled = bool(rows, BANNER_ENABLED);
        json.bannerMessage = string(rows, BANNER_MESSAGE);
        json.bannerSeverity = string(rows, BANNER_SEVERITY);
        json.bannerDismissId = string(rows, BANNER_DISMISS_ID);
        return json;
    }

    /** The rows these settings are stored under. A null field is one the caller isn't touching, so it is left out. */
    public Map<String, Object> toRows() {
        var rows = new LinkedHashMap<String, Object>();
        putIfPresent(rows, READ_ONLY, readOnly);
        putIfPresent(rows, MAX_EXTENSION_SIZE, maxExtensionSize);
        putIfPresent(rows, BANNER_ENABLED, bannerEnabled);
        putIfPresent(rows, BANNER_MESSAGE, bannerMessage);
        putIfPresent(rows, BANNER_SEVERITY, bannerSeverity);
        putIfPresent(rows, BANNER_DISMISS_ID, bannerDismissId);
        return rows;
    }

    private static void putIfPresent(Map<String, Object> rows, String key, @Nullable Object value) {
        if (value != null) {
            rows.put(key, value);
        }
    }

    private static @Nullable Boolean bool(Map<String, Object> rows, String key) {
        return rows.get(key) instanceof Boolean value ? value : null;
    }

    private static @Nullable String string(Map<String, Object> rows, String key) {
        return rows.get(key) instanceof String value ? value : null;
    }

    public @Nullable Boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(@Nullable Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public @Nullable Long getMaxExtensionSize() {
        return maxExtensionSize;
    }

    public void setMaxExtensionSize(@Nullable Long maxExtensionSize) {
        this.maxExtensionSize = maxExtensionSize;
    }

    public @Nullable Long getMaxOverrideSize() {
        return maxOverrideSize;
    }

    public void setMaxOverrideSize(@Nullable Long maxOverrideSize) {
        this.maxOverrideSize = maxOverrideSize;
    }

    public @Nullable Boolean isBannerEnabled() {
        return bannerEnabled;
    }

    public void setBannerEnabled(@Nullable Boolean bannerEnabled) {
        this.bannerEnabled = bannerEnabled;
    }

    public @Nullable String getBannerMessage() {
        return bannerMessage;
    }

    public void setBannerMessage(@Nullable String bannerMessage) {
        this.bannerMessage = bannerMessage;
    }

    public @Nullable String getBannerSeverity() {
        return bannerSeverity;
    }

    public void setBannerSeverity(@Nullable String bannerSeverity) {
        this.bannerSeverity = bannerSeverity;
    }

    public @Nullable String getBannerDismissId() {
        return bannerDismissId;
    }

    public void setBannerDismissId(@Nullable String bannerDismissId) {
        this.bannerDismissId = bannerDismissId;
    }
}
