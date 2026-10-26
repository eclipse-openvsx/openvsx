/******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
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
package org.eclipse.openvsx.entities;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.*;

import org.eclipse.openvsx.json.AdminStatisticsJson;

@Entity
@Table(name = "admin_statistics")
public class AdminStatistics {

    /** The publish method of versions published without a token, from the web UI or unrecorded. */
    public static final String PUBLISH_METHOD_NONE = "NONE";

    @Id
    @GeneratedValue(generator = "adminStatisticsSeq")
    @SequenceGenerator(name = "adminStatisticsSeq", sequenceName = "admin_statistics_seq")
    private long id;

    private int year;

    private int month;

    private long extensions;

    private long downloads;

    private long downloadsTotal;

    private long publishers;

    private double averageReviewsPerExtension;

    private long namespaceOwners;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "rating")
    @Column(name = "extensions")
    private Map<Integer, Integer> extensionsByRating;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "extensions_published")
    @Column(name = "publishers")
    private Map<Integer, Integer> publishersByExtensionsPublished;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "login_name")
    @Column(name = "extension_version_count")
    private Map<String, Integer> topMostActivePublishingUsers;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "namespace")
    @Column(name = "extension_count")
    private Map<String, Integer> topNamespaceExtensions;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "namespace")
    @Column(name = "extension_version_count")
    private Map<String, Integer> topNamespaceExtensionVersions;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "extension_identifier")
    @Column(name = "downloads")
    private Map<String, Long> topMostDownloadedExtensions;

    /**
     * Keyed by {@link PersonalAccessTokenType} name, or {@code NONE} for web UI uploads and unknown; web UI
     * uploads before 1.2.0 went through a one-time token and are keyed {@code OTT}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "publish_method")
    @Column(name = "extension_versions")
    private Map<String, Integer> extensionVersionsByPublishMethod;

    public String toCsv() {
        var ratings = 5;
        var headers = new ArrayList<>(
                List.of(
                        "year",
                        "month",
                        "extensions",
                        "downloads",
                        "downloads_total",
                        "publishers",
                        "average_reviews_per_extension",
                        "namespace_owners"));
        for (int i = 0; i < ratings; i++) {
            headers.add("extensions_by_rating_" + (i + 1));
        }

        var extensionsPublishedAmounts = publishersByExtensionsPublished.keySet().stream().sorted()
                .collect(Collectors.toList());
        for (var amount : extensionsPublishedAmounts) {
            headers.add("publishers_published_extensions_" + amount);
        }

        var values = new ArrayList<Number>(
                List.of(
                        year,
                        month,
                        extensions,
                        downloads,
                        downloadsTotal,
                        publishers,
                        averageReviewsPerExtension,
                        namespaceOwners));
        for (int i = 0; i < ratings; i++) {
            values.add(extensionsByRating.getOrDefault(i + 1, 0));
        }
        for (var amount : extensionsPublishedAmounts) {
            values.add(publishersByExtensionsPublished.get(amount));
        }
        // A fixed set of columns, so CSVs of different months line up.
        var publishMethods = new ArrayList<String>();
        for (var type : PersonalAccessTokenType.values()) {
            publishMethods.add(type.name());
        }
        publishMethods.add(PUBLISH_METHOD_NONE);
        for (var method : publishMethods) {
            headers.add("extension_versions_published_with_" + method.toLowerCase());
            values.add(extensionVersionsByPublishMethod.getOrDefault(method, 0));
        }

        topMapToCsv(headers, values, topMostActivePublishingUsers, "most_active_publishing_users_");
        topMapToCsv(headers, values, topNamespaceExtensions, "namespace_extensions_");
        topMapToCsv(headers, values, topNamespaceExtensionVersions, "namespace_extension_versions_");
        topMapToCsv(headers, values, topMostDownloadedExtensions, "most_downloaded_extensions_");

        var valueStrings = values.stream().map(String::valueOf).collect(Collectors.joining(","));
        return String.join(",", headers) + "\n" + valueStrings;
    }

    private void topMapToCsv(
            List<String> headers,
            List<Number> values,
            Map<String, ? extends Number> topMap,
            String headerPrefix
    ) {
        topMap.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<String, ? extends Number>>comparingLong(
                                entry -> entry.getValue().longValue()).reversed())
                .forEach(entry -> {
                    headers.add(headerPrefix + entry.getKey());
                    values.add(entry.getValue());
                });
    }

    public AdminStatisticsJson toJson() {
        var json = new AdminStatisticsJson();
        json.setYear(year);
        json.setMonth(month);
        json.setExtensions(extensions);
        json.setDownloads(downloads);
        json.setDownloadsTotal(downloadsTotal);
        json.setPublishers(publishers);
        json.setAverageReviewsPerExtension(averageReviewsPerExtension);
        json.setNamespaceOwners(namespaceOwners);
        json.setExtensionsByRating(mapExtensionsByRating());
        json.setPublishersByExtensionsPublished(mapPublishersByExtensionsPublished());
        json.setTopMostActivePublishingUsers(mapTopMostActivePublishingUsers());
        json.setTopNamespaceExtensions(mapTopNamespaceExtensions());
        json.setTopNamespaceExtensionVersions(mapTopNamespaceExtensionVersions());
        json.setTopMostDownloadedExtensions(mapTopMostDownloadedExtensions());
        json.setExtensionVersionsByPublishMethod(mapExtensionVersionsByPublishMethod());

        return json;
    }

    private List<AdminStatisticsJson.ExtensionVersionsByPublishMethod> mapExtensionVersionsByPublishMethod() {
        return extensionVersionsByPublishMethod.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.ExtensionVersionsByPublishMethod();
                    mapping.setPublishMethod(entry.getKey());
                    mapping.setExtensionVersions(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.ExtensionVersionsByPublishMethod>comparingInt(
                                pe -> pe.getExtensionVersions()).reversed())
                .collect(Collectors.toList());
    }

    private List<AdminStatisticsJson.ExtensionsByRating> mapExtensionsByRating() {
        return extensionsByRating.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.ExtensionsByRating();
                    mapping.setRating(entry.getKey());
                    mapping.setExtensions(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.ExtensionsByRating>comparingInt(er -> er.getRating())
                                .reversed())
                .collect(Collectors.toList());
    }

    private List<AdminStatisticsJson.PublishersByExtensionsPublished> mapPublishersByExtensionsPublished() {
        return publishersByExtensionsPublished.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.PublishersByExtensionsPublished();
                    mapping.setExtensionsPublished(entry.getKey());
                    mapping.setPublishers(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.PublishersByExtensionsPublished>comparingInt(
                                pe -> pe.getExtensionsPublished()).reversed())
                .collect(Collectors.toList());
    }

    private List<AdminStatisticsJson.TopMostActivePublishingUsers> mapTopMostActivePublishingUsers() {
        return topMostActivePublishingUsers.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.TopMostActivePublishingUsers();
                    mapping.setUserLoginName(entry.getKey());
                    mapping.setPublishedExtensionVersions(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.TopMostActivePublishingUsers>comparingInt(
                                pe -> pe.getPublishedExtensionVersions()).reversed())
                .collect(Collectors.toList());
    }

    private List<AdminStatisticsJson.TopNamespaceExtensions> mapTopNamespaceExtensions() {
        return topNamespaceExtensions.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.TopNamespaceExtensions();
                    mapping.setNamespace(entry.getKey());
                    mapping.setExtensions(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.TopNamespaceExtensions>comparingInt(pe -> pe.getExtensions())
                                .reversed())
                .collect(Collectors.toList());
    }

    private List<AdminStatisticsJson.TopNamespaceExtensionVersions> mapTopNamespaceExtensionVersions() {
        return topNamespaceExtensionVersions.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.TopNamespaceExtensionVersions();
                    mapping.setNamespace(entry.getKey());
                    mapping.setExtensionVersions(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.TopNamespaceExtensionVersions>comparingInt(
                                pe -> pe.getExtensionVersions()).reversed())
                .collect(Collectors.toList());
    }

    private List<AdminStatisticsJson.TopMostDownloadedExtensions> mapTopMostDownloadedExtensions() {
        return topMostDownloadedExtensions.entrySet().stream()
                .map(entry -> {
                    var mapping = new AdminStatisticsJson.TopMostDownloadedExtensions();
                    mapping.setExtensionIdentifier(entry.getKey());
                    mapping.setDownloads(entry.getValue());
                    return mapping;
                })
                .sorted(
                        Comparator.<AdminStatisticsJson.TopMostDownloadedExtensions>comparingLong(
                                pe -> pe.getDownloads()).reversed())
                .collect(Collectors.toList());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public long getExtensions() {
        return extensions;
    }

    public void setExtensions(long extensions) {
        this.extensions = extensions;
    }

    public long getDownloads() {
        return downloads;
    }

    public void setDownloads(long downloads) {
        this.downloads = downloads;
    }

    public long getDownloadsTotal() {
        return downloadsTotal;
    }

    public void setDownloadsTotal(long downloadsTotal) {
        this.downloadsTotal = downloadsTotal;
    }

    public long getPublishers() {
        return publishers;
    }

    public void setPublishers(long publishers) {
        this.publishers = publishers;
    }

    public double getAverageReviewsPerExtension() {
        return averageReviewsPerExtension;
    }

    public void setAverageReviewsPerExtension(double averageReviewsPerExtension) {
        this.averageReviewsPerExtension = averageReviewsPerExtension;
    }

    public long getNamespaceOwners() {
        return namespaceOwners;
    }

    public void setNamespaceOwners(long namespaceOwners) {
        this.namespaceOwners = namespaceOwners;
    }

    public Map<Integer, Integer> getExtensionsByRating() {
        return extensionsByRating;
    }

    public void setExtensionsByRating(Map<Integer, Integer> extensionsByRating) {
        this.extensionsByRating = extensionsByRating;
    }

    public Map<Integer, Integer> getPublishersByExtensionsPublished() {
        return publishersByExtensionsPublished;
    }

    public void setPublishersByExtensionsPublished(Map<Integer, Integer> publishersByExtensionsPublished) {
        this.publishersByExtensionsPublished = publishersByExtensionsPublished;
    }

    public Map<String, Integer> getTopMostActivePublishingUsers() {
        return topMostActivePublishingUsers;
    }

    public void setTopMostActivePublishingUsers(Map<String, Integer> topMostActivePublishingUsers) {
        this.topMostActivePublishingUsers = topMostActivePublishingUsers;
    }

    public Map<String, Integer> getTopNamespaceExtensions() {
        return topNamespaceExtensions;
    }

    public void setTopNamespaceExtensions(Map<String, Integer> topNamespaceExtensions) {
        this.topNamespaceExtensions = topNamespaceExtensions;
    }

    public Map<String, Integer> getTopNamespaceExtensionVersions() {
        return topNamespaceExtensionVersions;
    }

    public void setTopNamespaceExtensionVersions(Map<String, Integer> topNamespaceExtensionVersions) {
        this.topNamespaceExtensionVersions = topNamespaceExtensionVersions;
    }

    public Map<String, Long> getTopMostDownloadedExtensions() {
        return topMostDownloadedExtensions;
    }

    public void setTopMostDownloadedExtensions(Map<String, Long> topMostDownloadedExtensions) {
        this.topMostDownloadedExtensions = topMostDownloadedExtensions;
    }

    public Map<String, Integer> getExtensionVersionsByPublishMethod() {
        return extensionVersionsByPublishMethod;
    }

    public void setExtensionVersionsByPublishMethod(Map<String, Integer> extensionVersionsByPublishMethod) {
        this.extensionVersionsByPublishMethod = extensionVersionsByPublishMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AdminStatistics that = (AdminStatistics) o;
        return id == that.id && year == that.year && month == that.month && extensions == that.extensions
                && downloads == that.downloads && downloadsTotal == that.downloadsTotal && publishers == that.publishers
                && Double.compare(that.averageReviewsPerExtension, averageReviewsPerExtension) == 0
                && namespaceOwners == that.namespaceOwners
                && Objects.equals(extensionsByRating, that.extensionsByRating)
                && Objects.equals(publishersByExtensionsPublished, that.publishersByExtensionsPublished)
                && Objects.equals(topMostActivePublishingUsers, that.topMostActivePublishingUsers)
                && Objects.equals(topNamespaceExtensions, that.topNamespaceExtensions)
                && Objects.equals(topNamespaceExtensionVersions, that.topNamespaceExtensionVersions)
                && Objects.equals(topMostDownloadedExtensions, that.topMostDownloadedExtensions)
                && Objects.equals(extensionVersionsByPublishMethod, that.extensionVersionsByPublishMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                year,
                month,
                extensions,
                downloads,
                downloadsTotal,
                publishers,
                averageReviewsPerExtension,
                namespaceOwners,
                extensionsByRating,
                publishersByExtensionsPublished,
                topMostActivePublishingUsers,
                topNamespaceExtensions,
                topNamespaceExtensionVersions,
                topMostDownloadedExtensions,
                extensionVersionsByPublishMethod);
    }
}
