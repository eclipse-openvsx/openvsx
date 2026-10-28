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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The runtime settings an admin reads and writes. The JSON properties are camelCase like every
 * other DTO; {@code SettingsJsonMapper}, beside the settings, translates to and from the rows they
 * are stored under, so this type names no setting.
 * <p>
 * Every field is boxed: a null means the caller left the setting alone, so a client that knows
 * nothing about a setting cannot reset it by omitting it.
 */
@Schema(name = "Settings", description = "Runtime settings of the registry")
@JsonInclude(Include.NON_NULL)
public class SettingsJson extends ResultJson {

    @Schema(description = "Blocks write operations while keeping browsing, search and downloads available")
    private @Nullable Boolean readOnly;

    @Schema(
        description = "Default max extension package size in bytes, applied when no namespace/extension override exists"
    )
    private @Nullable Long maxExtensionSize;

    @Schema(
        description = "Read-only: the hard ceiling neither an override nor the default size can be raised past",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    private @Nullable Long maxOverrideSize;

    @Schema(description = "Whether the site banner is shown. A message can be drafted while this is off.")
    private @Nullable Boolean bannerEnabled;

    @Schema(
        description = "Site banner text, as Markdown. Untrusted input: sanitize it before rendering it as HTML."
    )
    private @Nullable String bannerMessage;

    @Schema(description = "Site banner tone", allowableValues = { "info", "warning" })
    private @Nullable String bannerSeverity;

    @Schema(
        description = "Token a client stores when it dismisses the banner. It changes only when an admin asks for "
                + "the banner to be shown again, so correcting the message leaves dismissals in place."
    )
    private @Nullable String bannerDismissId;

    @Schema(
        description = "Whether the web UI shows the weekly downloads card on extension pages. Off by default. "
                + "Absent where download analytics is not configured (ovsx.analytics.enabled), and then it "
                + "cannot be switched on. As in any update, absent in a request leaves it unchanged."
    )
    private @Nullable Boolean downloadAnalyticsVisible;

    /** Settings a deployment adds outside the registry's own, flattened into the document as camelCase properties. */
    private final Map<String, Object> additionalSettings = new LinkedHashMap<>();

    public @Nullable Boolean getReadOnly() {
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

    public @Nullable Boolean getBannerEnabled() {
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

    public @Nullable Boolean getDownloadAnalyticsVisible() {
        return downloadAnalyticsVisible;
    }

    public void setDownloadAnalyticsVisible(@Nullable Boolean downloadAnalyticsVisible) {
        this.downloadAnalyticsVisible = downloadAnalyticsVisible;
    }

    @JsonAnyGetter
    @Schema(hidden = true)
    public Map<String, Object> getAdditionalSettings() {
        return additionalSettings;
    }

    @JsonAnySetter
    public void setAdditionalSetting(String name, Object value) {
        additionalSettings.put(name, value);
    }
}
