/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.search;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Objects;

@Document(indexName = "extensions")
public class ExtensionSearch {

    @Field(index = false)
    private long id;

    @Field(index = false, type = FieldType.Float)
    private double relevance;

    private String name;

    private String namespace;

    @Field(index = false)
    private String extensionId;

    private List<String> targetPlatforms;

    private String displayName;

    private String description;

    @Field(index = false)
    private long timestamp;

    @Nullable
    @Field(index = false, type = FieldType.Float)
    private Double rating;

    @Field(index = false)
    private int downloadCount;

    @Field(index = false)
    private List<String> categories;

    private List<String> tags;

    private List<String> extensionKind;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getRelevance() {
        return relevance;
    }

    public void setRelevance(double relevance) {
        this.relevance = relevance;
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

    public String getExtensionId() {
        return extensionId;
    }

    public void setExtensionId(String extensionId) {
        this.extensionId = extensionId;
    }

    public List<String> getTargetPlatforms() {
        return targetPlatforms;
    }

    public void setTargetPlatforms(List<String> targetPlatforms) {
        this.targetPlatforms = targetPlatforms;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public Double getRating() {
        return rating;
    }

    public void setRating(@Nullable Double rating) {
        this.rating = rating;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getExtensionKind() {
        return extensionKind;
    }

    public void setExtensionKind(List<String> extensionKind) {
        this.extensionKind = extensionKind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionSearch that = (ExtensionSearch) o;
        return id == that.id
                && Double.compare(that.relevance, relevance) == 0
                && timestamp == that.timestamp
                && downloadCount == that.downloadCount
                && Objects.equals(name, that.name)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(extensionId, that.extensionId)
                && Objects.equals(targetPlatforms, that.targetPlatforms)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(description, that.description)
                && Objects.equals(rating, that.rating)
                && Objects.equals(categories, that.categories)
                && Objects.equals(tags, that.tags)
                && Objects.equals(extensionKind, that.extensionKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, relevance, name, namespace, extensionId, targetPlatforms, displayName, description, timestamp,
                rating, downloadCount, categories, tags, extensionKind
        );
    }
}