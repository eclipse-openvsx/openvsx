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

    @JsonProperty("read-only")
    @Schema(description = "Blocks write operations while keeping browsing, search and downloads available")
    private @Nullable Boolean readOnly;

    @JsonProperty("banner-enabled")
    @Schema(description = "Whether the site banner is shown. A message can be drafted while this is off.")
    private @Nullable Boolean bannerEnabled;

    @JsonProperty("banner-message")
    @Schema(description = "Site banner text, rendered as Markdown")
    private @Nullable String bannerMessage;

    @JsonProperty("banner-severity")
    @Schema(description = "Site banner tone", allowableValues = { "info", "warning" })
    private @Nullable String bannerSeverity;

    @JsonProperty("banner-dismiss-id")
    @Schema(
        description = "Token a client stores when it dismisses the banner. It changes only when an admin asks for "
                + "the banner to be shown again, so correcting the message leaves dismissals in place."
    )
    private @Nullable String bannerDismissId;

    public @Nullable Boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(@Nullable Boolean readOnly) {
        this.readOnly = readOnly;
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
