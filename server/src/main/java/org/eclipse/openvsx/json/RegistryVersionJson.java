/******************************************************************************
 * Copyright (c) 2024 STMicroelectronics and others
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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

@Schema(
    name = "RegistryVersion",
    description = "Configuration of the registry service"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistryVersionJson extends ResultJson {
    public static RegistryVersionJson error(String message) {
        var result = new RegistryVersionJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Registry version")
    @NotNull
    private String version;

    @Schema(description = "Default maximum extension package size in bytes, when no override applies")
    private long maxExtensionSize;

    @Schema(
        description = "Largest extension package the registry can accept from any namespace: the default "
                + "raised by the highest configured override. A package above this is rejected whoever "
                + "publishes it; one below it may still exceed the limit for its own namespace."
    )
    private long maxExtensionSizeCeiling;

    @Schema(description = "Audience for trusted publishing on the registry, if feature enabled.")
    @Nullable
    private String trustedPublishingAudience;

    @Schema(description = "Whether download analytics are enabled and the analytics endpoints are available")
    private boolean analyticsEnabled;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getMaxExtensionSize() {
        return maxExtensionSize;
    }

    public void setMaxExtensionSize(long maxExtensionSize) {
        this.maxExtensionSize = maxExtensionSize;
    }

    public long getMaxExtensionSizeCeiling() {
        return maxExtensionSizeCeiling;
    }

    public void setMaxExtensionSizeCeiling(long maxExtensionSizeCeiling) {
        this.maxExtensionSizeCeiling = maxExtensionSizeCeiling;
    }

    public String getTrustedPublishingAudience() {
        return trustedPublishingAudience;
    }

    public void setTrustedPublishingAudience(String trustedPublishingAudience) {
        this.trustedPublishingAudience = trustedPublishingAudience;
    }

    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    public void setAnalyticsEnabled(boolean analyticsEnabled) {
        this.analyticsEnabled = analyticsEnabled;
    }
}
