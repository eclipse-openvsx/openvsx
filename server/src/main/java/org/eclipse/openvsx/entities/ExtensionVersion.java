/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.ExtensionReferenceJson;
import org.eclipse.openvsx.json.SearchEntryJson;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TimeUtil;

@Entity
@Table(name = "extension_version")
public class ExtensionVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Comparator<ExtensionVersion> SORT_COMPARATOR = Comparator
            .comparing(ExtensionVersion::getSemanticVersion)
            .thenComparing(ExtensionVersion::isUniversalTargetPlatform, Comparator.reverseOrder())
            .thenComparing(ExtensionVersion::getTargetPlatform)
            .thenComparing(ExtensionVersion::getTimestamp, Comparator.reverseOrder());

    public enum Type {
        REGULAR, MINIMAL, EXTENDED
    }

    @Id
    @GeneratedValue(generator = "extensionVersionSeq")
    @SequenceGenerator(name = "extensionVersionSeq", sequenceName = "extension_version_seq")
    private long id;

    @ManyToOne
    private Extension extension;

    private String version;

    private String targetPlatform;

    private boolean universalTargetPlatform;

    @Embedded
    @AttributeOverride(name = "major", column = @Column(name = "semver_major"))
    @AttributeOverride(name = "minor", column = @Column(name = "semver_minor"))
    @AttributeOverride(name = "patch", column = @Column(name = "semver_patch"))
    @AttributeOverride(name = "preRelease", column = @Column(name = "semver_pre_release"))
    @AttributeOverride(name = "isPreRelease", column = @Column(name = "semver_is_pre_release"))
    @AttributeOverride(name = "buildMetadata", column = @Column(name = "semver_build_metadata"))
    private SemanticVersion semver;

    private boolean preRelease;

    private boolean preview;

    private LocalDateTime timestamp;

    /**
     * Who published this version.
     */
    @ManyToOne
    private UserData publishedBy;

    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private PersonalAccessTokenType publishedWithTt;

    /**
     * The token this version was published with, as best-effort provenance: it answers "what did this
     * credential publish" after a leak. Nullable and allowed to decay - the token row is deleted when a
     * one-time token is used or a forgotten user's tokens go - which is why authorship is recorded
     * separately in {@link #publishedBy} and {@link #publishedWithTt} instead of being read through it.
     */
    @Column(name = "published_with_id")
    @Nullable
    private Long publishedWithId;

    /**
     * The OIDC identity that produced this version, for versions published through trusted publishing: the
     * immutable repository and owner ids and the workflow reference including the ref it ran on, as the
     * provider asserted them at the exchange. Null for everything published with an ordinary token.
     * <p>
     * Copied at publish time rather than reached through the token, which is deleted as it is used.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_provenance", columnDefinition = "jsonb")
    @Nullable
    private Map<String, String> publishedProvenance;

    private boolean active;

    /**
     * Why the last publish attempt for this version did not finish, or {@code null} when none failed.
     * <p>
     * The work that follows a successful upload - storing the files, signing, checksumming - runs after
     * the response has gone out, so a failure in it reaches nobody. This is where it is written down, so
     * that a version sitting at {@code active == false} can be asked why rather than guessed at. Cleared
     * on activation, so it describes the latest attempt and not the history of them.
     */
    @Column(name = "publish_error", columnDefinition = "text")
    @Nullable
    private String publishError;

    private boolean potentiallyMalicious;

    /**
     * Sticky tombstone marker. A removed version has been soft-deleted: it is hidden
     * (also {@code active == false}) and its files have been stripped from storage, but the row is
     * kept so its identity stays permanently reserved and can never be republished. Only an admin
     * purge physically removes the row. Unlike {@code active}, this flag is never cleared by
     * reactivation or scan processing.
     */
    private boolean removed;

    private LocalDateTime removedTimestamp;

    @ManyToOne
    private UserData removedBy;

    private String displayName;

    @Column(length = 2048)
    private String description;

    @Column(length = 2048)
    @Convert(converter = ListOfStringConverter.class)
    private List<String> engines;

    @Column(length = 2048)
    @Convert(converter = ListOfStringConverter.class)
    private List<String> categories;

    @Column(length = 16384)
    @Convert(converter = ListOfStringConverter.class)
    private List<String> tags;

    @Column
    @Convert(converter = ListOfStringConverter.class)
    private List<String> extensionKind;

    private String license;

    private String homepage;

    private String repository;

    private String sponsorLink;

    private String bugs;

    @Column(length = 16)
    private String markdown;

    @Column(length = 16)
    private String galleryColor;

    @Column(length = 16)
    private String galleryTheme;

    @Column
    @Convert(converter = ListOfStringConverter.class)
    private List<String> localizedLanguages;

    private String qna;

    @Column(length = 2048)
    @Convert(converter = ListOfStringConverter.class)
    private List<String> dependencies;

    @Column(length = 2048)
    @Convert(converter = ListOfStringConverter.class)
    private List<String> bundledExtensions;

    @ManyToOne
    private SignatureKeyPair signatureKeyPair;

    @Transient
    private Type type;

    /**
     * Convert to a JSON object without URLs.
     */
    public ExtensionJson toExtensionJson() {
        var json = new ExtensionJson();
        json.setTargetPlatform(this.getTargetPlatform());
        var namespace = extension.getNamespace();
        json.setNamespace(namespace.getName());
        json.setNamespaceDisplayName(
                StringUtils.isNotEmpty(namespace.getDisplayName())
                        ? namespace.getDisplayName()
                        : json.getNamespace());
        json.setName(extension.getName());
        json.setAverageRating(extension.getAverageRating());
        json.setDownloadCount(extension.getDownloadCount());
        json.setVersion(this.getVersion());
        json.setPreRelease(this.isPreRelease());
        if (this.getTimestamp() != null) {
            json.setTimestamp(TimeUtil.toUTCString(this.getTimestamp()));
        }
        json.setDisplayName(this.getDisplayName());
        json.setDescription(this.getDescription());
        json.setEngines(this.getEnginesMap());
        json.setCategories(this.getCategories());
        json.setExtensionKind(this.getExtensionKind());
        json.setTags(this.getTags());
        json.setLicense(this.getLicense());
        json.setHomepage(this.getHomepage());
        json.setRepository(this.getRepository());
        json.setSponsorLink(this.getSponsorLink());
        json.setBugs(this.getBugs());
        json.setMarkdown(this.getMarkdown());
        json.setGalleryColor(this.getGalleryColor());
        json.setGalleryTheme(this.getGalleryTheme());
        json.setLocalizedLanguages(this.getLocalizedLanguages());
        json.setQna(this.getQna());
        if (this.getPublishedBy() != null) {
            json.setPublishedBy(this.getPublishedBy().toUserJson());
        }
        json.setPublishedWithTrustedPublishing(
                getPublishedWithTt() != null && getPublishedWithTt() == PersonalAccessTokenType.TPT);
        if (this.getDependencies() != null) {
            json.setDependencies(toExtensionReferenceJson(this.getDependencies()));
        }
        if (this.getBundledExtensions() != null) {
            json.setBundledExtensions(toExtensionReferenceJson(this.getBundledExtensions()));
        }

        json.setDeprecated(extension.isDeprecated());
        json.setDownloadable(extension.isDownloadable());
        return json;
    }

    private List<ExtensionReferenceJson> toExtensionReferenceJson(List<String> extensionReferences) {
        return extensionReferences.stream().map(fqn -> {
            var startIndex = fqn.indexOf('.');
            var lastIndex = fqn.lastIndexOf('.');
            if (startIndex <= 0 || lastIndex >= fqn.length() - 1 || startIndex != lastIndex) {
                return null;
            }
            var ref = new ExtensionReferenceJson();
            ref.setNamespace(fqn.substring(0, startIndex));
            ref.setExtension(fqn.substring(startIndex + 1));
            return ref;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Convert to a search entry JSON object without URLs.
     */
    public SearchEntryJson toSearchEntryJson() {
        var entry = new SearchEntryJson();
        var ext = this.getExtension();
        entry.setName(ext.getName());
        entry.setNamespace(ext.getNamespace().getName());
        entry.setAverageRating(ext.getAverageRating());
        entry.setReviewCount(ext.getReviewCount());
        entry.setDownloadCount(ext.getDownloadCount());
        entry.setVersion(this.getVersion());
        entry.setTimestamp(TimeUtil.toUTCString(this.getTimestamp()));
        entry.setDisplayName(this.getDisplayName());
        entry.setDescription(this.getDescription());
        entry.setDeprecated(ext.isDeprecated());
        return entry;
    }

    public Map<String, String> getEnginesMap() {
        var map = Optional.ofNullable(this.getEngines()).orElse(Collections.emptyList()).stream()
                .map(engine -> engine.split("@"))
                .filter(split -> split.length == 2)
                .collect(Collectors.toMap(split -> split[0], split -> split[1], (a, b) -> a, LinkedHashMap::new));

        return !map.isEmpty() ? map : null;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Extension getExtension() {
        return extension;
    }

    public void setExtension(Extension extension) {
        this.extension = extension;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        this.semver = SemanticVersion.parse(version);
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
        this.universalTargetPlatform = TargetPlatform.isUniversal(targetPlatform);
    }

    public boolean isUniversalTargetPlatform() {
        return universalTargetPlatform;
    }

    public void setUniversalTargetPlatform(boolean universalTargetPlatform) {
        // do nothing, universalTargetPlatform is derived from targetPlatform
    }

    public SemanticVersion getSemanticVersion() {
        return semver;
    }

    public void setSemanticVersion(SemanticVersion semver) {
        // do nothing, semver is derived from version
    }

    public boolean isPreRelease() {
        return preRelease;
    }

    public void setPreRelease(boolean preRelease) {
        this.preRelease = preRelease;
    }

    public boolean isPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public UserData getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UserData publishedBy) {
        this.publishedBy = publishedBy;
    }

    public PersonalAccessTokenType getPublishedWithTt() {
        return publishedWithTt;
    }

    @Nullable
    public Map<String, String> getPublishedProvenance() {
        return publishedProvenance;
    }

    public void setPublishedProvenance(@Nullable Map<String, String> publishedProvenance) {
        this.publishedProvenance = publishedProvenance;
    }

    @Nullable
    public Long getPublishedWithId() {
        return publishedWithId;
    }

    public void setPublishedWithId(@Nullable Long publishedWithId) {
        this.publishedWithId = publishedWithId;
    }

    public void setPublishedWithTt(PersonalAccessTokenType publishedWithTt) {
        this.publishedWithTt = publishedWithTt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public @Nullable String getPublishError() {
        return publishError;
    }

    public void setPublishError(@Nullable String publishError) {
        this.publishError = publishError;
    }

    public boolean isPotentiallyMalicious() {
        return potentiallyMalicious;
    }

    public void setPotentiallyMalicious(boolean potentiallyMalicious) {
        this.potentiallyMalicious = potentiallyMalicious;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    /**
     * Whether the extension this version belongs to should be reported as deleted, i.e. this version is a
     * soft-delete tombstone <em>and</em> the extension has no active version left.
     * <p>
     * Meant for the endpoints that describe an extension through its latest version: as the latest version
     * is picked across all target platforms including removed ones, a removed version on its own says
     * nothing about the extension — other versions or target platforms may well still be available.
     */
    public boolean isExtensionRemoved() {
        return removed && extension != null && !extension.isActive();
    }

    public LocalDateTime getRemovedTimestamp() {
        return removedTimestamp;
    }

    public void setRemovedTimestamp(LocalDateTime removedTimestamp) {
        this.removedTimestamp = removedTimestamp;
    }

    public UserData getRemovedBy() {
        return removedBy;
    }

    public void setRemovedBy(UserData removedBy) {
        this.removedBy = removedBy;
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

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public List<String> getEngines() {
        return engines;
    }

    public void setEngines(List<String> engines) {
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

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getBundledExtensions() {
        return bundledExtensions;
    }

    public void setBundledExtensions(List<String> bundledExtensions) {
        this.bundledExtensions = bundledExtensions;
    }

    public SignatureKeyPair getSignatureKeyPair() {
        return signatureKeyPair;
    }

    public void setSignatureKeyPair(SignatureKeyPair signatureKeyPair) {
        this.signatureKeyPair = signatureKeyPair;
    }

    public void setType(ExtensionVersion.Type type) {
        this.type = type;
    }

    public ExtensionVersion.Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtensionVersion that = (ExtensionVersion) o;
        return id == that.id
                && preRelease == that.preRelease
                && preview == that.preview
                && active == that.active
                && potentiallyMalicious == that.potentiallyMalicious
                && Objects.equals(publishError, that.publishError)
                && removed == that.removed
                && Objects.equals(removedTimestamp, that.removedTimestamp)
                && Objects.equals(getId(removedBy), getId(that.removedBy)) // use id to prevent infinite recursion
                && Objects.equals(getId(extension), getId(that.extension)) // use id to prevent infinite recursion
                && Objects.equals(version, that.version)
                && Objects.equals(targetPlatform, that.targetPlatform)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(getId(publishedBy), getId(that.publishedBy)) // use id to prevent infinite recursion
                && Objects.equals(publishedWithTt, that.publishedWithTt)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(description, that.description)
                && Objects.equals(engines, that.engines)
                && Objects.equals(categories, that.categories)
                && Objects.equals(tags, that.tags)
                && Objects.equals(extensionKind, that.extensionKind)
                && Objects.equals(license, that.license)
                && Objects.equals(homepage, that.homepage)
                && Objects.equals(repository, that.repository)
                && Objects.equals(sponsorLink, that.sponsorLink)
                && Objects.equals(bugs, that.bugs)
                && Objects.equals(markdown, that.markdown)
                && Objects.equals(galleryColor, that.galleryColor)
                && Objects.equals(galleryTheme, that.galleryTheme)
                && Objects.equals(localizedLanguages, that.localizedLanguages)
                && Objects.equals(qna, that.qna)
                && Objects.equals(dependencies, that.dependencies)
                && Objects.equals(bundledExtensions, that.bundledExtensions)
                && Objects.equals(signatureKeyPair, that.signatureKeyPair)
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                getId(extension),
                version,
                targetPlatform,
                semver,
                preRelease,
                preview,
                timestamp,
                getId(publishedBy),
                publishedWithTt,
                active,
                potentiallyMalicious,
                publishError,
                removed,
                removedTimestamp,
                getId(removedBy),
                displayName,
                description,
                engines,
                categories,
                tags,
                extensionKind,
                license,
                homepage,
                repository,
                sponsorLink,
                bugs,
                markdown,
                galleryColor,
                galleryTheme,
                localizedLanguages,
                qna,
                dependencies,
                bundledExtensions,
                signatureKeyPair,
                type);
    }

    private Long getId(Extension extension) {
        return Optional.ofNullable(extension).map(Extension::getId).orElse(null);
    }

    private Long getId(UserData user) {
        return Optional.ofNullable(user).map(UserData::getId).orElse(null);
    }
}
