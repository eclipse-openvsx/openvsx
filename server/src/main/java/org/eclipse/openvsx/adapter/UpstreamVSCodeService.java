/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UpstreamProxyService;
import org.eclipse.openvsx.UrlConfigService;
import org.eclipse.openvsx.util.HttpHeadersUtil;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TempFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UpstreamVSCodeService implements IVSCodeService {
    private static final String VAR_NAMESPACE = "namespace";
    private static final String VAR_EXTENSION = "extension";
    private static final String VAR_VERSION = "version";

    private final Logger logger = LoggerFactory.getLogger(UpstreamVSCodeService.class);

    private final RestTemplate restTemplate;
    private UpstreamProxyService proxy;
    private final RestTemplate nonRedirectingRestTemplate;
    private final UrlConfigService urlConfigService;
    private final ExtensionValidator extensionValidator;

    public UpstreamVSCodeService(
            RestTemplate restTemplate,
            Optional<UpstreamProxyService> upstreamProxyService,
            RestTemplate nonRedirectingRestTemplate,
            UrlConfigService urlConfigService,
            ExtensionValidator extensionValidator
    ) {
        this.restTemplate = restTemplate;
        upstreamProxyService.ifPresent(service -> this.proxy = service);
        this.nonRedirectingRestTemplate = nonRedirectingRestTemplate;
        this.urlConfigService = urlConfigService;
        this.extensionValidator = extensionValidator;
    }

    public boolean isValid() {
        return !StringUtils.isEmpty(urlConfigService.getUpstreamUrl());
    }

    @Override
    public ExtensionQueryResult extensionQuery(ExtensionQueryParam param, int defaultPageSize) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/vscode/gallery/extensionquery";
        var request = new RequestEntity<>(param, HttpHeadersUtil.getForwardedHeaders(), HttpMethod.POST, URI.create(urlTemplate));
        ResponseEntity<ExtensionQueryResult> response;
        try {
            response = restTemplate.exchange(request, ExtensionQueryResult.class);
        } catch(RestClientException exc) {
            throw propagateRestException(exc, request.getMethod(), urlTemplate, null);
        }

        var statusCode = response.getStatusCode();
        if (statusCode.is2xxSuccessful()) {
            var json = response.getBody();
            return proxy != null ? proxy.rewriteUrls(json) : json;
        }
        if (statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
            logger.error("POST {}: {}", urlTemplate, response);
        }

        throw new NotFoundException();
    }

    @Override
    public ExtensionQueryResult.Extension latest(String namespaceName, String extensionName) {
        // Check that the user-provided namespace and extension parameters are actually valid before
        // making a request to the upstream server.
        if (extensionValidator.validateNamespace(namespaceName).isPresent() ||
            extensionValidator.validateExtensionName(extensionName).isPresent()) {
            throw new NotFoundException();
        }

        var urlTemplate = urlConfigService.getUpstreamUrl() + "/vscode/gallery/{namespace}/{extension}/latest";
        var uriVariables = new HashMap<>(Map.of(
                VAR_NAMESPACE, namespaceName,
                VAR_EXTENSION, extensionName
        ));

        URI url = restTemplate.getUriTemplateHandler().expand(urlTemplate, uriVariables);
        var request = new RequestEntity<>(HttpHeadersUtil.getForwardedHeaders(), HttpMethod.GET, url);
        ResponseEntity<ExtensionQueryResult.Extension> response;
        try {
            response = restTemplate.exchange(request, ExtensionQueryResult.Extension.class);
        } catch (RestClientException exc) {
            throw propagateRestException(exc, request.getMethod(), url.toString(), null);
        }

        var statusCode = response.getStatusCode();
        if (statusCode.is2xxSuccessful()) {
            var json = response.getBody();
            return proxy != null ? proxy.rewriteUrls(json) : json;
        }
        if (statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
            logger.error("GET {}: {}", urlTemplate, response);
        }

        throw new NotFoundException();
    }

    @Override
    public ResponseEntity<StreamingResponseBody> browse(String namespaceName, String extensionName, String version, String path) {
        var urlBuilder = new StringBuilder(urlConfigService.getUpstreamUrl() + "/vscode/unpkg/{namespace}/{extension}/{version}");
        var uriVariables = new HashMap<>(Map.of(
            VAR_NAMESPACE, namespaceName,
            VAR_EXTENSION, extensionName,
            VAR_VERSION, version
        ));

        if (StringUtils.isNotBlank(path)) {
            var segments = path.split("/");
            for (var i = 0; i < segments.length; i++) {
                var varName = "seg" + i;
                urlBuilder.append("/{").append(varName).append("}");
                uriVariables.put(varName, segments[i]);
            }
        }

        var method = HttpMethod.GET;
        var urlTemplate = urlBuilder.toString();
        var responseHandler = new ResponseExtractor<ResponseEntity<StreamingResponseBody>>() {
            @Override
            public ResponseEntity<StreamingResponseBody> extractData(ClientHttpResponse response) throws IOException {
                var statusCode = response.getStatusCode();
                if(statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
                    handleResponseError(urlTemplate, uriVariables, response);
                }

                var failed = !statusCode.is2xxSuccessful() && !statusCode.is3xxRedirection();
                if(failed) {
                    throw new NotFoundException();
                }

                var headers = new HttpHeaders();
                headers.addAll(response.getHeaders());
                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                headers.remove(HttpHeaders.VARY);

                if (proxy != null && MediaType.APPLICATION_JSON.equals(headers.getContentType())) {
                    var mapper = new ObjectMapper();
                    var json = proxy.rewriteUrls(mapper.readTree(response.getBody()));
                    return ResponseEntity.status(statusCode)
                            .headers(headers)
                            .body(outputStream -> mapper.writeValue(outputStream, json));
                } else {
                    return streamResponse(response, headers, "browse");
                }
            }
        };

        try {
            return nonRedirectingRestTemplate.execute(urlBuilder.toString(), method, null, responseHandler, uriVariables);
        } catch(RestClientException exc) {
            throw propagateRestException(exc, method, urlBuilder.toString(), uriVariables);
        }
    }

    @Override
    public String download(String namespace, String extension, String version, String targetPlatform) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/vscode/gallery/publishers/{namespace}/vsextensions/{extension}/{version}/vspackage?targetPlatform={targetPlatform}";
        var uriVariables = Map.of(
                VAR_NAMESPACE, namespace,
                VAR_EXTENSION, extension,
                VAR_VERSION, version,
                "targetPlatform", targetPlatform
        );

        ResponseEntity<Void> response;
        var method = HttpMethod.GET;
        try {
            response = nonRedirectingRestTemplate.exchange(urlTemplate, method, null, Void.class, uriVariables);
        } catch(RestClientException exc) {
            throw propagateRestException(exc, method, urlTemplate, uriVariables);
        }

        URI location = null;
        var statusCode = response.getStatusCode();
        if(statusCode.is3xxRedirection()) {
            location = response.getHeaders().getLocation();
        }
        if(statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
            handleResponseError(urlTemplate, uriVariables, response);
        }
        if(location == null) {
            throw new NotFoundException();
        }

        if(proxy != null) {
            location = proxy.rewriteUrl(location);
        }
        return location.toString();
    }

    @Override
    public String getItemUrl(String namespace, String extension) {
        var urlTemplate = urlConfigService.getUpstreamUrl() + "/vscode/item?itemName={namespace}.{extension}";
        var uriVariables = Map.of(VAR_NAMESPACE, namespace, VAR_EXTENSION, extension);

        ResponseEntity<Void> response;
        var method = HttpMethod.GET;
        try {
            response = nonRedirectingRestTemplate.exchange(urlTemplate, method, null, Void.class, uriVariables);
        } catch (RestClientException exc) {
            throw propagateRestException(exc, method, urlTemplate, uriVariables);
        }

        URI location = null;
        var statusCode = response.getStatusCode();
        if(statusCode.is3xxRedirection()) {
            location = response.getHeaders().getLocation();
        }
        if(statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
            handleResponseError(urlTemplate, uriVariables, response);
        }
        if(location == null) {
            throw new NotFoundException();
        }

        if(proxy != null) {
            location = proxy.rewriteUrl(location);
        }
        return location.toString();
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getAsset(String namespace, String extensionName, String version, String assetType, String targetPlatform, String restOfTheUrl) {
        var urlBuilder = new StringBuilder()
                .append(urlConfigService.getUpstreamUrl())
                .append("/vscode/asset/{namespace}/{extension}/{version}/{assetType}");
        var uriVariables = new HashMap<>(Map.of(
            VAR_NAMESPACE, namespace,
            VAR_EXTENSION, extensionName,
            VAR_VERSION, version,
            "assetType", assetType,
            "targetPlatform", targetPlatform
        ));

        if (StringUtils.isNotBlank(restOfTheUrl)) {
            var segments = restOfTheUrl.split("/");
            for (var i = 0; i < segments.length; i++) {
                var varName = "seg" + i;
                urlBuilder.append("/{").append(varName).append("}");
                uriVariables.put(varName, segments[i]);
            }
        }

        urlBuilder.append("?targetPlatform={targetPlatform}");
        var urlTemplate = urlBuilder.toString();
        var method = HttpMethod.GET;
        var responseHandler = new ResponseExtractor<ResponseEntity<StreamingResponseBody>>() {
            @Override
            public ResponseEntity<StreamingResponseBody> extractData(ClientHttpResponse response) throws IOException {
                var statusCode = response.getStatusCode();
                if (statusCode.is3xxRedirection()) {
                    var location = response.getHeaders().getLocation();
                    if (location == null) {
                        return ResponseEntity.internalServerError().build();
                    }

                    if (proxy != null) {
                        location = proxy.rewriteUrl(location);
                    }

                    return ResponseEntity.status(HttpStatus.FOUND)
                            .headers(response.getHeaders())
                            .location(location)
                            .build();
                } else if (statusCode.isError() && statusCode != HttpStatus.NOT_FOUND) {
                    handleResponseError(urlTemplate, uriVariables, response);
                } else if (!statusCode.is2xxSuccessful()) {
                    throw new NotFoundException();
                }

                var headers = new HttpHeaders();
                headers.addAll(response.getHeaders());
                headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                headers.remove(HttpHeaders.VARY);

                return streamResponse(response, headers, "asset");
            }
        };

        try {
            return nonRedirectingRestTemplate.execute(urlTemplate, method, null, responseHandler, uriVariables);
        } catch (RestClientException exc) {
            throw propagateRestException(exc, method, urlTemplate, uriVariables);
        }
    }

    private void handleResponseError(String urlTemplate, Map<String, String> uriVariables, Object response) {
        var url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
        logger.error("GET {}: {}", url, response);
    }

    private NotFoundException propagateRestException(RestClientException exc, HttpMethod method, String urlTemplate,
        Map<String, String> uriVariables) {
        if (exc instanceof HttpStatusCodeException) {
            var statusCode = ((HttpStatusCodeException)exc).getStatusCode();
            if(statusCode == HttpStatus.NOT_FOUND) {
                return new NotFoundException();
            }
        }

        URI url;
        if (uriVariables != null) {
            url = UriComponentsBuilder.fromUriString(urlTemplate).build(uriVariables);
        } else {
            url = URI.create(urlTemplate);
        }
        logger.error("upstream: {}: {}", method, url, exc);
        return new NotFoundException();
    }

    private ResponseEntity<StreamingResponseBody> streamResponse(
            ClientHttpResponse response,
            HttpHeaders headers,
            String prefix
    ) throws IOException {
        var tempFile = new TempFile(prefix, null);
        try {
            try (var out = Files.newOutputStream(tempFile.getPath())) {
                response.getBody().transferTo(out);
            }

            return ResponseEntity.status(response.getStatusCode())
                    .headers(headers)
                    .body(outputStream -> {
                        try (var in = Files.newInputStream(tempFile.getPath())) {
                            in.transferTo(outputStream);
                        }

                        IOUtils.closeQuietly(tempFile);
                    });
        } catch (IOException e) {
            IOUtils.closeQuietly(tempFile);
            throw e;
        }
    }
}
