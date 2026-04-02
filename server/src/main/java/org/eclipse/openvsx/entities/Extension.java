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

import jakarta.persistence.*;
import org.eclipse.openvsx.search.ExtensionSearch;
import org.eclipse.openvsx.util.NamingUtil;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "extension")
public class Extension implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "extensionSeq")
    @SequenceGenerator(name = "extensionSeq", sequenceName = "extension_seq")
    private long id;

    @Column(length = 128)
    private String publicId;

    private String name;

    @ManyToOne
    private Namespace namespace;

    @OneToMany(mappedBy = "extension")
    private List<ExtensionVersion> versions;

    private boolean active;

    private Double averageRating;

    private Long reviewCount;

    private int downloadCount;

    private LocalDateTime publishedDate;

    private LocalDateTime lastUpdatedDate;

    private boolean deprecated;

    @OneToOne
    private Extension replacement;

    private boolean downloadable;

    /**
     * Convert to a search entity for Elasticsearch.
     */
    public ExtensionSearch toSearch(ExtensionVersion latest, List<String> targetPlatforms) {
        var search = new ExtensionSearch();
        search.setId(this.getId());
        search.setName(this.getName());
        search.setNamespace(this.getNamespace().getName());
        search.setExtensionId(NamingUtil.toExtensionId(search));
        search.setDownloadCount(this.getDownloadCount());
        search.setTargetPlatforms(targetPlatforms);
        search.setDisplayName(latest.getDisplayName());
        search.setDescription(latest.getDescription());
        search.setTimestamp(latest.getTimestamp().toEpochSecond(ZoneOffset.UTC));
        search.setCategories(latest.getCategories());
        search.setTags(latest.getTags());
        search.setExtensionKind(latest.getExtensionKind());

        return search;
    }

    public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getPublicId() {
		return publicId;
	}

	public void setPublicId(String publicId) {
		this.publicId = publicId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Namespace getNamespace() {
		return namespace;
    }

	public void setNamespace(Namespace namespace) {
		this.namespace = namespace;
	}

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public LocalDateTime getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDateTime publishedDate) {
        this.publishedDate = publishedDate;
    }

    public LocalDateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    public List<ExtensionVersion> getVersions() {
        if(versions == null) {
            versions = new ArrayList<>();
        }

        return versions;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public Extension getReplacement() {
        return replacement;
    }

    public void setReplacement(Extension replacement) {
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extension extension = (Extension) o;
        return id == extension.id
                && active == extension.active
                && downloadCount == extension.downloadCount
                && Objects.equals(publicId, extension.publicId)
                && Objects.equals(name, extension.name)
                && Objects.equals(namespace, extension.namespace)
                && Objects.equals(versions, extension.versions)
                && Objects.equals(averageRating, extension.averageRating)
                && Objects.equals(reviewCount, extension.reviewCount)
                && Objects.equals(publishedDate, extension.publishedDate)
                && Objects.equals(lastUpdatedDate, extension.lastUpdatedDate)
                && Objects.equals(deprecated, extension.deprecated)
                && Objects.equals(replacement, extension.replacement)
                && Objects.equals(downloadable, extension.downloadable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, publicId, name, namespace, versions, active, averageRating, reviewCount, downloadCount,
                publishedDate, lastUpdatedDate, deprecated, replacement, downloadable
        );
    }
}