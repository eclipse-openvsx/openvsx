/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.json.ExtensionJson;
import org.springframework.data.util.Pair;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriUtils;

import jakarta.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UrlUtil {

    private UrlUtil() {
    }

    public static String createApiFileUrl(String baseUrl, ExtensionVersion extVersion, String fileName) {
        var extension = extVersion.getExtension();
        var namespaceName = extension.getNamespace().getName();
        return createApiFileUrl(baseUrl, namespaceName, extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion(), fileName);
    }

    public static String createApiFileUrl(String baseUrl, String namespaceName, String extensionName, String targetPlatform, String version, String fileName) {
        return createApiFileUrl(createApiFileBaseUrl(baseUrl, namespaceName, extensionName, targetPlatform, version), fileName);
    }

    public static String createApiFileUrl(String fileBaseUrl, String fileName) {
        var segments = ArrayUtils.addAll(new String[]{"file"}, fileName.split("/"));
        return createApiUrl(fileBaseUrl, segments);
    }

    public static String createApiFileBaseUrl(String baseUrl, ExtensionVersion extVersion) {
        var extension = extVersion.getExtension();
        var namespaceName = extension.getNamespace().getName();
        return createApiFileBaseUrl(baseUrl, namespaceName, extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
    }

    public static String createApiFileBaseUrl(String baseUrl, String namespaceName, String extensionName, String targetPlatform, String version) {
        return createApiUrl(baseUrl, createApiVersionSegments(namespaceName, extensionName, targetPlatform, true, version));
    }

    public static String createApiVersionUrl(String baseUrl, ExtensionJson json) {
        return createApiVersionUrl(baseUrl, json.getNamespace(), json.getName(), json.getTargetPlatform(), json.getVersion());
    }

    public static String createApiVersionUrl(String baseUrl, ExtensionVersion extVersion) {
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        return createApiVersionUrl(baseUrl, namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
    }

    public static String createApiVersionUrl(String baseUrl, String namespaceName, String extensionName, String targetPlatform, String version) {
        return createApiUrl(baseUrl, createApiVersionSegments(namespaceName, extensionName, targetPlatform, false, version));
    }

    public static String createApiVersionBaseUrl(String baseUrl, String namespaceName, String extensionName, String targetPlatform) {
        return createApiUrl(baseUrl, createApiVersionSegments(namespaceName, extensionName, targetPlatform,false, null));
    }

    private static String[] createApiVersionSegments(String namespaceName, String extensionName, String targetPlatform, boolean excludeUniversalTargetPlatform, String version) {
        var segments = new String[]{ "api", namespaceName, extensionName };
        if(excludeUniversalTargetPlatform && TargetPlatform.isUniversal(targetPlatform)) {
            targetPlatform = null;
        }
        if(targetPlatform != null) {
            segments = ArrayUtils.add(segments, targetPlatform);
        }
        if(version != null) {
            segments = ArrayUtils.add(segments, version);
        }

        return segments;
    }

    /**
     * Create a URL pointing to an API path.
     */
    public static String createApiUrl(String baseUrl, String... segments) {
        if(Arrays.stream(segments).anyMatch(Objects::isNull)) {
            return null;
        }

        var path = Arrays.stream(segments)
                .filter(StringUtils::isNotEmpty)
                .map(segment ->
                    UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8)
                        .replace("+", "%2B")
)


                .collect(Collectors.joining("/"));

        if (baseUrl.isEmpty() || baseUrl.charAt(baseUrl.length() - 1) != '/') {
            baseUrl += '/';
        }

        return baseUrl + path;
    }

    public static String createApiReviewsUrl(String serverUrl, String namespace, String extension) {
        return createApiUrl(serverUrl, "api", namespace, extension, "reviews");
    }

    /**
     * Creates a URI of the form {@code baseUrl + '/' + encodedPath},
     * ensuring that only a single slash is between baseUrl and path.
     *
     * @param baseUrl the baseURL.
     * @param encodedPath the encoded path to append
     */
    public static URI createURI(String baseUrl, String encodedPath) {
        // ensure that the baseURL always ends with a '/'.
        if (baseUrl.isEmpty() || baseUrl.charAt(baseUrl.length() - 1) != '/') {
            baseUrl += '/';
        }

        // strip a preceding '/' from the path.
        if (encodedPath.startsWith("/")) {
            encodedPath = encodedPath.substring(1);
        }

        return URI.create(baseUrl + encodedPath);
    }

    /**
     * Add a query to a URL. The parameters array must contain a sequence of key and
     * value pairs, so its length is expected to be even.
     */
    public static String addQuery(String url, String... parameters) {
        if (parameters.length % 2 != 0)
            throw new IllegalArgumentException("Expected an even number of parameters.");

        var result = new StringBuilder(url);
        var printedParams = 0;
        for (var i = 0; i < parameters.length; i += 2) {
            var key = parameters[i];
            var value = parameters[i + 1];
            if (key == null)
                throw new NullPointerException("Parameter key must not be null");
            if (value != null) {
                if (printedParams == 0)
                    result.append('?');
                else
                    result.append('&');
                result.append(key).append('=').append(UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8));
                printedParams++;
            }
        }
        return result.toString();
    }

    /**
     * Get the base URL to use for API requests from the current servlet request.
     */
    public static String getBaseUrl() {
        try {
            var requestAttrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return getBaseUrl(requestAttrs.getRequest());
        } catch (IllegalStateException e) {
            // method is called outside of web request context
            return "";
        }
    }

    protected static String getBaseUrl(HttpServletRequest request) {
        var url = new StringBuilder();

        var scheme = getBaseUrlScheme(request);
        url.append(scheme).append("://");

        var pair = getBaseUrlHostAndPort(request);
        url.append(pair.getFirst());
        var port = pair.getSecond();
        switch (scheme) {
            case "http":
                if (port != 80 && port > 0)
                    url.append(":").append(port);
                break;
            case "https":
                if (port != 443 && port > 0)
                    url.append(":").append(port);
                break;
            default:
                throw new IllegalArgumentException("Unsupported scheme: " + scheme);
        }

        url.append(getBaseUrlPrefix(request));
        url.append(request.getContextPath());
        return url.toString();
    }

    private static String getBaseUrlPrefix(HttpServletRequest request) {
        // Use the prefix from the X-Forwarded-Prefix header if present
        var forwardedPrefix = request.getHeader("X-Forwarded-Prefix");
        if (forwardedPrefix == null) {
            forwardedPrefix = "";
        }

        return forwardedPrefix;
    }

    private static String getBaseUrlScheme(HttpServletRequest request) {
        // Use the scheme from the X-Forwarded-Proto header if present
        var forwardedScheme = request.getHeader("X-Forwarded-Proto");
        return forwardedScheme != null ? forwardedScheme : request.getScheme();
    }

    public static Pair<String,Integer> getBaseUrlHostAndPort(HttpServletRequest request) {
        // Use the host and port from the X-Forwarded-Host header if present
        var forwardedHostHeadersEnumeration = request.getHeaders("X-Forwarded-Host");
        if (forwardedHostHeadersEnumeration == null || !forwardedHostHeadersEnumeration.hasMoreElements()) {
            return Pair.of(request.getServerName(), request.getServerPort());
        } else {
            // take the first one
            var forwardedHost = forwardedHostHeadersEnumeration.nextElement();

            // if it's comma separated, take the first one
            var forwardedHosts = forwardedHost.split(",");
            if (forwardedHosts.length > 1) {
                forwardedHost = forwardedHosts[0];
            }
            int colonIndex = forwardedHost.lastIndexOf(':');
            if (colonIndex > 0) {
                int port;
                try {
                    port = Integer.parseInt(forwardedHost.substring(colonIndex + 1));
                } catch (NumberFormatException exc) {
                    port = -1;
                }

                return Pair.of(forwardedHost.substring(0, colonIndex), port);
            } else {
                return Pair.of(forwardedHost, -1);
            }
        }
    }

    public static String extractWildcardPath(HttpServletRequest request) {
        return extractWildcardPath(request, (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    }

    /**
     * Extract the rest ** of a wildcard path /<segments>/**
     * @param request incoming request
     * @param pattern ant pattern to match against
     * @return rest of the path
     */
    public static String extractWildcardPath(HttpServletRequest request, String pattern) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        if (path != null) {
            // need to decode the path as it is part of the URL and thus URI encoded.
            path = UriUtils.decode(path, StandardCharsets.UTF_8);
        }

        return path != null && pattern != null
                ? new AntPathMatcher().extractPathWithinPattern(pattern, path)
                : "";
    }

    public static String getPublicKeyUrl(ExtensionVersion extVersion) {
        return getPublicKeyUrl(extVersion.getSignatureKeyPair().getPublicId());
    }

    public static String getPublicKeyUrl(String publicId) {
        return createApiUrl(getBaseUrl(), "api", "-", "public-key", publicId);
    }

    public static String createAllVersionsUrl(String namespaceName, String extensionName, String targetPlatform) {
        return createAllVersionsUrl(namespaceName, extensionName, targetPlatform, "versions");
    }

    public static String createAllVersionsUrl(String namespaceName, String extensionName, String targetPlatform, String versionsSegment) {
        var segments = new String[]{ "api", namespaceName, extensionName };
        if(targetPlatform != null) {
            segments = ArrayUtils.add(segments, targetPlatform);
        }

        segments = ArrayUtils.add(segments, versionsSegment);
        return createApiUrl(getBaseUrl(), segments);
    }
}
