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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.settings.BannerSetting;

/**
 * The settings served anonymously to every visitor. A type of its own rather than a view of
 * {@link SettingsJson}, so an admin-only setting cannot reach the public endpoint by being added
 * there. Properties are absent while the banner is switched off.
 */
@Schema(name = "SiteSettings", description = "Settings of the registry meant for visitors")
@JsonInclude(Include.NON_NULL)
public class SiteSettingsJson extends ResultJson {

    @Schema(description = "Whether the site banner is shown")
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

    /** Reads the public rows into the payload; anything else is left out. */
    public static SiteSettingsJson of(Map<String, Object> rows) {
        var json = new SiteSettingsJson();
        json.bannerEnabled = rows.get(BannerSetting.KEY_ENABLED) instanceof Boolean value ? value : null;
        json.bannerMessage = rows.get(BannerSetting.KEY_MESSAGE) instanceof String value ? value : null;
        json.bannerSeverity = rows.get(BannerSetting.KEY_SEVERITY) instanceof String value ? value : null;
        json.bannerDismissId = rows.get(BannerSetting.KEY_DISMISS_ID) instanceof String value ? value : null;
        return json;
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
}
