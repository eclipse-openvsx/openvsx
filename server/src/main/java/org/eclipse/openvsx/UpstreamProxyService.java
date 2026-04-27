/********************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.adapter.ExtensionQueryResult;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(value="ovsx.upstream.proxy.enabled", havingValue = "true")
public class UpstreamProxyService {

    protected final Logger logger = LoggerFactory.getLogger(UpstreamProxyService.class);

    public NamespaceJson rewriteUrls(NamespaceJson json) {
        rewriteUrlMap(json.getExtensions());
        if(!StringUtils.isEmpty(json.getMembersUrl())) {
            json.setMembersUrl(rewriteUrl(json.getMembersUrl()));
        }
        if(!StringUtils.isEmpty(json.getRoleUrl())) {
            json.setRoleUrl(rewriteUrl(json.getRoleUrl()));
        }

        return json;
    }

    public ExtensionJson rewriteUrls(ExtensionJson json) {
        json.setNamespaceUrl(rewriteUrl(json.getNamespaceUrl()));
        json.setReviewsUrl(rewriteUrl(json.getReviewsUrl()));
        rewriteUrlMap(json.getFiles());
        rewriteUrlMap(json.getAllVersions());
        json.setDependencies(rewriteUrlList(json.getDependencies(), this::rewriteUrls));
        json.setBundledExtensions(rewriteUrlList(json.getBundledExtensions(), this::rewriteUrls));
        rewriteUrlMap(json.getDownloads());
        return json;
    }

    public SearchResultJson rewriteUrls(SearchResultJson json) {
        json.setExtensions(rewriteUrlList(json.getExtensions(), this::rewriteUrls));
        return json;
    }

    public QueryResultJson rewriteUrls(QueryResultJson json) {
        json.setExtensions(rewriteUrlList(json.getExtensions(), this::rewriteUrls));
        return json;
    }

    public ExtensionQueryResult rewriteUrls(ExtensionQueryResult json) {
        return new ExtensionQueryResult(json.results().stream()
                .map(result -> new ExtensionQueryResult.ResultItem(rewriteExtensionUrls(result.extensions()), result.resultMetadata()))
                .toList());
    }

    public ExtensionQueryResult.Extension rewriteUrls(ExtensionQueryResult.Extension json) {
        return rewriteExtensionUrls(List.of(json)).getFirst();
    }

    private List<ExtensionQueryResult.Extension> rewriteExtensionUrls(List<ExtensionQueryResult.Extension> extensions) {
        return extensions.stream()
                .map(extension -> new ExtensionQueryResult.Extension(
                        extension.extensionId(),
                        extension.extensionName(),
                        extension.displayName(),
                        extension.shortDescription(),
                        extension.publisher(),
                        rewriteVersionUrls(extension.versions()),
                        extension.statistics(),
                        extension.tags(),
                        extension.releaseDate(),
                        extension.publishedDate(),
                        extension.lastUpdated(),
                        extension.categories(),
                        extension.flags()
                ))
                .toList();
    }

    private List<ExtensionQueryResult.ExtensionVersion> rewriteVersionUrls(List<ExtensionQueryResult.ExtensionVersion> versions) {
        return versions.stream()
                .map(version -> new ExtensionQueryResult.ExtensionVersion(
                        version.version(),
                        version.lastUpdated(),
                        rewriteUrl(version.assetUri()),
                        rewriteUrl(version.fallbackAssetUri()),
                        rewriteFileUrls(version.files()),
                        version.properties(),
                        version.targetPlatform()
                ))
                .toList();
    }

    private List<ExtensionQueryResult.ExtensionFile> rewriteFileUrls(List<ExtensionQueryResult.ExtensionFile> files) {
        return files.stream()
                .map(file -> new ExtensionQueryResult.ExtensionFile(
                        file.assetType(),
                        rewriteUrl(file.source())
                ))
                .toList();
    }

    public VersionsJson rewriteUrls(VersionsJson json) {
        rewriteUrlMap(json.getVersions());
        return json;
    }

    public VersionReferencesJson rewriteUrls(VersionReferencesJson json) {
        json.setVersions(json.getVersions().stream().map(this::rewriteUrls).toList());
        return json;
    }

    public JsonNode rewriteUrls(JsonNode json) {
        if(json.isArray()) {
            var list = new ObjectMapper().createArrayNode();
            var array = (ArrayNode) json;
            array.forEach(url -> list.add(rewriteUrl(url.asText())));
            json = list;
        }

        return json;
    }

    public URI rewriteUrl(URI location) {
        return URI.create(rewriteUrl(location.toString()));
    }

    private SearchEntryJson rewriteUrls(SearchEntryJson json) {
        json.setUrl(rewriteUrl(json.getUrl()));
        rewriteUrlMap(json.getFiles());
        json.setAllVersions(rewriteUrlList(json.getAllVersions(), this::rewriteUrls));

        return json;
    }

    private VersionReferenceJson rewriteUrls(VersionReferenceJson json) {
        json.setUrl(rewriteUrl(json.getUrl()));
        rewriteUrlMap(json.getFiles());
        return json;
    }

    private ExtensionReferenceJson rewriteUrls(ExtensionReferenceJson json) {
        json.setUrl(rewriteUrl(json.getUrl()));
        return json;
    }

    private <T> List<T> rewriteUrlList(List<T> jsonList, Function<T,T> mapper) {
        return jsonList != null ? jsonList.stream().map(mapper).collect(Collectors.toList()) : jsonList;
    }

    private void rewriteUrlMap(Map<String, String> map) {
        if(map != null) {
            map.replaceAll((k, v) -> rewriteUrl(v));
        }
    }

    private String rewriteUrl(String url) {
        var baseUri = URI.create(UrlUtil.getBaseUrl());
        var uri = URI.create(url);

        var scheme = baseUri.getScheme();
        var userInfo = baseUri.getUserInfo();
        var host = baseUri.getHost();
        var port = baseUri.getPort();
        var path = uri.getPath();
        var query = uri.getQuery();
        var fragment = uri.getFragment();

        try {
            return new URI(scheme, userInfo, host, port, path, query, fragment).toString();
        } catch (URISyntaxException e) {
            logger.error("failed to rewrite URI: {}", uri);
            return null;
        }
    }
}
