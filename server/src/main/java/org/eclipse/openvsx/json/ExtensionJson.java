/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static org.eclipse.openvsx.util.TargetPlatform.*;

@Schema(
    name = "Extension",
    description = "Metadata of an extension"
)
@JsonInclude(Include.NON_NULL)
public class ExtensionJson extends ResultJson {

    public static ExtensionJson error(String message) {
        var info = new ExtensionJson();
        info.setError(message);
        return info;
    }

    @Schema(description = "URL to get metadata of the extension's namespace")
    @NotNull
    private String namespaceUrl;

    @Schema(description = "URL to get the list of reviews of this extension")
    @NotNull
    private String reviewsUrl;

    @Schema(
        description = "Map of file types (download, manifest, icon, readme, license, changelog) to their respective URLs"
    )
    private Map<String, String> files;

    @Schema(
        description = "Size in bytes of the extension package - the file behind the 'download' entry of "
                + "'files' - for this version and target platform. Null where the size is not known, which "
                + "is the case for packages published before the registry started recording it and not yet "
                + "backfilled. This is the download size; the installed extension is larger, by whatever "
                + "the package's compression ratio happens to be."
    )
    private Long downloadSize;

    @Schema(description = "Name of the extension")
    @NotNull
    private String name;

    @Schema(description = "Namespace of the extension")
    @NotNull
    private String namespace;

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

    @Schema(description = "Selected version, or the latest version if none was specified")
    @NotNull
    private String version;

    @Schema(description = "Indicates whether this is a pre-release version")
    private Boolean preRelease;

    @Schema(description = "Data of the user who published this version")
    @NotNull
    private UserJson publishedBy;

    @Schema(description = "Indicates whether this version was published using trusted publishing")
    @NotNull
    private Boolean publishedWithTrustedPublishing;

    @Schema(hidden = true)
    private Boolean active;

    @Schema(hidden = true)
    private Boolean removed;

    @Schema(
        description = "Review/publishing status: published (active and visible to all), under_review (being reviewed), rejected (blocked)",
        allowableValues = { "published", "under_review", "rejected" }
    )
    private String reviewStatus;

    @Schema(description = "Message explaining the review status (e.g., why extension was quarantined or rejected)")
    private String reviewMessage;

    @Schema(
        description = "The value 'true' means the publishing user is a privileged user or the publishing user is a member of the extension's namespace and the namespace has at least one owner."
    )
    @NotNull
    private Boolean verified;

    /**
     * True when this version's latest scan recorded a namespace-ownership-check threat, i.e. the
     * namespace it was published to already exists in a referenced external gallery and is not
     * verified. Internal signal for the webui, not part of the documented public API.
     */
    @Schema(hidden = true)
    private Boolean namespaceOwnershipConflict;

    /**
     * @deprecated
     */
    @Schema(
        description = "Map of available versions to their metadata URLs. Deprecated: only returns the last 100 versions. Use allVersionsUrl instead."
    )
    @Deprecated
    private Map<String, String> allVersions;

    @Schema(description = "URL to get a map of available versions to their metadata URLs.")
    private String allVersionsUrl;

    @Schema(description = "Average rating")
    @Min(0)
    @Max(5)
    private Double averageRating;

    @Schema(description = "Number of downloads of the extension package")
    @Min(0)
    private Integer downloadCount;

    @Schema(description = "Number of reviews")
    @Min(0)
    private Long reviewCount;

    @Schema(description = "Available version aliases ('latest' or 'pre-release')")
    private List<String> versionAlias;

    @Schema(description = "Date and time when this version was published (ISO-8601)")
    @NotNull
    private String timestamp;

    @Schema(description = "Indicates whether this is a preview extension")
    private Boolean preview;

    @Schema(description = "Name to be displayed in user interfaces")
    private String displayName;

    @Schema(description = "Namespace name to be displayed in user interfaces")
    @NotNull
    private String namespaceDisplayName;

    private String description;

    @Schema(description = "Map of engine names to the respective version constraints")
    private Map<String, String> engines;

    private List<String> categories;

    @Schema(
        description = "A list that indicates where the extension should run in remote configurations. Values are \"ui\" (run locally), \"workspace\" (run on remote machine) and \"web\""
    )
    private List<String> extensionKind;

    private List<String> tags;

    @Schema(description = "License identifier")
    private String license;

    @Schema(description = "URL of the extension's homepage")
    private String homepage;

    @Schema(description = "URL of the extension's source repository")
    private String repository;

    @Schema(description = "URL to sponsor the extension")
    private String sponsorLink;

    @Schema(description = "URL of the extension's bug tracker")
    private String bugs;

    @Schema(
        description = "Markdown rendering engine to use in user interfaces",
        allowableValues = { "standard", "github" }
    )
    private String markdown;

    @Schema(description = "CSS color to use as background in user interfaces")
    private String galleryColor;

    @Schema(description = "Theme type for user interfaces", allowableValues = { "light", "dark" })
    private String galleryTheme;

    @Schema(description = "Languages the extension has been translated in")
    private List<String> localizedLanguages;

    @Schema(description = "URL of the extension's Q&A page")
    private String qna;

    @Schema(description = "List of badges to display in user interfaces")
    private List<BadgeJson> badges;

    @Schema(description = "List of dependencies to other extensions")
    private List<ExtensionReferenceJson> dependencies;

    @Schema(description = "List of extensions bundled with this extension")
    private List<ExtensionReferenceJson> bundledExtensions;

    @Schema(description = "Map of download links by target platform")
    private Map<String, String> downloads;

    @Schema(description = "Map of target platforms by extension version")
    private List<VersionTargetPlatformsJson> allTargetPlatformVersions;

    @Schema(description = "version metadata URL")
    private String url;

    @Schema(description = "Indicates whether the extension is deprecated")
    private boolean deprecated;

    @Schema(description = "Reference to extension that replaces this extension when it's deprecated")
    private ExtensionReplacementJson replacement;

    @Schema(description = "Whether to show downloads in user interfaces")
    private boolean downloadable;

    public String getNamespaceUrl() {
        return namespaceUrl;
    }

    public void setNamespaceUrl(String namespaceUrl) {
        this.namespaceUrl = namespaceUrl;
    }

    public String getReviewsUrl() {
        return reviewsUrl;
    }

    public void setReviewsUrl(String reviewsUrl) {
        this.reviewsUrl = reviewsUrl;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public void setFiles(Map<String, String> files) {
        this.files = files;
    }

    public Long getDownloadSize() {
        return downloadSize;
    }

    public void setDownloadSize(Long downloadSize) {
        this.downloadSize = downloadSize;
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

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Boolean getPreRelease() {
        return preRelease;
    }

    public void setPreRelease(Boolean preRelease) {
        this.preRelease = preRelease;
    }

    public UserJson getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UserJson publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Boolean getPublishedWithTrustedPublishing() {
        return publishedWithTrustedPublishing;
    }

    public void setPublishedWithTrustedPublishing(Boolean publishedWithTrustedPublishing) {
        this.publishedWithTrustedPublishing = publishedWithTrustedPublishing;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getRemoved() {
        return removed;
    }

    public void setRemoved(Boolean removed) {
        this.removed = removed;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewMessage() {
        return reviewMessage;
    }

    public void setReviewMessage(String reviewMessage) {
        this.reviewMessage = reviewMessage;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Boolean getNamespaceOwnershipConflict() {
        return namespaceOwnershipConflict;
    }

    public void setNamespaceOwnershipConflict(Boolean namespaceOwnershipConflict) {
        this.namespaceOwnershipConflict = namespaceOwnershipConflict;
    }

    public Map<String, String> getAllVersions() {
        return allVersions;
    }

    public void setAllVersions(Map<String, String> allVersions) {
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

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public List<String> getVersionAlias() {
        return versionAlias;
    }

    public void setVersionAlias(List<String> versionAlias) {
        this.versionAlias = versionAlias;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getPreview() {
        return preview;
    }

    public void setPreview(Boolean preview) {
        this.preview = preview;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNamespaceDisplayName() {
        return namespaceDisplayName;
    }

    public void setNamespaceDisplayName(String namespaceDisplayName) {
        this.namespaceDisplayName = namespaceDisplayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getEngines() {
        return engines;
    }

    public void setEngines(Map<String, String> engines) {
        this.engines = engines;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getExtensionKind() {
        return extensionKind;
    }

    public void setExtensionKind(List<String> extensionKind) {
        this.extensionKind = extensionKind;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getSponsorLink() {
        return sponsorLink;
    }

    public void setSponsorLink(String sponsorLink) {
        this.sponsorLink = sponsorLink;
    }

    public String getBugs() {
        return bugs;
    }

    public void setBugs(String bugs) {
        this.bugs = bugs;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String getGalleryColor() {
        return galleryColor;
    }

    public void setGalleryColor(String galleryColor) {
        this.galleryColor = galleryColor;
    }

    public String getGalleryTheme() {
        return galleryTheme;
    }

    public void setGalleryTheme(String galleryTheme) {
        this.galleryTheme = galleryTheme;
    }

    public List<String> getLocalizedLanguages() {
        return localizedLanguages;
    }

    public void setLocalizedLanguages(List<String> localizedLanguages) {
        this.localizedLanguages = localizedLanguages;
    }

    public String getQna() {
        return qna;
    }

    public void setQna(String qna) {
        this.qna = qna;
    }

    public List<BadgeJson> getBadges() {
        return badges;
    }

    public void setBadges(List<BadgeJson> badges) {
        this.badges = badges;
    }

    public List<ExtensionReferenceJson> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<ExtensionReferenceJson> dependencies) {
        this.dependencies = dependencies;
    }

    public List<ExtensionReferenceJson> getBundledExtensions() {
        return bundledExtensions;
    }

    public void setBundledExtensions(List<ExtensionReferenceJson> bundledExtensions) {
        this.bundledExtensions = bundledExtensions;
    }

    public Map<String, String> getDownloads() {
        return downloads;
    }

    public void setDownloads(Map<String, String> downloads) {
        this.downloads = downloads;
    }

    public List<VersionTargetPlatformsJson> getAllTargetPlatformVersions() {
        return allTargetPlatformVersions;
    }

    public void setAllTargetPlatformVersions(List<VersionTargetPlatformsJson> allTargetPlatformVersions) {
        this.allTargetPlatformVersions = allTargetPlatformVersions;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public ExtensionReplacementJson getReplacement() {
        return replacement;
    }

    public void setReplacement(ExtensionReplacementJson replacement) {
        this.replacement = replacement;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public void setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtensionJson that = (ExtensionJson) o;
        return Objects.equals(namespaceUrl, that.namespaceUrl)
                && Objects.equals(reviewsUrl, that.reviewsUrl)
                && Objects.equals(files, that.files)
                && Objects.equals(downloadSize, that.downloadSize)
                && Objects.equals(name, that.name)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(targetPlatform, that.targetPlatform)
                && Objects.equals(version, that.version)
                && Objects.equals(preRelease, that.preRelease)
                && Objects.equals(publishedBy, that.publishedBy)
                && Objects.equals(active, that.active)
                && Objects.equals(removed, that.removed)
                && Objects.equals(reviewStatus, that.reviewStatus)
                && Objects.equals(reviewMessage, that.reviewMessage)
                && Objects.equals(verified, that.verified)
                && Objects.equals(namespaceOwnershipConflict, that.namespaceOwnershipConflict)
                && Objects.equals(allVersions, that.allVersions)
                && Objects.equals(allVersionsUrl, that.allVersionsUrl)
                && Objects.equals(averageRating, that.averageRating)
                && Objects.equals(downloadCount, that.downloadCount)
                && Objects.equals(reviewCount, that.reviewCount)
                && Objects.equals(versionAlias, that.versionAlias)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(preview, that.preview)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(description, that.description)
                && Objects.equals(engines, that.engines)
                && Objects.equals(categories, that.categories)
                && Objects.equals(extensionKind, that.extensionKind)
                && Objects.equals(tags, that.tags)
                && Objects.equals(license, that.license)
                && Objects.equals(homepage, that.homepage)
                && Objects.equals(repository, that.repository)
                && Objects.equals(bugs, that.bugs)
                && Objects.equals(markdown, that.markdown)
                && Objects.equals(galleryColor, that.galleryColor)
                && Objects.equals(galleryTheme, that.galleryTheme)
                && Objects.equals(qna, that.qna)
                && Objects.equals(badges, that.badges)
                && Objects.equals(dependencies, that.dependencies)
                && Objects.equals(bundledExtensions, that.bundledExtensions)
                && Objects.equals(downloads, that.downloads)
                && Objects.equals(allTargetPlatformVersions, that.allTargetPlatformVersions)
                && Objects.equals(url, that.url)
                && Objects.equals(deprecated, that.deprecated)
                && Objects.equals(replacement, that.replacement)
                && Objects.equals(downloadable, that.downloadable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                namespaceUrl,
                reviewsUrl,
                files,
                downloadSize,
                name,
                namespace,
                targetPlatform,
                version,
                preRelease,
                publishedBy,
                active,
                removed,
                reviewStatus,
                reviewMessage,
                verified,
                namespaceOwnershipConflict,
                allVersions,
                allVersionsUrl,
                averageRating,
                downloadCount,
                reviewCount,
                versionAlias,
                timestamp,
                preview,
                displayName,
                description,
                engines,
                categories,
                extensionKind,
                tags,
                license,
                homepage,
                repository,
                bugs,
                markdown,
                galleryColor,
                galleryTheme,
                qna,
                badges,
                dependencies,
                bundledExtensions,
                downloads,
                allTargetPlatformVersions,
                url,
                deprecated,
                replacement,
                downloadable);
    }
}
