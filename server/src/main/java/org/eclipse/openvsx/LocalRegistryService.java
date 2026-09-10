/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import org.eclipse.openvsx.accesstoken.AccessTokenAction;
import org.eclipse.openvsx.accesstoken.AccessTokenService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.publish.ExtensionVersionIntegrityService;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.ExtensionSearch;
import org.eclipse.openvsx.search.ISearchService;
import org.eclipse.openvsx.search.SearchResult;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.search.SimilarityCheckService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.trustedpublishing.TrustedPublishingConfig;
import org.eclipse.openvsx.util.ChangesCursor;
import org.eclipse.openvsx.util.DrainOnCloseInputStream;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.eclipse.openvsx.util.VersionAlias;
import org.eclipse.openvsx.util.VersionService;
import org.eclipse.openvsx.util.auth.AuthenticatedUser;
import org.eclipse.openvsx.util.auth.LoggedInAuthentication;

import static org.eclipse.openvsx.cache.CacheService.*;
import static org.eclipse.openvsx.entities.FileResource.*;
import static org.eclipse.openvsx.util.UrlUtil.*;

@Component
public class LocalRegistryService implements IExtensionRegistry {

    protected final Logger logger = LoggerFactory.getLogger(LocalRegistryService.class);

    private static final String ACCESS_TOKEN_ERROR = "Invalid access token.";

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private final ExtensionService extensions;
    private final VersionService versions;
    private final UserService users;
    private final AccessTokenService tokens;
    private final SearchUtilService search;
    private final ExtensionValidator validator;
    private final StorageUtilService storageUtil;
    private final EclipseService eclipse;
    private final CacheService cache;
    private final ExtensionVersionIntegrityService integrityService;
    private final SimilarityCheckService similarityCheckService;
    private final PublishingConfig publishingConfig;
    private final TrustedPublishingConfig trustedPublishingConfig;

    /**
     * How far behind the present the changes feed stops, see {@link #visibleUntil}.
     */
    private final Duration changesFeedLag;

    public LocalRegistryService(
            EntityManager entityManager,
            RepositoryService repositories,
            ExtensionService extensions,
            VersionService versions,
            UserService users,
            AccessTokenService tokens,
            SearchUtilService search,
            ExtensionValidator validator,
            StorageUtilService storageUtil,
            EclipseService eclipse,
            CacheService cache,
            ExtensionVersionIntegrityService integrityService,
            @Nullable SimilarityCheckService similarityCheckService,
            PublishingConfig publishingConfig,
            TrustedPublishingConfig trustedPublishingConfig,
            @Value("${ovsx.changes-feed.lag:PT30S}") Duration changesFeedLag
    ) {
        this.entityManager = entityManager;
        this.repositories = repositories;
        this.extensions = extensions;
        this.versions = versions;
        this.users = users;
        this.tokens = tokens;
        this.search = search;
        this.validator = validator;
        this.storageUtil = storageUtil;
        this.eclipse = eclipse;
        this.cache = cache;
        this.integrityService = integrityService;
        this.similarityCheckService = similarityCheckService;
        this.publishingConfig = publishingConfig;
        this.trustedPublishingConfig = trustedPublishingConfig;
        this.changesFeedLag = changesFeedLag;
    }

    @Value("${ovsx.webui.url:}")
    String webuiUrl;

    @Value("${ovsx.registry.version:}")
    String registryVersion;

    @Override
    public NamespaceJson getNamespace(String namespaceName) {
        return getNamespace(namespaceName, false);
    }

    /**
     * Build the namespace JSON. When {@code includeInactive} is {@code true}, all extensions of the
     * namespace are listed, including inactive/soft-deleted ones; otherwise only active extensions are
     * listed. Inactive extensions must only be exposed on admin surfaces.
     */
    public NamespaceJson getNamespace(String namespaceName, boolean includeInactive) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new NotFoundException();
        }
        var json = new NamespaceJson();
        json.setName(namespace.getName());
        var extensionsMap = new LinkedHashMap<String, String>();
        var serverUrl = UrlUtil.getBaseUrl();
        var extensionNames = includeInactive
                ? repositories.findAllExtensionNames(namespace)
                : repositories.findActiveExtensionNames(namespace);
        for (var name : extensionNames) {
            String url = createApiUrl(serverUrl, "api", namespace.getName(), name);
            extensionsMap.put(name, url);
        }
        json.setExtensions(extensionsMap);
        json.setVerified(repositories.isVerified(namespace));
        return json;
    }

    @Override
    @Cacheable(value = CACHE_EXTENSION_JSON, keyGenerator = GENERATOR_EXTENSION_JSON)
    public ExtensionJson getExtension(String namespace, String extensionName, String targetPlatform) {
        return getExtension(namespace, extensionName, targetPlatform, VersionAlias.LATEST);
    }

    @Override
    @Cacheable(value = CACHE_EXTENSION_JSON, keyGenerator = GENERATOR_EXTENSION_JSON)
    public ExtensionJson getExtension(String namespace, String extensionName, String targetPlatform, String version) {
        var extVersion = findExtensionVersion(namespace, extensionName, targetPlatform, version);
        var json = toExtensionVersionJson(extVersion, targetPlatform, true);
        json.setDownloads(getDownloads(extVersion.getExtension(), targetPlatform, extVersion.getVersion()));
        return json;
    }

    @Override
    public VersionsJson getVersions(String namespace, String extension, String targetPlatform, int size, int offset) {
        var pageRequest = PageRequest.of((offset / size), size);
        var page = repositories.findActiveVersionStringsSorted(namespace, extension, targetPlatform, pageRequest);

        var json = new VersionsJson();
        json.setOffset((int) page.getPageable().getOffset());
        json.setTotalSize((int) page.getTotalElements());
        var namespaceLowerCase = namespace.toLowerCase();
        var extensionLowerCase = extension.toLowerCase();
        json.setVersions(
                page.get()
                        .collect(
                                Collectors.toMap(
                                        version -> version,
                                        version -> UrlUtil.createApiVersionUrl(
                                                UrlUtil.getBaseUrl(),
                                                namespaceLowerCase,
                                                extensionLowerCase,
                                                targetPlatform,
                                                version),
                                        (v1, v2) -> v1,
                                        LinkedHashMap::new)));

        return json;
    }

    @Override
    public VersionReferencesJson getVersionReferences(
            String namespace,
            String extension,
            String targetPlatform,
            int size,
            int offset
    ) {
        var pageRequest = PageRequest.of((offset / size), size);
        var page = targetPlatform == null
                ? repositories.findActiveVersionsSorted(namespace, extension, pageRequest)
                : repositories.findActiveVersionsSorted(namespace, extension, targetPlatform, pageRequest);

        var fileUrls = storageUtil.getFileUrls(page.getContent(), UrlUtil.getBaseUrl(), withFileTypes(DOWNLOAD));

        var json = new VersionReferencesJson();
        json.setOffset((int) page.getPageable().getOffset());
        json.setTotalSize((int) page.getTotalElements());
        json.setVersions(
                page.get()
                        .map(extVersion -> {
                            var versionRef = new VersionReferenceJson();
                            versionRef.setVersion(extVersion.getVersion());
                            versionRef.setTargetPlatform(extVersion.getTargetPlatform());
                            versionRef.setEngines(extVersion.getEnginesMap());
                            versionRef.setUrl(UrlUtil.createApiVersionUrl(UrlUtil.getBaseUrl(), extVersion));
                            var files = fileUrls.get(extVersion.getId());
                            if (files.containsKey(DOWNLOAD_SIG)) {
                                files.put(PUBLIC_KEY, UrlUtil.getPublicKeyUrl(extVersion));
                            }

                            versionRef.setFiles(files);
                            return versionRef;
                        })
                        .toList());

        return json;
    }

    private Map<String, String> getDownloads(Extension extension, String targetPlatform, String version) {
        var extVersions = repositories.findVersionsForUrls(extension, targetPlatform, version);
        var fileUrls = storageUtil.getFileUrls(extVersions, UrlUtil.getBaseUrl(), DOWNLOAD);
        return extVersions.stream()
                .map(ev -> {
                    var files = fileUrls.get(ev.getId());
                    var download = files != null ? files.get(DOWNLOAD) : null;
                    if (download == null) {
                        logger.warn("Could not find download for: {}", NamingUtil.toLogFormat(ev));
                        return null;
                    } else {
                        return Map.entry(ev.getTargetPlatform(), download);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private ExtensionVersion findExtensionVersion(
            String namespace,
            String extensionName,
            String targetPlatform,
            String version
    ) {
        var extVersion = repositories.findExtensionVersion(namespace, extensionName, targetPlatform, version);
        if (extVersion == null) {
            throw new NotFoundException();
        }

        return extVersion;
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getFile(
            String namespace,
            String extensionName,
            String targetPlatform,
            String version,
            String fileName
    ) {
        var resource = isType(fileName)
                ? repositories.findFileByType(namespace, extensionName, targetPlatform, version, fileName.toLowerCase())
                : repositories.findFileByName(namespace, extensionName, targetPlatform, version, fileName);

        if (resource == null) {
            throw new NotFoundException();
        }
        if (resource.getType().equals(DOWNLOAD)) {
            storageUtil.increaseDownloadCount(resource);
        }

        return storageUtil.getFileResponse(resource);
    }

    public boolean isType(String fileName) {
        var expectedTypes = new ArrayList<>(
                List.of(MANIFEST, README, LICENSE, ICON, DOWNLOAD, DOWNLOAD_SHA256, CHANGELOG, VSIXMANIFEST));
        if (integrityService.isEnabled()) {
            expectedTypes.add(DOWNLOAD_SIG);
        }

        return expectedTypes.stream().anyMatch(fileName::equalsIgnoreCase);
    }

    @Override
    public ReviewListJson getReviews(String namespaceName, String extensionName) {
        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null || !extension.isActive()) {
            throw new NotFoundException();
        }
        var list = new ReviewListJson();
        var serverUrl = UrlUtil.getBaseUrl();
        list.setPostUrl(
                createApiUrl(serverUrl, "api", extension.getNamespace().getName(), extension.getName(), "review"));
        list.setDeleteUrl(
                createApiUrl(
                        serverUrl,
                        "api",
                        extension.getNamespace().getName(),
                        extension.getName(),
                        "review",
                        "delete"));
        list.setReviews(
                repositories.findActiveReviews(extension)
                        .map(ExtensionReview::toReviewJson)
                        .toList());
        return list;
    }

    @Override
    public SearchResultJson search(ISearchService.Options options) {
        var json = new SearchResultJson();
        var size = options.requestedSize();
        if (size <= 0 || !search.isEnabled()) {
            json.setExtensions(Collections.emptyList());
            return json;
        }

        var result = search.search(options);
        if (result.hasSearchHits()) {
            json.setExtensions(toSearchEntries(result, options));
            json.setOffset(options.requestedOffset());
            json.setTotalSize((int) result.getTotalHits());
        } else {
            json.setExtensions(Collections.emptyList());
        }

        return json;
    }

    @Override
    public QueryResultJson query(QueryRequest request) {
        if (!StringUtils.isEmpty(request.extensionId())) {
            var extensionId = NamingUtil.fromExtensionId(request.extensionId());
            if (extensionId == null) {
                throw new ErrorResultException(
                        "The 'extensionId' parameter must have the format 'namespace.extension'.");
            }
            if (!StringUtils.isEmpty(request.namespaceName())
                    && !request.namespaceName().equals(extensionId.namespace())) {
                throw new ErrorResultException("Conflicting parameters 'extensionId' and 'namespaceName'");
            }
            if (!StringUtils.isEmpty(request.extensionName())
                    && !request.extensionName().equals(extensionId.extension())) {
                throw new ErrorResultException("Conflicting parameters 'extensionId' and 'extensionName'");
            }

            request = new QueryRequest(
                    extensionId.namespace(),
                    extensionId.extension(),
                    request.extensionVersion(),
                    null,
                    request.extensionUuid(),
                    request.namespaceUuid(),
                    request.includeAllVersions(),
                    request.targetPlatform(),
                    request.size(),
                    request.offset());
        }

        if (!TargetPlatform.isValid(request.targetPlatform())) {
            request = new QueryRequest(
                    request.namespaceName(),
                    request.extensionName(),
                    request.extensionVersion(),
                    request.extensionId(),
                    request.extensionUuid(),
                    request.namespaceUuid(),
                    request.includeAllVersions(),
                    null,
                    request.size(),
                    request.offset());
        }

        var extensionVersionsPage = repositories.findActiveVersions(request);
        var extensionVersions = extensionVersionsPage.getContent();
        var extensionIds = extensionVersions.stream()
                .map(ev -> ev.getExtension().getId())
                .collect(Collectors.toSet());

        var reviewCounts = getReviewCounts(extensionVersions);
        var versionStrings = getVersionStrings(extensionIds, request.targetPlatform());
        var latestVersions = getLatestVersions(extensionVersions);
        var latestPreReleases = getLatestVersions(extensionVersions, true);
        var previewsByExtensionId = getPreviews(extensionIds);
        var fileResourcesByExtensionVersionId = getFileResources(extensionVersions);
        var membershipsByNamespaceId = getMemberships(extensionVersions);
        var targetPlatform = request.targetPlatform();

        var result = new QueryResultJson();
        result.setOffset((int) extensionVersionsPage.getPageable().getOffset());
        result.setTotalSize((int) extensionVersionsPage.getTotalElements());
        result.setExtensions(
                extensionVersions.stream()
                        .map(ev -> {
                            var latest = latestVersions.get(getLatestVersionKey(ev));
                            var latestPreRelease = latestPreReleases.get(getLatestVersionKey(ev));
                            var reviewCount = reviewCounts.getOrDefault(ev.getExtension().getId(), 0L);
                            var preview = previewsByExtensionId.get(ev.getExtension().getId());
                            if (preview == null) {
                                logger.warn(
                                        "Unable to determine if extension {} is a preview version",
                                        NamingUtil.toLogFormat(ev));
                                preview = Boolean.FALSE;
                            }
                            var extensionVersionStrings = versionStrings.get(ev.getExtension().getId());
                            var fileResources = fileResourcesByExtensionVersionId
                                    .getOrDefault(ev.getId(), Collections.emptyList());
                            return toExtensionVersionJson(
                                    ev,
                                    latest,
                                    latestPreRelease,
                                    reviewCount,
                                    preview,
                                    extensionVersionStrings,
                                    targetPlatform,
                                    fileResources,
                                    membershipsByNamespaceId);
                        })
                        .toList());

        return result;
    }

    @Override
    public QueryResultJson queryV2(QueryRequestV2 request) {
        request = normalizeQueryRequest(request);
        var queryRequest = request.toQueryRequest();
        var extensionVersionsPage = repositories.findActiveVersions(queryRequest);
        var extensionVersions = extensionVersionsPage.getContent();
        var extensionIds = extensionVersions.stream()
                .map(ev -> ev.getExtension().getId())
                .collect(Collectors.toSet());

        var reviewCounts = getReviewCounts(extensionVersions);
        var addAllVersions = request.includeAllVersions().equals("links");
        var versionStrings = addAllVersions ? getVersionStrings(extensionIds, request.targetPlatform()) : null;
        var latestGlobalVersions = addAllVersions ? getLatestGlobalVersions(extensionVersions) : null;
        var latestGlobalPreReleases = addAllVersions ? getLatestGlobalVersions(extensionVersions, true) : null;

        var latestVersions = getLatestVersions(extensionVersions);
        var latestPreReleases = getLatestVersions(extensionVersions, true);
        var previewsByExtensionId = getPreviews(extensionIds);
        var fileResourcesByExtensionVersionId = getFileResources(extensionVersions);
        var membershipsByNamespaceId = getMemberships(extensionVersions);
        var targetPlatform = request.targetPlatform();

        var result = new QueryResultJson();
        result.setOffset((int) extensionVersionsPage.getPageable().getOffset());
        result.setTotalSize((int) extensionVersionsPage.getTotalElements());
        result.setExtensions(
                extensionVersions.stream()
                        .map(ev -> {
                            var latest = latestVersions.get(getLatestVersionKey(ev));
                            var latestPreRelease = latestPreReleases.get(getLatestVersionKey(ev));
                            var reviewCount = reviewCounts.getOrDefault(ev.getExtension().getId(), 0L);
                            var preview = previewsByExtensionId.get(ev.getExtension().getId());
                            if (preview == null) {
                                logger.warn(
                                        "Unable to determine if extension {} is a preview version",
                                        NamingUtil.toLogFormat(ev));
                                preview = Boolean.FALSE;
                            }
                            var fileResources = fileResourcesByExtensionVersionId
                                    .getOrDefault(ev.getId(), Collections.emptyList());
                            var globalLatest = addAllVersions
                                    ? latestGlobalVersions.get(ev.getExtension().getId())
                                    : null;
                            var globalLatestPreRelease = addAllVersions
                                    ? latestGlobalPreReleases.get(ev.getExtension().getId())
                                    : null;
                            var extensionVersionStrings = addAllVersions
                                    ? versionStrings.get(ev.getExtension().getId())
                                    : null;

                            return toExtensionVersionJsonV2(
                                    ev,
                                    latest,
                                    latestPreRelease,
                                    globalLatest,
                                    globalLatestPreRelease,
                                    reviewCount,
                                    preview,
                                    extensionVersionStrings,
                                    targetPlatform,
                                    fileResources,
                                    membershipsByNamespaceId);
                        })
                        .toList());

        return result;
    }

    private QueryRequestV2 normalizeQueryRequest(QueryRequestV2 request) {
        ExtensionId extensionId;
        if (!StringUtils.isEmpty(request.extensionId())) {
            extensionId = NamingUtil.fromExtensionId(request.extensionId());
            if (extensionId == null) {
                throw new ErrorResultException(
                        "The 'extensionId' parameter must have the format 'namespace.extension'.");
            }
            if (!StringUtils.isEmpty(request.namespaceName())
                    && !request.namespaceName().equals(extensionId.namespace())) {
                throw new ErrorResultException("Conflicting parameters 'extensionId' and 'namespaceName'");
            }
            if (!StringUtils.isEmpty(request.extensionName())
                    && !request.extensionName().equals(extensionId.extension())) {
                throw new ErrorResultException("Conflicting parameters 'extensionId' and 'extensionName'");
            }
        } else {
            extensionId = new ExtensionId(request.namespaceName(), request.extensionName());
        }

        var targetPlatform = request.targetPlatform();
        if (!TargetPlatform.isValid(targetPlatform)) {
            targetPlatform = null;
        }

        // Revert to default includeAllVersions value when extensionVersion is set
        var includeAllVersions = request.includeAllVersions();
        if (!StringUtils.isEmpty(request.extensionVersion()) && request.includeAllVersions().equals("true")) {
            includeAllVersions = "links";
        }

        return new QueryRequestV2(
                extensionId.namespace(),
                extensionId.extension(),
                request.extensionVersion(),
                null,
                request.extensionUuid(),
                request.namespaceUuid(),
                includeAllVersions,
                targetPlatform,
                request.size(),
                request.offset());
    }

    @Override
    @Transactional
    @Cacheable(CACHE_NAMESPACE_DETAILS_JSON)
    public NamespaceDetailsJson getNamespaceDetails(String namespaceName) {
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new NotFoundException();
        }

        var logo = namespace.getLogoStorageType() != null
                ? storageUtil.getNamespaceLogoLocation(namespace).toString()
                : null;

        var json = namespace.toNamespaceDetailsJson();
        json.setVerified(repositories.isVerified(namespace));
        json.setLogo(logo);

        var serverUrl = UrlUtil.getBaseUrl();
        var extVersions = repositories.findLatestVersions(namespace);
        var fileUrls = storageUtil.getFileUrls(extVersions, serverUrl, withFileTypes(DOWNLOAD, ICON));
        json.setExtensions(
                extVersions.stream()
                        .map(extVersion -> {
                            var entry = extVersion.toSearchEntryJson();
                            entry.setUrl(createApiUrl(serverUrl, "api", entry.getNamespace(), entry.getName()));
                            var files = fileUrls.get(extVersion.getId());
                            if (files.containsKey(DOWNLOAD_SIG)) {
                                files.put(PUBLIC_KEY, UrlUtil.getPublicKeyUrl(extVersion));
                            }

                            entry.setFiles(files);
                            return entry;
                        })
                        .toList());

        return json;
    }

    private String[] withFileTypes(String... types) {
        var typesList = new ArrayList<>(List.of(types));
        if (typesList.contains(DOWNLOAD)) {
            typesList.add(DOWNLOAD_SHA256);
            if (integrityService.isEnabled()) {
                typesList.add(DOWNLOAD_SIG);
            }
        }

        return typesList.toArray(String[]::new);
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getNamespaceLogo(String namespaceName, String fileName) {
        if (fileName == null) {
            fileName = "";
        }

        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null || !fileName.equals(namespace.getLogoName())) {
            throw new NotFoundException();
        }

        return storageUtil.getNamespaceLogo(namespace);
    }

    private Map<Long, Long> getReviewCounts(List<ExtensionVersion> extensionVersions) {
        if (extensionVersions.isEmpty()) {
            return Collections.emptyMap();
        }

        return extensionVersions.stream()
                .map(ExtensionVersion::getExtension)
                .filter(e -> e.getReviewCount() != null)
                .collect(
                        Collectors
                                .toMap(Extension::getId, Extension::getReviewCount, (reviews1, reviews2) -> reviews1));
    }

    private Map<Long, List<String>> getVersionStrings(Set<Long> extensionIds, String targetPlatform) {
        if (extensionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return repositories.findActiveVersionStringsSorted(extensionIds, targetPlatform);
    }

    private Map<String, ExtensionVersion> getLatestVersions(List<ExtensionVersion> extensionVersions) {
        return getLatestVersions(extensionVersions, false);
    }

    private Map<String, ExtensionVersion> getLatestVersions(
            List<ExtensionVersion> extensionVersions,
            boolean onlyPreRelease
    ) {
        return extensionVersions.stream()
                .collect(Collectors.groupingBy(this::getLatestVersionKey))
                .values()
                .stream()
                .map(list -> versions.getLatest(list, true, onlyPreRelease))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(this::getLatestVersionKey, ev -> ev));
    }

    private Map<Long, ExtensionVersion> getLatestGlobalVersions(List<ExtensionVersion> extensionVersions) {
        return getLatestGlobalVersions(extensionVersions, false);
    }

    private Map<Long, ExtensionVersion> getLatestGlobalVersions(
            List<ExtensionVersion> extensionVersions,
            boolean onlyPreRelease
    ) {
        return extensionVersions.stream()
                .collect(Collectors.groupingBy(ev -> ev.getExtension().getId()))
                .values()
                .stream()
                .map(list -> versions.getLatest(list, false, onlyPreRelease))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ev -> ev.getExtension().getId(), ev -> ev));
    }

    private String getLatestVersionKey(ExtensionVersion extVersion) {
        return extVersion.getExtension().getId() + "@" + extVersion.getTargetPlatform();
    }

    private Map<Long, Boolean> getPreviews(Set<Long> extensionIds) {
        return repositories.findLatestVersionsIsPreview(extensionIds);
    }

    private Map<Long, List<FileResource>> getFileResources(List<ExtensionVersion> extensionVersions) {
        if (extensionVersions.isEmpty()) {
            return Collections.emptyMap();
        }

        var fileTypes = List.of(withFileTypes(DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG, VSIXMANIFEST));
        var extensionVersionIds = extensionVersions.stream()
                .map(ExtensionVersion::getId)
                .collect(Collectors.toSet());

        return repositories.findFileResourcesByExtensionVersionIdAndType(extensionVersionIds, fileTypes).stream()
                .collect(Collectors.groupingBy(fr -> fr.getExtension().getId()));
    }

    private Map<Long, List<NamespaceMembership>> getMemberships(Collection<ExtensionVersion> extensionVersions) {
        if (extensionVersions.isEmpty()) {
            return Collections.emptyMap();
        }

        var namespaceIds = extensionVersions.stream()
                .map(ExtensionVersion::getExtension)
                .map(Extension::getNamespace)
                .map(Namespace::getId)
                .collect(Collectors.toSet());

        return repositories.findNamespaceMemberships(namespaceIds).stream()
                .collect(Collectors.groupingBy(nm -> nm.getNamespace().getId()));
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson createNamespace(NamespaceJson json, String tokenValue) {
        var tau = tokens.useAccessToken(tokenValue, new AccessTokenAction.CreateNamespace(json.getName()));
        if (tau == null) {
            throw new ErrorResultException(ACCESS_TOKEN_ERROR, HttpStatus.UNAUTHORIZED);
        }

        return createNamespace(json, tau.userData());
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson createNamespace(NamespaceJson json, UserData user) {
        var namespaceIssue = validator.validateNamespace(json.getName());
        if (namespaceIssue.isPresent()) {
            throw new ErrorResultException(namespaceIssue.get().toString());
        }

        eclipse.checkPublisherAgreement(user);
        var namespaceName = repositories.findNamespaceName(json.getName());
        if (namespaceName != null) {
            throw new ErrorResultException("Namespace already exists: " + namespaceName);
        }

        // Check if the proposed namespace name is too similar to existing ones (if enabled)
        if (similarityCheckService != null && similarityCheckService.isEnabled()) {
            var similarNamespaces = similarityCheckService.findSimilarNamespacesForCreation(json.getName(), user);
            if (!similarNamespaces.isEmpty()) {
                var similarNames = similarNamespaces.stream()
                        .map(Namespace::getName)
                        .collect(Collectors.joining(", "));
                throw new ErrorResultException(
                        "Namespace name '" + json.getName() + "' is too similar to existing namespace(s): "
                                + similarNames + ". " +
                                "Please choose a more distinct name to avoid confusion. " +
                                "Refer to the publishing guidelines: https://github.com/EclipseFdn/open-vsx.org/wiki/Publishing-Extensions");
            }
        }

        // Create the requested namespace
        var namespace = new Namespace();
        namespace.setName(json.getName());
        entityManager.persist(namespace);

        // Assign the requesting user as contributor
        var membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUser(user);
        membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        entityManager.persist(membership);

        return ResultJson.success("Created namespace " + namespace.getName());
    }

    /**
     * Soft-deletes extension versions on behalf of the user the given personal access token belongs to.
     * <p>
     * This is the token-authenticated counterpart of {@code UserAPI.deleteExtension}, which authenticates
     * the user via their login session, and applies the same authorization: only namespace members may
     * delete, owners may delete any version while other members are restricted to the versions they
     * published themselves.
     *
     * @param targetVersions the versions to delete, or {@code null} to delete the extension as a whole
     */
    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson deleteExtension(
            String namespaceName,
            String extensionName,
            @Nullable List<TargetPlatformVersionJson> targetVersions,
            String tokenValue
    ) {
        var tau = tokens
                .useAccessToken(tokenValue, new AccessTokenAction.DeleteVersion(namespaceName, extensionName));
        if (tau == null) {
            throw new ErrorResultException(ACCESS_TOKEN_ERROR);
        }

        return extensions.deleteExtensionAsUser(tau.userData(), namespaceName, extensionName, targetVersions);
    }

    public ResultJson verifyToken(String namespaceName, String tokenValue) {
        var tau = tokens.useAccessToken(tokenValue, new AccessTokenAction.Verify());
        if (tau == null) {
            throw new ErrorResultException(ACCESS_TOKEN_ERROR, HttpStatus.UNAUTHORIZED);
        }

        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new NotFoundException();
        }
        if (!users.hasPublishPermission(tau.userData(), namespace)) {
            throw new ErrorResultException(
                    "Insufficient access rights for namespace: " + namespace.getName(),
                    HttpStatus.FORBIDDEN);
        }

        return ResultJson.success("Valid token");
    }

    public ExtensionJson publish(InputStream content, LoggedInAuthentication liu) throws ErrorResultException {
        return doPublish(content, null, liu);
    }

    public ExtensionJson publish(InputStream rawContent, String tokenValue) throws ErrorResultException {
        return doPublish(rawContent, tokenValue, null);
    }

    private ExtensionJson doPublish(InputStream rawContent, String tokenValue, AuthenticatedUser auth)
            throws ErrorResultException {
        // A rejection anywhere below - invalid/expired token, missing publisher agreement, or
        // (pre-existing, inside extensions.publishVersion) an oversized package - can happen before
        // the request body has been fully read. Wrapping it once here and relying on
        // try-with-resources to always close it, on every exit path, drains whatever is left
        // automatically instead of requiring each rejection site to remember to do it, so a LB/proxy
        // doesn't see an early response to a request whose body is still arriving and mistake it
        // for a 50x. Capped at the same max upload size a successful publish already reads in full,
        // so an oversized/abusive body doesn't tie up the request thread and bandwidth beyond that.
        try (var content = new DrainOnCloseInputStream(rawContent, publishingConfig.getMaxContentSize())) {
            var tempFile = extensions.createExtensionFile(content);
            try {
                AuthenticatedUser au = auth;
                ExtensionVersion extVersion;
                try (var processor = new ExtensionProcessor(tempFile)) {
                    // now that we know the details, ensure token is still fine
                    if (au == null) {
                        au = tokens.useAccessToken(
                                tokenValue,
                                new AccessTokenAction.PublishVersion(
                                        processor.getNamespace(),
                                        processor.getExtensionName()));
                    }
                    if (au == null) {
                        throw new ErrorResultException(ACCESS_TOKEN_ERROR, HttpStatus.UNAUTHORIZED);
                    }
                    // Check whether the user has a valid publisher agreement
                    eclipse.checkPublisherAgreement(au.userData());

                    extVersion = extensions.publishVersion(processor, au);
                }

                var json = toExtensionVersionJson(extVersion, null, true);
                json.setSuccess("It can take a couple minutes before the extension version is available");

                if (repositories.hasSameVersion(extVersion)) {
                    var existingRelease = extVersion.isPreRelease() ? "stable release" : "pre-release";
                    var thisRelease = extVersion.isPreRelease() ? "pre-release" : "stable release";
                    var extension = extVersion.getExtension();
                    var semver = extVersion.getSemanticVersion();
                    var newVersion = String
                            .join(".", String.valueOf(semver.getMajor()), String.valueOf(semver.getMinor() + 1), "0");

                    json.setWarning(
                            "A " + existingRelease + " already exists for "
                                    + NamingUtil.toLogFormat(
                                            extension.getNamespace().getName(),
                                            extension.getName(),
                                            extVersion.getVersion())
                                    + ".\n" +
                                    "To prevent update conflicts, we recommend that this " + thisRelease + " uses "
                                    + newVersion + " as its version instead.");
                }

                return json;
            } catch (RuntimeException exc) {
                // extensions.publishVersion(...) either hands tempFile off to the async publish
                // pipeline (which deletes it once done reading it) on success, or already deletes it
                // itself before rethrowing on failure - so this only ever actually deletes anything
                // when we reject *before* reaching that call (invalid token, no publisher agreement).
                // Deleting an already-deleted file is a no-op, so it's safe to always try here rather
                // than track exactly which path already handled it.
                IOUtils.closeQuietly(tempFile);
                throw exc;
            }
        } catch (IOException e) {
            throw new ErrorResultException("Failed to read extension file", e);
        }
    }

    @Transactional(rollbackOn = ResponseStatusException.class)
    public ResultJson postReview(ReviewJson review, String namespace, String extensionName) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null || !extension.isActive()) {
            var extensionId = NamingUtil.toExtensionId(namespace, extensionName);
            return ResultJson.error("Extension not found: " + extensionId);
        }
        if (repositories.hasActiveReview(extension, user)) {
            return ResultJson.error("You must not submit more than one review for an extension.");
        }

        var extReview = new ExtensionReview();
        extReview.setExtension(extension);
        extReview.setActive(true);
        extReview.setTimestamp(TimeUtil.getCurrentUTC());
        extReview.setUser(user);
        extReview.setTitle(review.getTitle());
        extReview.setComment(review.getComment());
        extReview.setRating(review.getRating());
        entityManager.persist(extReview);
        extension.setAverageRating(repositories.getAverageReviewRating(extension));
        extension.setReviewCount(repositories.countActiveReviews(extension));
        search.updateSearchEntry(extension);
        cache.evictExtensionJsons(extension);
        cache.evictLatestExtensionVersion(extension);
        return ResultJson.success("Added review for " + NamingUtil.toExtensionId(extension));
    }

    @Transactional(rollbackOn = ResponseStatusException.class)
    public ResultJson deleteReview(String namespace, String extensionName) {
        var user = users.findLoggedInUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null || !extension.isActive()) {
            return ResultJson.error("Extension not found: " + NamingUtil.toExtensionId(namespace, extensionName));
        }
        var activeReviews = repositories.findActiveReviews(extension, user);
        if (activeReviews.isEmpty()) {
            return ResultJson.error("You have not submitted any review yet.");
        }

        for (var extReview : activeReviews) {
            extReview.setActive(false);
        }

        extension.setAverageRating(repositories.getAverageReviewRating(extension));
        extension.setReviewCount(repositories.countActiveReviews(extension));
        search.updateSearchEntry(extension);
        cache.evictExtensionJsons(extension);
        cache.evictLatestExtensionVersion(extension);
        return ResultJson.success("Deleted review for " + NamingUtil.toExtensionId(extension));
    }

    private LinkedHashMap<Long, ExtensionVersion> getLatestVersions(SearchResult result) {
        var ids = result.getHits().stream()
                .map(ExtensionSearch::getId)
                .distinct()
                .collect(Collectors.toList());

        var latestVersions = findLatestVersions(ids);
        ids.removeAll(latestVersions.keySet());
        if (!ids.isEmpty()) {
            search.removeSearchEntries(ids);
        }

        return latestVersions;
    }

    private LinkedHashMap<Long, ExtensionVersion> findLatestVersions(Collection<Long> ids) {
        var extById = repositories.findLatestVersions(ids).stream()
                .collect(Collectors.toMap(ev -> ev.getExtension().getId(), ev -> ev));

        // use LinkedHashMap to order latest versions by extension id
        var map = new LinkedHashMap<Long, ExtensionVersion>();
        ids.forEach(id -> {
            var latest = extById.get(id);
            if (latest != null) {
                map.put(id, latest);
            }
        });

        return map;
    }

    private List<SearchEntryJson> toSearchEntries(SearchResult result, ISearchService.Options options) {
        var serverUrl = UrlUtil.getBaseUrl();
        var latestVersions = getLatestVersions(result);
        var membershipsByNamespaceId = getMemberships(latestVersions.values());
        var searchEntries = latestVersions.entrySet().stream()
                .map(e -> {
                    var entry = e.getValue().toSearchEntryJson();
                    entry.setUrl(createApiUrl(serverUrl, "api", entry.getNamespace(), entry.getName()));
                    entry.setVerified(isVerified(e.getValue(), membershipsByNamespaceId));
                    return Map.entry(e.getKey(), entry);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var fileUrls = storageUtil.getFileUrls(latestVersions.values(), serverUrl, withFileTypes(DOWNLOAD, ICON));
        searchEntries.forEach((extensionId, searchEntry) -> {
            var extVersion = latestVersions.get(extensionId);
            var files = fileUrls.get(extVersion.getId());
            if (files.containsKey(DOWNLOAD_SIG)) {
                files.put(PUBLIC_KEY, UrlUtil.getPublicKeyUrl(extVersion));
            }

            searchEntry.setFiles(files);
        });
        if (options.includeAllVersions()) {
            var activeVersions = repositories.findActiveVersionReferencesSorted(latestVersions.keySet());
            var activeVersionsByExtensionId = activeVersions.stream()
                    .collect(Collectors.groupingBy(ev -> ev.getExtension().getId()));
            var versionFileUrls = storageUtil.getFileUrls(activeVersions, serverUrl, withFileTypes(DOWNLOAD));
            for (var extensionId : latestVersions.keySet()) {
                var extVersions = activeVersionsByExtensionId.get(extensionId);
                var searchEntry = searchEntries.get(extensionId);
                searchEntry.setAllVersions(getAllVersionReferences(extVersions, versionFileUrls, serverUrl));
                searchEntry.setAllVersionsUrl(
                        UrlUtil.createAllVersionsUrl(
                                searchEntry.getNamespace(),
                                searchEntry.getName(),
                                options.targetPlatform(),
                                "version-references"));
            }
        }

        return latestVersions.keySet().stream()
                .map(searchEntries::get)
                .collect(Collectors.toList());
    }

    private List<VersionReferenceJson> getAllVersionReferences(
            List<ExtensionVersion> extVersions,
            Map<Long, Map<String, String>> versionFileUrls,
            String serverUrl
    ) {
        return extVersions.stream().map(extVersion -> {
            var ref = new VersionReferenceJson();
            ref.setVersion(extVersion.getVersion());
            ref.setTargetPlatform(extVersion.getTargetPlatform());
            ref.setEngines(extVersion.getEnginesMap());
            ref.setUrl(UrlUtil.createApiVersionUrl(serverUrl, extVersion));
            var files = versionFileUrls.get(extVersion.getId());
            if (files.containsKey(DOWNLOAD_SIG)) {
                files.put(PUBLIC_KEY, UrlUtil.getPublicKeyUrl(extVersion));
            }

            ref.setFiles(files);
            return ref;
        }).collect(Collectors.toList());
    }

    public ExtensionJson toExtensionVersionJson(
            ExtensionVersion extVersion,
            String targetPlatform,
            boolean onlyActive
    ) {
        var extension = extVersion.getExtension();
        var latest = repositories.findLatestVersionForAllUrls(extension, targetPlatform, false, onlyActive);
        var latestPreRelease = repositories.findLatestVersionForAllUrls(extension, targetPlatform, true, onlyActive);

        var json = extVersion.toExtensionJson();
        json.setReplacement(toReplacementJson(extension, targetPlatform, onlyActive, true));
        json.setPreview(latest != null && latest.isPreview());
        var versionAlias = new ArrayList<String>();
        if (latest != null && extVersion.getVersion().equals(latest.getVersion())) {
            versionAlias.add(VersionAlias.LATEST);
        }
        if (latestPreRelease != null && extVersion.getVersion().equals(latestPreRelease.getVersion())) {
            versionAlias.add(VersionAlias.PRE_RELEASE);
        }

        json.setVersionAlias(versionAlias);
        json.setVerified(repositories.isVerifiedPublisher(extVersion));
        json.setReviewCount(Optional.ofNullable(extension.getReviewCount()).orElse(0L));
        var serverUrl = UrlUtil.getBaseUrl();
        json.setNamespaceUrl(createApiUrl(serverUrl, "api", json.getNamespace()));
        json.setReviewsUrl(createApiReviewsUrl(serverUrl, json.getNamespace(), json.getName()));

        var allVersions = new ArrayList<String>();
        if (latest != null) {
            allVersions.add(VersionAlias.LATEST);
        }
        if (latestPreRelease != null) {
            allVersions.add(VersionAlias.PRE_RELEASE);
        }

        var versionBaseUrl = UrlUtil
                .createApiVersionBaseUrl(serverUrl, json.getNamespace(), json.getName(), targetPlatform);
        allVersions.addAll(repositories.findVersionStringsSorted(extension, targetPlatform, onlyActive));
        json.setAllVersionsUrl(UrlUtil.createAllVersionsUrl(json.getNamespace(), json.getName(), targetPlatform));
        var allVersionsJson = new LinkedHashMap<String, String>(allVersions.size());
        for (var version : allVersions) {
            allVersionsJson.put(version, createApiUrl(versionBaseUrl, version));
        }

        json.setAllVersions(allVersionsJson);
        // getFiles rather than getFileUrls: the package size is read from the same rows as the URLs, so
        // this path stays one query, and toFilesJson is the same mapping the two builders below already
        // use - including the public key it adds alongside a signature.
        var resources = storageUtil
                .getFiles(
                        List.of(extVersion),
                        withFileTypes(DOWNLOAD, MANIFEST, ICON, README, LICENSE, CHANGELOG, VSIXMANIFEST))
                .get(extVersion.getId());
        json.setFiles(toFilesJson(extVersion, resources, UrlUtil.createApiFileBaseUrl(serverUrl, extVersion)));
        json.setDownloadSize(downloadSize(resources));
        if (json.getDependencies() != null) {
            for (var ref : json.getDependencies()) {
                ref.setUrl(createApiUrl(serverUrl, "api", ref.getNamespace(), ref.getExtension()));
            }
        }
        if (json.getBundledExtensions() != null) {
            for (var ref : json.getBundledExtensions()) {
                ref.setUrl(createApiUrl(serverUrl, "api", ref.getNamespace(), ref.getExtension()));
            }
        }
        return json;
    }

    public ExtensionJson toExtensionVersionJson(
            ExtensionVersion extVersion,
            ExtensionVersion latest,
            ExtensionVersion latestPreRelease,
            long reviewCount,
            boolean preview,
            List<String> versions,
            String targetPlatformParam,
            List<FileResource> resources,
            Map<Long, List<NamespaceMembership>> membershipsByNamespaceId
    ) {
        var json = extVersion.toExtensionJson();
        json.setPreview(preview);
        json.setVerified(isVerified(extVersion, membershipsByNamespaceId));
        json.setReviewCount(reviewCount);
        var serverUrl = UrlUtil.getBaseUrl();
        json.setNamespaceUrl(createApiUrl(serverUrl, "api", json.getNamespace()));
        json.setReviewsUrl(createApiReviewsUrl(serverUrl, json.getNamespace(), json.getName()));

        var extension = extVersion.getExtension();
        json.setReplacement(toReplacementJson(extension, targetPlatformParam, true, false));

        var versionAlias = new ArrayList<String>(2);
        if (extVersion.equals(latest)) {
            versionAlias.add(VersionAlias.LATEST);
        }
        if (extVersion.equals(latestPreRelease)) {
            versionAlias.add(VersionAlias.PRE_RELEASE);
        }

        json.setVersionAlias(versionAlias);
        var allVersions = new ArrayList<String>();
        if (latest != null) {
            allVersions.add(VersionAlias.LATEST);
        }
        if (latestPreRelease != null) {
            allVersions.add(VersionAlias.PRE_RELEASE);
        }
        if (versions != null && !versions.isEmpty()) {
            allVersions.addAll(versions);
        }

        json.setAllVersionsUrl(UrlUtil.createAllVersionsUrl(json.getNamespace(), json.getName(), targetPlatformParam));
        var allVersionsJson = new LinkedHashMap<String, String>(allVersions.size());
        var versionBaseUrl = UrlUtil
                .createApiVersionBaseUrl(serverUrl, json.getNamespace(), json.getName(), targetPlatformParam);
        for (var version : allVersions) {
            allVersionsJson.put(version, createApiUrl(versionBaseUrl, version));
        }

        json.setAllVersions(allVersionsJson);
        var files = new LinkedHashMap<String, String>(8);
        var fileBaseUrl = UrlUtil.createApiFileBaseUrl(
                serverUrl,
                json.getNamespace(),
                json.getName(),
                json.getTargetPlatform(),
                json.getVersion());
        for (var resource : resources) {
            var fileUrl = UrlUtil.createApiFileUrl(fileBaseUrl, resource.getName());
            files.put(resource.getType(), fileUrl);
        }
        if (files.containsKey(DOWNLOAD_SIG)) {
            files.put(PUBLIC_KEY, UrlUtil.getPublicKeyUrl(extVersion));
        }

        json.setFiles(files);
        json.setDownloadSize(downloadSize(resources));
        if (json.getDependencies() != null) {
            for (var ref : json.getDependencies()) {
                ref.setUrl(createApiUrl(serverUrl, "api", ref.getNamespace(), ref.getExtension()));
            }
        }
        if (json.getBundledExtensions() != null) {
            for (var ref : json.getBundledExtensions()) {
                ref.setUrl(createApiUrl(serverUrl, "api", ref.getNamespace(), ref.getExtension()));
            }
        }
        return json;
    }

    public ExtensionJson toExtensionVersionJsonV2(
            ExtensionVersion extVersion,
            ExtensionVersion latest,
            ExtensionVersion latestPreRelease,
            ExtensionVersion globalLatest,
            ExtensionVersion globalLatestPreRelease,
            long reviewCount,
            boolean preview,
            List<String> versions,
            String targetPlatformParam,
            List<FileResource> resources,
            Map<Long, List<NamespaceMembership>> membershipsByNamespaceId
    ) {
        var json = extVersion.toExtensionJson();
        json.setPreview(preview);
        json.setVerified(isVerified(extVersion, membershipsByNamespaceId));
        json.setReviewCount(reviewCount);
        var serverUrl = UrlUtil.getBaseUrl();
        json.setNamespaceUrl(createApiUrl(serverUrl, "api", json.getNamespace()));
        json.setReviewsUrl(createApiReviewsUrl(serverUrl, json.getNamespace(), json.getName()));
        json.setUrl(createApiVersionUrl(serverUrl, json));

        var extension = extVersion.getExtension();
        json.setReplacement(toReplacementJson(extension, targetPlatformParam, true, false));
        json.setVersionAlias(getVersionAlias(extVersion, latest, latestPreRelease));
        json.setAllVersionsUrl(UrlUtil.createAllVersionsUrl(json.getNamespace(), json.getName(), targetPlatformParam));
        var versionBaseUrl = UrlUtil
                .createApiVersionBaseUrl(serverUrl, json.getNamespace(), json.getName(), targetPlatformParam);
        json.setAllVersions(toAllVersionsJson(versions, versionBaseUrl, globalLatest, globalLatestPreRelease));
        var fileBaseUrl = UrlUtil.createApiFileBaseUrl(
                serverUrl,
                json.getNamespace(),
                json.getName(),
                json.getTargetPlatform(),
                json.getVersion());
        json.setFiles(toFilesJson(extVersion, resources, fileBaseUrl));
        json.setDownloadSize(downloadSize(resources));
        setExtensionReferenceUrls(json.getDependencies(), serverUrl);
        setExtensionReferenceUrls(json.getBundledExtensions(), serverUrl);
        return json;
    }

    private void setExtensionReferenceUrls(List<ExtensionReferenceJson> refs, String serverUrl) {
        if (refs == null) {
            return;
        }

        for (var ref : refs) {
            ref.setUrl(createApiUrl(serverUrl, "api", ref.getNamespace(), ref.getExtension()));
        }
    }

    private List<String> getVersionAlias(
            ExtensionVersion extVersion,
            ExtensionVersion latest,
            ExtensionVersion latestPreRelease
    ) {
        var versionAlias = new ArrayList<String>(2);
        if (extVersion.equals(latest)) {
            versionAlias.add(VersionAlias.LATEST);
        }
        if (extVersion.equals(latestPreRelease)) {
            versionAlias.add(VersionAlias.PRE_RELEASE);
        }

        return versionAlias;
    }

    private Map<String, String> toAllVersionsJson(
            List<String> versions,
            String versionBaseUrl,
            ExtensionVersion globalLatest,
            ExtensionVersion globalLatestPreRelease
    ) {
        var allVersions = new ArrayList<String>();
        if (globalLatest != null) {
            allVersions.add(VersionAlias.LATEST);
        }
        if (globalLatestPreRelease != null) {
            allVersions.add(VersionAlias.PRE_RELEASE);
        }
        if (versions != null && !versions.isEmpty()) {
            allVersions.addAll(versions);
        }
        if (allVersions.isEmpty()) {
            return null;
        }

        var allVersionsJson = new LinkedHashMap<String, String>(allVersions.size());
        for (var version : allVersions) {
            allVersionsJson.put(version, createApiUrl(versionBaseUrl, version));
        }

        return allVersionsJson;
    }

    /**
     * The size of the extension package itself, read from the {@code download} resource rather than summed
     * over the version's files: readme, icon, changelog and license are stored as rows of their own but
     * hold content that is already inside the package, so a sum would count it twice. Null where the
     * package predates the size column and has not been backfilled yet.
     */
    private Long downloadSize(List<FileResource> resources) {
        return resources.stream()
                .filter(resource -> DOWNLOAD.equals(resource.getType()))
                .findFirst()
                .map(FileResource::getSize)
                .orElse(null);
    }

    private Map<String, String> toFilesJson(
            ExtensionVersion extVersion,
            List<FileResource> resources,
            String fileBaseUrl
    ) {
        var files = new LinkedHashMap<String, String>(8);
        for (var resource : resources) {
            var fileUrl = UrlUtil.createApiFileUrl(fileBaseUrl, resource.getName());
            files.put(resource.getType(), fileUrl);
        }
        if (files.containsKey(DOWNLOAD_SIG)) {
            files.put(PUBLIC_KEY, UrlUtil.getPublicKeyUrl(extVersion));
        }

        return files;
    }

    private ExtensionReplacementJson toReplacementJson(
            Extension extension,
            String targetPlatform,
            boolean onlyActive,
            boolean webui
    ) {
        if (extension.getReplacement() == null) {
            return null;
        }

        var replacementId = extension.getReplacement().getId();
        var replacement = repositories.findLatestReplacement(replacementId, targetPlatform, false, onlyActive);
        if (replacement == null) {
            return null;
        }

        var baseUrl = webui ? webuiUrl : UrlUtil.getBaseUrl();
        var segments = new String[] {
            webui ? "extension" : "api",
            replacement.getExtension().getNamespace().getName(),
            replacement.getExtension().getName()
        };

        var displayName = StringUtils.isNotEmpty(replacement.getDisplayName())
                ? replacement.getDisplayName()
                : replacement.getExtension().getName();

        var json = new ExtensionReplacementJson();
        json.setUrl(UrlUtil.createApiUrl(baseUrl, segments));
        json.setDisplayName(displayName);
        return json;
    }

    private boolean isVerified(
            ExtensionVersion extVersion,
            Map<Long, List<NamespaceMembership>> membershipsByNamespaceId
    ) {
        if (extVersion.getPublishedBy() == null) {
            return false;
        }

        var user = extVersion.getPublishedBy();
        if (user.isPrivileged()) {
            return true;
        }

        var namespace = extVersion.getExtension().getNamespace().getId();
        var memberships = membershipsByNamespaceId.getOrDefault(namespace, Collections.emptyList());
        return memberships.stream().anyMatch(m -> m.getRole().equalsIgnoreCase(NamespaceMembership.ROLE_OWNER))
                && memberships.stream().anyMatch(m -> m.getUser().getId() == user.getId());
    }

    public String getPublicKey(String publicId) {
        var keyPair = repositories.findKeyPair(publicId);
        if (keyPair == null) {
            throw new NotFoundException();
        }

        return keyPair.getPublicKeyText();
    }

    @Override
    public RegistryVersionJson getRegistryVersion() {
        if (StringUtils.isEmpty(registryVersion)) {
            throw new NotFoundException();
        }

        var json = new RegistryVersionJson();
        json.setVersion(registryVersion);
        json.setMaxExtensionSize(publishingConfig.getMaxContentSize());
        json.setTrustedPublishingAudience(
                trustedPublishingConfig.isEnabled() ? trustedPublishingConfig.getAudience() : null);
        return json;
    }

    /**
     * Feed of the extension versions that are or were publicly available, so that consumers such as
     * mirrors or security scanners can follow what the registry publishes and removes without polling
     * every extension.
     */
    public ChangesResultJson getChanges(LocalDateTime since, LocalDateTime until, ChangesCursor after, int size) {
        var page = repositories.findChanges(
                since,
                visibleUntil(until, TimeUtil.getCurrentUTC(), changesFeedLag),
                after,
                size);

        var baseUrl = UrlUtil.getBaseUrl();
        var changes = page.changes();
        changes.forEach(
                entry -> entry.setUrl(
                        UrlUtil.createApiVersionUrl(
                                baseUrl,
                                entry.getNamespace(),
                                entry.getName(),
                                entry.getTargetPlatform(),
                                entry.getVersion())));

        var json = new ChangesResultJson();
        json.setChanges(changes);
        json.setHasMore(page.hasMore());
        if (page.nextCursor() != null) {
            json.setNextCursor(page.nextCursor().encode());
        }
        return json;
    }

    /**
     * Up to where the changes feed may report, which is the requested bound unless that reaches into the
     * recent past the feed holds back.
     * <p>
     * A transition is stamped with the instant it happened before the transaction that records it commits,
     * so an entry can become readable after a consumer has already read past the position it occupies --
     * the feed is ordered by that instant, not by the order the entries turned up in. Holding back
     * everything younger than the lag gives a transaction that long to commit before its entries are
     * reported, so a consumer that has caught up cannot have missed one. The lag therefore has to exceed
     * the longest window between a transition being stamped and its transaction committing, plus whatever
     * the clocks of the instances writing those stamps differ by.
     * <p>
     * A request that asks for an older window is not clamped: those entries have long been committed, and
     * the bound the caller asked for is the more restrictive one.
     */
    static LocalDateTime visibleUntil(LocalDateTime until, LocalDateTime now, Duration lag) {
        var cutoff = now.minus(lag);
        return until == null || until.isAfter(cutoff) ? cutoff : until;
    }
}
