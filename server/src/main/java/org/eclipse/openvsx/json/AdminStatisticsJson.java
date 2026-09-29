/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminStatisticsJson extends ResultJson {

    public static AdminStatisticsJson error(String message) {
        var result = new AdminStatisticsJson();
        result.setError(message);
        return result;
    }

    private int year;

    private int month;

    private long extensions;

    private long downloads;

    private long downloadsTotal;

    private long publishers;

    private double averageReviewsPerExtension;

    private long namespaceOwners;

    private List<ExtensionsByRating> extensionsByRating;

    private List<PublishersByExtensionsPublished> publishersByExtensionsPublished;

    private List<TopMostActivePublishingUsers> topMostActivePublishingUsers;

    private List<TopNamespaceExtensions> topNamespaceExtensions;

    private List<TopNamespaceExtensionVersions> topNamespaceExtensionVersions;

    private List<TopMostDownloadedExtensions> topMostDownloadedExtensions;

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

    public List<ExtensionsByRating> getExtensionsByRating() {
        return extensionsByRating;
    }

    public void setExtensionsByRating(List<ExtensionsByRating> extensionsByRating) {
        this.extensionsByRating = extensionsByRating;
    }

    public List<PublishersByExtensionsPublished> getPublishersByExtensionsPublished() {
        return publishersByExtensionsPublished;
    }

    public void setPublishersByExtensionsPublished(
            List<PublishersByExtensionsPublished> publishersByExtensionsPublished
    ) {
        this.publishersByExtensionsPublished = publishersByExtensionsPublished;
    }

    public List<TopMostActivePublishingUsers> getTopMostActivePublishingUsers() {
        return topMostActivePublishingUsers;
    }

    public void setTopMostActivePublishingUsers(List<TopMostActivePublishingUsers> topMostActivePublishingUsers) {
        this.topMostActivePublishingUsers = topMostActivePublishingUsers;
    }

    public List<TopNamespaceExtensions> getTopNamespaceExtensions() {
        return topNamespaceExtensions;
    }

    public void setTopNamespaceExtensions(List<TopNamespaceExtensions> topNamespaceExtensions) {
        this.topNamespaceExtensions = topNamespaceExtensions;
    }

    public List<TopNamespaceExtensionVersions> getTopNamespaceExtensionVersions() {
        return topNamespaceExtensionVersions;
    }

    public void setTopNamespaceExtensionVersions(List<TopNamespaceExtensionVersions> topNamespaceExtensionVersions) {
        this.topNamespaceExtensionVersions = topNamespaceExtensionVersions;
    }

    public List<TopMostDownloadedExtensions> getTopMostDownloadedExtensions() {
        return topMostDownloadedExtensions;
    }

    public void setTopMostDownloadedExtensions(List<TopMostDownloadedExtensions> topMostDownloadedExtensions) {
        this.topMostDownloadedExtensions = topMostDownloadedExtensions;
    }

    public static class ExtensionsByRating {
        private int rating;

        private int extensions;

        public int getRating() {
            return rating;
        }

        public void setRating(int rating) {
            this.rating = rating;
        }

        public int getExtensions() {
            return extensions;
        }

        public void setExtensions(int extensions) {
            this.extensions = extensions;
        }
    }

    public static class PublishersByExtensionsPublished {
        private int extensionsPublished;

        private int publishers;

        public int getExtensionsPublished() {
            return extensionsPublished;
        }

        public void setExtensionsPublished(int extensionsPublished) {
            this.extensionsPublished = extensionsPublished;
        }

        public int getPublishers() {
            return publishers;
        }

        public void setPublishers(int publishers) {
            this.publishers = publishers;
        }
    }

    public static class TopMostActivePublishingUsers {
        private String userLoginName;

        private int publishedExtensionVersions;

        public String getUserLoginName() {
            return userLoginName;
        }

        public void setUserLoginName(String userLoginName) {
            this.userLoginName = userLoginName;
        }

        public int getPublishedExtensionVersions() {
            return publishedExtensionVersions;
        }

        public void setPublishedExtensionVersions(int publishedExtensionVersions) {
            this.publishedExtensionVersions = publishedExtensionVersions;
        }
    }

    public static class TopNamespaceExtensions {
        private String namespace;

        private int extensions;

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public int getExtensions() {
            return extensions;
        }

        public void setExtensions(int extensions) {
            this.extensions = extensions;
        }
    }

    public static class TopNamespaceExtensionVersions {
        private String namespace;

        private int extensionVersions;

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public int getExtensionVersions() {
            return extensionVersions;
        }

        public void setExtensionVersions(int extensionVersions) {
            this.extensionVersions = extensionVersions;
        }
    }

    public static class TopMostDownloadedExtensions {
        private String extensionIdentifier;

        private long downloads;

        public String getExtensionIdentifier() {
            return extensionIdentifier;
        }

        public void setExtensionIdentifier(String extensionIdentifier) {
            this.extensionIdentifier = extensionIdentifier;
        }

        public long getDownloads() {
            return downloads;
        }

        public void setDownloads(long downloads) {
            this.downloads = downloads;
        }
    }
}
