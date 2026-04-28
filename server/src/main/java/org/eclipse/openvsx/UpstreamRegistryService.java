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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.search.ISearchService;
import org.eclipse.openvsx.util.BooleanTernary;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UpstreamRegistryService implements IExtensionRegistry {

    private static final String VAR_NAMESPACE = "namespace";
    private static final String VAR_EXTENSION = "extension";
    private static final String VAR_TARGET = "targetPlatform";
    private static final String VAR_PRE_RELEASES = "preReleases";
    private static final String VAR_OFFSET = "offset";
    private static final String VAR_SIZE = "size";
    private static final String VAR_ALL_VERSIONS = "includeAllVersions";
    private static final String URL_EXTENSION_FRAGMENT = "/api/{namespace}/{extension}";
    private static final String URL_TARGET_FRAGMENT = "/{targetPlatform}";


    protected final Logger logger = LoggerFactory.getLogger(UpstreamRegistryService.class);

    private final RestTemplate restTemplate;
    private UpstreamProxyService proxy;
    private final UrlConfigService urlConfigService;

    public UpstreamRegistryService(
            RestTemplate restTemplate,
            Optional<UpstreamProxyService> upstreamProxyService,
            UrlConfigService urlConfigService
    ) {
        this.restTemplate = restTemplate;
        upstreamProxyService.ifPresent(service -> this.proxy = service);
        this.urlConfigService = urlConfigService;
    }

    public boolean isValid() {
        return !StringUtils.isEmpty(urlConfigService.getUpstreamUrl());
    }

    @Override
    public NamespaceJson getNamespace(String namespace) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/{namespace}";
        var uriVariables = Map.of(VAR_NAMESPACE, namespace);
        try {
            var json = restTemplate.getForObject(urlTemplate, NamespaceJson.class, uriVariables);
            return proxy != null ? proxy.rewriteUrls(json) : json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public NamespaceDetailsJson getNamespaceDetails(String namespace) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/{namespace}/details";
        var uriVariables = Map.of(VAR_NAMESPACE, namespace);
        try {
            return restTemplate.getForObject(urlTemplate, NamespaceDetailsJson.class, uriVariables);
        } catch (RestClientException exc) {
            handleError(exc);
            throw exc;
        }
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getNamespaceLogo(String namespaceName, String fileName) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/{namespace}/logo/{file}";
        var uriVariables = Map.of(VAR_NAMESPACE, namespaceName, "file", fileName);
        return getFile(urlTemplate, uriVariables);
    }

    @Override
    public ExtensionJson getExtension(String namespace, String extension, String targetPlatform) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + URL_EXTENSION_FRAGMENT;
        var uriVariables = new HashMap<String, String>();
        uriVariables.put(VAR_NAMESPACE, namespace);
        uriVariables.put(VAR_EXTENSION, extension);
        if(targetPlatform != null) {
            urlTemplate += URL_TARGET_FRAGMENT;
            uriVariables.put(VAR_TARGET, targetPlatform);
        }

        try {
            var json = restTemplate.getForObject(urlTemplate, ExtensionJson.class, uriVariables);
            if(json != null) {
                makeDownloadsCompatible(json);
                if(proxy != null) {
                    proxy.rewriteUrls(json);
                }
            }

            return json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public ExtensionJson getExtension(String namespace, String extension, String targetPlatform, String version) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + URL_EXTENSION_FRAGMENT;
        var uriVariables = new HashMap<String, String>();
        uriVariables.put(VAR_NAMESPACE, namespace);
        uriVariables.put(VAR_EXTENSION, extension);
        if(targetPlatform != null) {
            urlTemplate += URL_TARGET_FRAGMENT;
            uriVariables.put(VAR_TARGET, targetPlatform);
        }
        if(version != null) {
            urlTemplate += "/{version}";
            uriVariables.put("version", version);
        }

        try {
            var json = restTemplate.getForObject(urlTemplate, ExtensionJson.class, uriVariables);
            if(json != null) {
                makeDownloadsCompatible(json);
                if(proxy != null) {
                    proxy.rewriteUrls(json);
                }
            }

            return json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public VersionsJson getVersions(String namespace, String extension, String targetPlatform, int size, int offset) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + URL_EXTENSION_FRAGMENT;
        var uriVariables = new HashMap<String, String>();
        uriVariables.put(VAR_NAMESPACE, namespace);
        uriVariables.put(VAR_EXTENSION, extension);
        if(targetPlatform != null) {
            urlTemplate += URL_TARGET_FRAGMENT;
            uriVariables.put(VAR_TARGET, targetPlatform);
        }

        urlTemplate += "/versions?offset={offset}&size={size}";
        uriVariables.put(VAR_OFFSET, String.valueOf(offset));
        uriVariables.put(VAR_SIZE, String.valueOf(size));

        try {
            var json = restTemplate.getForObject(urlTemplate, VersionsJson.class, uriVariables);
            return proxy != null ? proxy.rewriteUrls(json) : json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public VersionReferencesJson getVersionReferences(String namespace, String extension, String targetPlatform, BooleanTernary preReleases, int size, int offset) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + URL_EXTENSION_FRAGMENT;
        var uriVariables = new HashMap<String, String>();
        uriVariables.put(VAR_NAMESPACE, namespace);
        uriVariables.put(VAR_EXTENSION, extension);
        if(targetPlatform != null) {
            urlTemplate += URL_TARGET_FRAGMENT;
            uriVariables.put(VAR_TARGET, targetPlatform);
        }

        urlTemplate += "/version-references?offset={offset}&size={size}";
        uriVariables.put(VAR_OFFSET, String.valueOf(offset));
        uriVariables.put(VAR_SIZE, String.valueOf(size));

        if (preReleases != BooleanTernary.UNKNOWN) {
            urlTemplate += "&preReleases={preReleases}";
            uriVariables.put(VAR_PRE_RELEASES, String.valueOf(preReleases));
        }

        try {
            var json = restTemplate.getForObject(urlTemplate, VersionReferencesJson.class, uriVariables);
            return proxy != null ? proxy.rewriteUrls(json) : json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getFile(String namespace, String extension, String targetPlatform, String version, String fileName) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + URL_EXTENSION_FRAGMENT;
        var uriVariables = new HashMap<String, String>();
        uriVariables.put(VAR_NAMESPACE, namespace);
        uriVariables.put(VAR_EXTENSION, extension);
        if(TargetPlatform.isUniversal(targetPlatform)) {
            targetPlatform = null;
        }
        if(targetPlatform != null) {
            urlTemplate += URL_TARGET_FRAGMENT;
            uriVariables.put(VAR_TARGET, targetPlatform);
        }

        urlTemplate += "/{version}/file/{fileName}";
        uriVariables.put("version", version);
        uriVariables.put("fileName", fileName);
        return getFile(urlTemplate, uriVariables);
    }

    private ResponseEntity<StreamingResponseBody> getFile(String urlTemplate, Map<String, ?> uriVariables) {
        var responseHandler = new ResponseExtractor<ResponseEntity<StreamingResponseBody>>() {
            @Override
            public ResponseEntity<StreamingResponseBody> extractData(ClientHttpResponse response) throws IOException {
                var statusCode = response.getStatusCode();
                if (statusCode.is2xxSuccessful()) {
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(UriComponentsBuilder.fromHttpUrl(urlTemplate).build(uriVariables))
                            .build();
                }
                if (statusCode.is3xxRedirection()) {
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .headers(response.getHeaders())
                            .build();
                }
                if (statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
                    var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                    logger.error("HEAD {}: {}", url, response);
                }

                throw new NotFoundException();
            }
        };

        try {
            return restTemplate.execute(urlTemplate, HttpMethod.HEAD, null, responseHandler, uriVariables);
        } catch(RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("HEAD " + url, exc);
            }

            throw new NotFoundException();
        }
    }

    @Override
    public ReviewListJson getReviews(String namespace, String extension) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/{namespace}/{extension}/reviews";
        var uriVariables = new HashMap<String, String>();
        uriVariables.put(VAR_NAMESPACE, namespace);
        uriVariables.put(VAR_EXTENSION, extension);

        try {
            return restTemplate.getForObject(urlTemplate, ReviewListJson.class, uriVariables);
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }

            throw new NotFoundException();
        }
    }

	@Override
	public SearchResultJson search(ISearchService.Options options) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/-/search";
        var uriVariables = new HashMap<String,String>();
        uriVariables.put(VAR_SIZE, Integer.toString(options.requestedSize()));
        uriVariables.put(VAR_OFFSET, Integer.toString(options.requestedOffset()));
        uriVariables.put(VAR_ALL_VERSIONS, Boolean.toString(options.includeAllVersions()));
        uriVariables.put("query", options.queryString());
        uriVariables.put("category", options.category());
        uriVariables.put("sortOrder", options.sortOrder());
        uriVariables.put("sortBy", options.sortBy());
        uriVariables.put(VAR_TARGET, options.targetPlatform());

        var queryString = uriVariables.entrySet().stream()
                .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
                .map(Map.Entry::getKey)
                .map(key -> key + "={" + key + "}")
                .collect(Collectors.joining("&"));
        if(!queryString.isEmpty()) {
            urlTemplate += "?" + queryString;
        }

        try {
            var json = restTemplate.getForObject(urlTemplate, SearchResultJson.class, uriVariables);
            return proxy != null ? proxy.rewriteUrls(json) : json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public QueryResultJson query(QueryRequest request) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/-/query";
        var queryParams = new HashMap<String,String>();
        queryParams.put("namespaceName", request.namespaceName());
        queryParams.put("extensionName", request.extensionName());
        queryParams.put("extensionVersion", request.extensionVersion());
        queryParams.put("extensionId", request.extensionId());
        queryParams.put("extensionUuid", request.extensionUuid());
        queryParams.put("namespaceUuid", request.namespaceUuid());
        queryParams.put(VAR_ALL_VERSIONS, String.valueOf(request.includeAllVersions()));
        queryParams.put(VAR_TARGET, request.targetPlatform());
        queryParams.put(VAR_SIZE, String.valueOf(request.size()));
        queryParams.put(VAR_OFFSET, String.valueOf(request.offset()));

        var queryString = queryParams.entrySet().stream()
            .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
            .map(Map.Entry::getKey)
            .map(key -> key + "={" + key + "}")
            .collect(Collectors.joining("&"));
        if(!queryString.isEmpty()) {
            urlTemplate += "?" + queryString;
        }

        try {
            var json = restTemplate.getForObject(urlTemplate, QueryResultJson.class, queryParams);
            return proxy != null ? proxy.rewriteUrls(json) : json;
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(queryParams);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    @Override
    public QueryResultJson queryV2(QueryRequestV2 request) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/v2/-/query";
        var queryParams = new HashMap<String,String>();
        queryParams.put("namespaceName", request.namespaceName());
        queryParams.put("extensionName", request.extensionName());
        queryParams.put("extensionVersion", request.extensionVersion());
        queryParams.put("extensionId", request.extensionId());
        queryParams.put("extensionUuid", request.extensionUuid());
        queryParams.put("namespaceUuid", request.namespaceUuid());
        queryParams.put(VAR_ALL_VERSIONS, String.valueOf(request.includeAllVersions()));
        queryParams.put(VAR_TARGET, request.targetPlatform());
        queryParams.put(VAR_SIZE, String.valueOf(request.size()));
        queryParams.put(VAR_OFFSET, String.valueOf(request.offset()));

        var queryString = queryParams.entrySet().stream()
                .filter(entry -> !StringUtils.isEmpty(entry.getValue()))
                .map(Map.Entry::getKey)
                .map(key -> key + "={" + key + "}")
                .collect(Collectors.joining("&"));
        if(!queryString.isEmpty()) {
            urlTemplate += "?" + queryString;
        }

        try {
            return restTemplate.getForObject(urlTemplate, QueryResultJson.class, queryParams);
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(queryParams);
                logger.error("GET " + url, exc);
            }
            throw new NotFoundException();
        }
    }

    public String getPublicKey(String publicId) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/public-key/{publicId}";
        var uriVariables = new HashMap<String, String>();
        uriVariables.put("publicId", publicId);

        try {
            return restTemplate.getForObject(urlTemplate, String.class, uriVariables);
        } catch (RestClientException exc) {
            if(!isNotFound(exc)) {
                var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
                logger.error("GET " + url, exc);
            }

            throw new NotFoundException();
        }
    }

    /**
     * Returns the version of the upstream registry.
     *
     * This functionality is currently not used, but could be called when
     * the need to show or check the version of the upstream registry arises.
     *
     * @return the version of the upstream registry
     */
    @Override
    public RegistryVersionJson getRegistryVersion() {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/api/version";

        try {
            ResponseEntity<RegistryVersionJson> response = restTemplate.getForEntity(urlTemplate,
                    RegistryVersionJson.class);
            return response.getBody();
        } catch (RestClientException exc) {
            logger.error("GET " + urlTemplate, exc);
            throw new NotFoundException();
        }
    }

    private void handleError(Throwable exc) throws RuntimeException {
        if (exc instanceof HttpStatusCodeException) {
            var status = ((HttpStatusCodeException) exc).getStatusCode();
            if (status == HttpStatus.NOT_FOUND)
                throw new NotFoundException();
            else
                throw new ResponseStatusException(status,
                        "Upstream registry responded with status \"" + exc.getMessage() + "\".", exc);
        } else if (exc.getCause() != null && exc.getCause() != exc) {
            handleError(exc.getCause());
        }
    }

    private void makeDownloadsCompatible(ExtensionJson json) {
        if (json.getDownloads() == null && json.getFiles().containsKey("download")) {
            var downloads = new HashMap<String, String>();
            downloads.put(TargetPlatform.NAME_UNIVERSAL, json.getFiles().get("download"));
            json.setDownloads(downloads);
        }
    }

    private boolean isNotFound(RestClientException exc) {
        return exc instanceof HttpStatusCodeException
                && ((HttpStatusCodeException) exc).getStatusCode() == HttpStatus.NOT_FOUND;
    }
}
