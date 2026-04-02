/********************************************************************************
 * Copyright (c) 2021 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.openvsx.search;

import jakarta.persistence.EntityManager;
import org.eclipse.openvsx.cache.LatestExtensionVersionCacheKeyGenerator;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Streamable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
class DatabaseSearchServiceTest {

    @MockitoBean
    EntityManager entityManager;

    @MockitoBean
    RepositoryService repositories;

    @Autowired
    DatabaseSearchService search;

    @Test
    void testCategory() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3)));

        var searchOptions = searchOptions(null, "Programming Languages",50, 0, null, null);
        var result = search.search(searchOptions);
        // should find two extensions
        assertThat(result.getTotalHits()).isEqualTo(2);
    }

    @Test
    void testRelevance() {
        var ext1 = mockExtension("yaml", 1.0, 100, 100, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 10000, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 1.0, 100, 10, "redhat", List.of("Snippets", "Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3)));

        var searchOptions = searchOptions(null, null, 50, 0, null, SortBy.RELEVANCE);
        var result = search.search(searchOptions);
        // should find all extensions but order should be different
        assertThat(result.getTotalHits()).isEqualTo(3);

        var hits = result.getHits();
        // java should have the most relevance
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("openshift"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("yaml"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("java"));
    }

    @Test
    void testReverse() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2)));

        var searchOptions = searchOptions(null, "Programming Languages", 50, 0, "desc", null);
        var result = search.search(searchOptions);
        // should find two extensions
        assertThat(result.getTotalHits()).isEqualTo(2);

        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("java"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("yaml"));
    }

    @Test
    void testSimplePageSize() {
        var ext1 = mockExtension("ext1", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("ext2", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("ext3", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext4 = mockExtension("ext4", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext5 = mockExtension("ext5", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext6 = mockExtension("ext6", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext7 = mockExtension("ext7", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4, ext5, ext6, ext7)));

        var pageSizeItems = 5;
        var searchOptions = searchOptions(null, null, pageSizeItems, 0, null, null);

        var result = search.search(searchOptions);
        // 7 total hits
        assertThat(result.getTotalHits()).isEqualTo(7);
        // but as we limit the page size it should only contains 5
        var hits = result.getHits();
        assertThat(hits.size()).isEqualTo(pageSizeItems);

        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("ext1"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("ext2"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("ext3"));
        assertThat(getIdFromExtensionHits(hits, 3)).isEqualTo(getIdFromExtensionName("ext4"));
        assertThat(getIdFromExtensionHits(hits, 4)).isEqualTo(getIdFromExtensionName("ext5"));
    }

    @Test
    void testPages() {
        var ext1 = mockExtension("ext1", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("ext2", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("ext3", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext4 = mockExtension("ext4", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext5 = mockExtension("ext5", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext6 = mockExtension("ext6", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext7 = mockExtension("ext7", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4, ext5, ext6, ext7)));

        var pageSizeItems = 2;
        var searchOptions = searchOptions(null, null, pageSizeItems, 4, null, null);
        var result = search.search(searchOptions);

        // 7 total hits
        assertThat(result.getTotalHits()).isEqualTo(7);
        // But it should only contains 2 search items as specified by the pageSize
        var hits = result.getHits();
        assertThat(hits.size()).isEqualTo(pageSizeItems);

        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("ext5"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("ext6"));
    }

    @Test
    void testQueryStringPublisherName() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions("redhat", null, 50, 0, null, null);
        var result = search.search(searchOptions);
        // namespace finding
        assertThat(result.getTotalHits()).isEqualTo(3);

        // Check it found the correct extension
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("yaml"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("java"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("openshift"));
    }

    @Test
    void testPublisher() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions("publisher:RedHat", null, 50, 0, null, null);
        var result = search.search(searchOptions);
        // namespace finding
        assertThat(result.getTotalHits()).isEqualTo(3);

        // Check it found the correct extension
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("yaml"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("java"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("openshift"));
    }

    @Test
    void testPublisherWithQuery() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions("publisher:RedHat yaml", null, 50, 0, null, null);
        var result = search.search(searchOptions);
        // namespace finding
        assertThat(result.getTotalHits()).isEqualTo(1);

        // Check it found the correct extension
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("yaml"));
    }

    @Test
    void testQueryStringExtensionName() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions("openshift", null, 50, 0, null, null);
        var result = search.search(searchOptions);
        // extension name finding
        assertThat(result.getTotalHits()).isEqualTo(1);

        // Check it found the correct extension
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("openshift"));
    }

    @Test
    void testQueryStringDescription() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        ext2.getVersions().get(0).setDescription("another desc");
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        ext3.getVersions().get(0).setDescription("my custom desc");
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions("my custom desc", null, 50, 0, null, null);
        var result = search.search(searchOptions);
        // custom description
        assertThat(result.getTotalHits()).isEqualTo(1);

        // Check it found the correct extension
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("openshift"));
    }

    @Test
    void testQueryStringDisplayName() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        ext1.getVersions().get(0).setDisplayName("This is a YAML extension");
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        ext2.getVersions().get(0).setDisplayName("Red Hat");
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions("Red Hat", null, 50, 0, null, null);
        var result = search.search(searchOptions);

        // custom displayname
        assertThat(result.getTotalHits()).isEqualTo(1);

        // Check it found the correct extension
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("java"));
    }

    @Test
    void testSortByTimeStamp() {
        var ext1 = mockExtension("yaml", 3.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        ext1.getVersions().get(0).setTimestamp(LocalDateTime.parse("2021-10-10T00:00"));
        var ext2 = mockExtension("java", 4.0, 100, 0, "redhat", List.of("Snippets", "Programming Languages"));
        ext2.getVersions().get(0).setTimestamp(LocalDateTime.parse("2021-10-07T00:00"));
        var ext3 = mockExtension("openshift", 4.0, 100, 0, "redhat", List.of("Snippets", "Other"));
        ext3.getVersions().get(0).setTimestamp(LocalDateTime.parse("2021-10-11T00:00"));
        var ext4 = mockExtension("foo", 4.0, 100, 0, "bar", List.of("Other"));
        ext4.getVersions().get(0).setTimestamp(LocalDateTime.parse("2021-10-06T00:00"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions(null, null, 50, 0, null, SortBy.TIMESTAMP);
        var result = search.search(searchOptions);
        // all extensions should be there
        assertThat(result.getTotalHits()).isEqualTo(4);

        // test now the order
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("foo"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("java"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("yaml"));
        assertThat(getIdFromExtensionHits(hits, 3)).isEqualTo(getIdFromExtensionName("openshift"));
    }

    @Test
    void testSortByDownloadCount() {
        var ext1 = mockExtension("yaml", 3.0, 100, 100, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 4.0, 100, 1000, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 4.0, 100, 300, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 4.0, 100, 500, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions(null, null, 50, 0, null, SortBy.DOWNLOADS);
        var result = search.search(searchOptions);
        // all extensions should be there
        assertThat(result.getTotalHits()).isEqualTo(4);

        // test now the order
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("yaml"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("openshift"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("foo"));
        assertThat(getIdFromExtensionHits(hits, 3)).isEqualTo(getIdFromExtensionName("java"));
    }

    @Test
    void testSortByRating() {
        var ext1 = mockExtension("yaml", 4.0, 1, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext2 = mockExtension("java", 5.0, 1, 0, "redhat", List.of("Snippets", "Programming Languages"));
        var ext3 = mockExtension("openshift", 2.0, 1, 0, "redhat", List.of("Snippets", "Other"));
        var ext4 = mockExtension("foo", 1.0, 1, 0, "bar", List.of("Other"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptions = searchOptions(null, null, 50, 0, null, SortBy.RATING);
        var result = search.search(searchOptions);
        // all extensions should be there
        assertThat(result.getTotalHits()).isEqualTo(4);

        // test now the order
        var hits = result.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("foo"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("openshift"));
        assertThat(getIdFromExtensionHits(hits, 2)).isEqualTo(getIdFromExtensionName("yaml"));
        assertThat(getIdFromExtensionHits(hits, 3)).isEqualTo(getIdFromExtensionName("java"));
    }

    @Test
    void testWebOnlyFilter() {
        var ext1 = mockExtension("web-ext", 4.0, 1, 0, "test", List.of("Other"), List.of("web"));
        var ext2 = mockExtension("desktop-ext", 4.0, 1, 0, "test", List.of("Other"), List.of("ui", "workspace"));
        var ext3 = mockExtension("hybrid-ext", 4.0, 1, 0, "test", List.of("Other"), List.of("web", "ui"));
        var ext4 = mockExtension("no-kind-ext", 4.0, 1, 0, "test", List.of("Other"), null);
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3, ext4)));

        var searchOptionsAll = searchOptions(null, null, 50, 0, null, null, null);
        var resultAll = search.search(searchOptionsAll);
        assertThat(resultAll.getTotalHits()).isEqualTo(4);

        var searchOptionsWebOnly = searchOptions(null, null, 50, 0, null, null, true);
        var resultWebOnly = search.search(searchOptionsWebOnly);
        assertThat(resultWebOnly.getTotalHits()).isEqualTo(2);
        
        var hits = resultWebOnly.getHits();
        assertThat(getIdFromExtensionHits(hits, 0)).isEqualTo(getIdFromExtensionName("web-ext"));
        assertThat(getIdFromExtensionHits(hits, 1)).isEqualTo(getIdFromExtensionName("hybrid-ext"));
    }

    @Test
    void testWebOnlyFilterWithQuery() {
        var ext1 = mockExtension("web-javascript", 4.0, 1, 0, "test", List.of("Other"), List.of("web"));
        var ext2 = mockExtension("desktop-javascript", 4.0, 1, 0, "test", List.of("Other"), List.of("ui"));
        var ext3 = mockExtension("web-typescript", 4.0, 1, 0, "test", List.of("Other"), List.of("web"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2, ext3)));

        var searchOptions = searchOptions("javascript", null, 50, 0, null, null, true);
        var result = search.search(searchOptions);
        assertThat(result.getTotalHits()).isEqualTo(1);
        assertThat(getIdFromExtensionHits(result.getHits(), 0)).isEqualTo(getIdFromExtensionName("web-javascript"));
    }

    @Test
    void testWebOnlyFilterFalse() {
        var ext1 = mockExtension("web-ext", 4.0, 1, 0, "test", List.of("Other"), List.of("web"));
        var ext2 = mockExtension("desktop-ext", 4.0, 1, 0, "test", List.of("Other"), List.of("ui"));
        Mockito.when(repositories.findAllActiveExtensions()).thenReturn(Streamable.of(List.of(ext1, ext2)));

        var searchOptions = searchOptions(null, null, 50, 0, null, null, false);
        var result = search.search(searchOptions);
        assertThat(result.getTotalHits()).isEqualTo(2);
    }

    // ---------- UTILITY ----------//

    private ISearchService.Options searchOptions(
            String queryString,
            String category,
            Integer requestedSize,
            Integer requestedOffset,
            String sortOrder,
            String sortBy
    ) {
        return searchOptions(queryString, category, requestedSize, requestedOffset, sortOrder, sortBy, null);
    }

    private ISearchService.Options searchOptions(
            String queryString,
            String category,
            Integer requestedSize,
            Integer requestedOffset,
            String sortOrder,
            String sortBy,
            Boolean webOnly
    ) {
        if(requestedSize == null) {
            requestedSize = 18;
        }
        if(requestedOffset == null) {
            requestedOffset = 0;
        }
        if(sortBy == null) {
            sortBy = SortBy.RELEVANCE;
        }

        return new ISearchService.Options(
                queryString,
                category,
                null,
                requestedSize,
                requestedOffset,
                sortOrder,
                sortBy,
                false,
                null,
                null,
                webOnly
        );
    }

    long getIdFromExtensionHits(List<ExtensionSearch> hits, int index) {
        return hits.get(index).getId();
    }

    long getIdFromExtensionName(String extensionName) {
        return extensionName.hashCode();
    }

    private Extension mockExtension(String name, double averageRating, long ratingCount, int downloadCount,
            String namespaceName, List<String> categories) {
        return mockExtension(name, averageRating, ratingCount, downloadCount, namespaceName, categories, null);
    }

    private Extension mockExtension(String name, double averageRating, long ratingCount, int downloadCount,
            String namespaceName, List<String> categories, List<String> extensionKind) {
        var extension = new Extension();
        extension.setName(name);
        extension.setId(name.hashCode());
        extension.setAverageRating(averageRating);
        extension.setReviewCount(ratingCount);
        extension.setDownloadCount(downloadCount);
        extension.setActive(true);
        Mockito.when(entityManager.merge(extension)).thenReturn(extension);
        var namespace = new Namespace();
        namespace.setName(namespaceName);
        extension.setNamespace(namespace);
        var extVer = new ExtensionVersion();
        extVer.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVer.setCategories(categories);
        extVer.setExtensionKind(extensionKind);
        extVer.setTimestamp(LocalDateTime.parse("2021-10-01T00:00"));
        extVer.setActive(true);
        extVer.setExtension(extension);
        extension.getVersions().add(extVer);
        var user = new UserData();
        var token = new PersonalAccessToken();
        token.setUser(user);
        extVer.setPublishedWith(token);
        Mockito.when(repositories.isVerified(namespace, user)).thenReturn(false);
        Mockito.when(repositories.findLatestVersion(extension, null, false, true)).thenReturn(extVer);
        return extension;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        DatabaseSearchService searchService(RelevanceService relevanceService, RepositoryService repositories) {
            return new DatabaseSearchService(relevanceService, repositories);
        }

        @Bean
        RelevanceService relevanceService(RepositoryService repositories) {
            return new RelevanceService(repositories);
        }

        @Bean
        LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKeyGenerator() {
            return new LatestExtensionVersionCacheKeyGenerator();
        }
    }
}