/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.json;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import static org.eclipse.openvsx.util.TargetPlatform.*;

@Schema(
    name = "VersionReference",
    description = "Essential metadata of an extension version"
)
public class VersionReferenceJson {

    @Schema(description = "URL to get the full metadata of this version")
    private String url;

    @Schema(
        description = "Map of file types (download, manifest, icon, readme, license, changelog) to their respective URLs"
    )
    private Map<String, String> files;

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

    @Schema(description = "Map of engine names to the respective version constraints")
    private Map<String, String> engines;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public void setFiles(Map<String, String> files) {
        this.files = files;
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

    public Map<String, String> getEngines() {
        return engines;
    }

    public void setEngines(Map<String, String> engines) {
        this.engines = engines;
    }
}
