/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.json;

import java.util.LinkedHashMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import org.apache.commons.lang3.StringUtils;

import static org.eclipse.openvsx.util.TargetPlatform.*;

@Schema(
    name = "QueryParam",
    description = "Metadata query parameter"
)
@JsonInclude(Include.NON_NULL)
public class QueryParamJson {

    @Schema(description = "Name of a namespace")
    private String namespaceName;

    @Schema(description = "Name of an extension")
    private String extensionName;

    @Schema(description = "Version of an extension")
    private String extensionVersion;

    @Schema(description = "Identifier in the format {namespace}.{extension}")
    private String extensionId;

    @Schema(description = "Universally unique identifier of an extension")
    private String extensionUuid;

    @Schema(description = "Universally unique identifier of a namespace")
    private String namespaceUuid;

    @Schema(description = "Whether to include all versions of an extension, ignored if extensionVersion is specified")
    private boolean includeAllVersions;

    @Pattern(regexp = NAMES_PARAM_REGEX, message = "field must be a supported target platform")
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

    @Schema(description = "Maximal number of entries to return", minimum = "0", defaultValue = "100")
    private Integer size;

    @Schema(
        description = "Number of entries to skip (usually a multiple of the page size)",
        minimum = "0",
        defaultValue = "0"
    )
    private Integer offset;

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public String getExtensionVersion() {
        return extensionVersion;
    }

    public void setExtensionVersion(String extensionVersion) {
        this.extensionVersion = extensionVersion;
    }

    public String getExtensionId() {
        return extensionId;
    }

    public void setExtensionId(String extensionId) {
        this.extensionId = extensionId;
    }

    public String getExtensionUuid() {
        return extensionUuid;
    }

    public void setExtensionUuid(String extensionUuid) {
        this.extensionUuid = extensionUuid;
    }

    public String getNamespaceUuid() {
        return namespaceUuid;
    }

    public void setNamespaceUuid(String namespaceUuid) {
        this.namespaceUuid = namespaceUuid;
    }

    public boolean isIncludeAllVersions() {
        return includeAllVersions;
    }

    public void setIncludeAllVersions(boolean includeAllVersions) {
        this.includeAllVersions = includeAllVersions;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public String[] toQueryParams() {
        var queryParams = new LinkedHashMap<String, String>();
        queryParams.put("namespaceName", namespaceName);
        queryParams.put("extensionName", extensionName);
        queryParams.put("extensionVersion", extensionVersion);
        queryParams.put("extensionId", extensionId);
        queryParams.put("extensionUuid", extensionUuid);
        queryParams.put("namespaceUuid", namespaceUuid);
        queryParams.put("targetPlatform", targetPlatform);
        queryParams.put("includeAllVersions", String.valueOf(includeAllVersions));
        if (offset != null) {
            queryParams.put("offset", String.valueOf(offset));
        }
        if (size != null) {
            queryParams.put("size", String.valueOf(size));
        }

        return queryParams.entrySet().stream()
                .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(String[]::new);
    }
}
