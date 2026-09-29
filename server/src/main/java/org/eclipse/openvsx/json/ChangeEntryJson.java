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
import io.swagger.v3.oas.annotations.media.Schema;

import org.eclipse.openvsx.entities.ExtensionVersionState;

import static org.eclipse.openvsx.util.TargetPlatform.*;

@Schema(
    name = "ChangeEntry",
    description = "State of a single extension version in the registry changes feed"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangeEntryJson {

    // The names of the ExtensionVersionState constants, spelled out because the @Schema below needs
    // compile-time constants and an enum name is not one. ChangeEntryJsonTest keeps the two in step.

    /**
     * @see ExtensionVersionState#ACTIVE
     */
    public static final String STATE_ACTIVE = "ACTIVE";

    /**
     * @see ExtensionVersionState#INACTIVE
     */
    public static final String STATE_INACTIVE = "INACTIVE";

    /**
     * @see ExtensionVersionState#REMOVED
     */
    public static final String STATE_REMOVED = "REMOVED";

    @Schema(description = "Namespace of the extension")
    private String namespace;

    @Schema(description = "Name of the extension")
    private String name;

    @Schema(description = "Version of the extension")
    private String version;

    @Schema(
        description = "Name of the target platform",
        allowableValues = {
            NAME_WIN32_X64,
            NAME_WIN32_IA32,
            NAME_WIN32_ARM64,
            NAME_LINUX_X64,
            NAME_LINUX_ARM64,
            NAME_LINUX_ARMHF,
            NAME_ALPINE_X64,
            NAME_ALPINE_ARM64,
            NAME_DARWIN_X64,
            NAME_DARWIN_ARM64,
            NAME_WEB,
            NAME_UNIVERSAL
        }
    )
    private String targetPlatform;

    @Schema(
        description = "State this extension version transitioned into",
        allowableValues = { STATE_ACTIVE, STATE_INACTIVE, STATE_REMOVED }
    )
    private String state;

    @Schema(description = "Date and time when this version was published (ISO-8601)")
    private String timestamp;

    @Schema(
        description = "Date and time the transition this entry reports happened (ISO-8601). The feed is "
                + "ordered by this value."
    )
    private String lastUpdated;

    @Schema(
        description = "URL to get the full metadata of this version. Versions that are not active are "
                + "not served by that endpoint anymore, so this only resolves for entries reporting a "
                + "transition into the ACTIVE state, and only until the version transitions again. It "
                + "never resolves for a version that has been purged, as there is no metadata left."
    )
    private String url;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
