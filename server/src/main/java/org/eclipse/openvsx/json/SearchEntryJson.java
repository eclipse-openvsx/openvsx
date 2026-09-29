/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "SearchEntry",
    description = "Summary of metadata of an extension"
)
@JsonInclude(Include.NON_NULL)
public class SearchEntryJson {

    @Schema(description = "URL to get the full metadata of the extension")
    @NotNull
    private String url;

    @Schema(
        description = "Map of file types (download, manifest, icon, readme, license, changelog) to their respective URLs"
    )
    @NotNull
    private Map<String, String> files;

    @Schema(description = "Name of the extension")
    @NotNull
    private String name;

    @Schema(description = "Namespace of the extension")
    @NotNull
    private String namespace;

    @Schema(description = "The latest published version")
    @NotNull
    private String version;

    @Schema(description = "Date and time when this version was published (ISO-8601)")
    @NotNull
    private String timestamp;

    @Schema(
        description = "The value 'true' means the publishing user is a privileged user or the publishing user is a member of the extension's namespace and the namespace has at least one owner."
    )
    @NotNull
    private Boolean verified;

    /**
     * @deprecated
     */
    @Schema(
        description = "Essential metadata of all available versions. Deprecated: only returns the last 100 versions. Use allVersionsUrl instead."
    )
    @Deprecated
    private List<VersionReferenceJson> allVersions;

    @Schema(description = "URL to get essential metadata of all available versions.")
    private String allVersionsUrl;

    @Schema(description = "Average rating")
    @Min(0)
    @Max(5)
    private Double averageRating;

    @Schema(description = "Number of reviews")
    @Min(0)
    private Long reviewCount;

    @Schema(description = "Number of downloads of the extension package")
    @Min(0)
    private int downloadCount;

    @Schema(description = "Name to be displayed in user interfaces")
    private String displayName;

    private String description;

    @Schema(description = "Indicates whether the extension is deprecated")
    private boolean deprecated;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public List<VersionReferenceJson> getAllVersions() {
        return allVersions;
    }

    public void setAllVersions(List<VersionReferenceJson> allVersions) {
        this.allVersions = allVersions;
    }

    public String getAllVersionsUrl() {
        return allVersionsUrl;
    }

    public void setAllVersionsUrl(String allVersionsUrl) {
        this.allVersionsUrl = allVersionsUrl;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
}
