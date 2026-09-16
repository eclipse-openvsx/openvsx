/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import org.eclipse.openvsx.util.ExtensionId;
import org.eclipse.openvsx.util.NamingUtil;

/**
 * Tags every response about a namespace or an extension with its {@link SurrogateKey}s.
 * <p>
 * Derived from the request's path variables rather than written out at each endpoint: which
 * extension a response is about is exactly what the URL template says, and there are two dozen
 * endpoints that say it. Doing it here also covers the responses an endpoint does not think of
 * itself as producing - a 404 for an extension that does not exist yet is tagged too, so publishing
 * it purges the cached 404 along with everything else.
 * <p>
 * {@code /vscode/item} is the one endpoint that names its extension in a query parameter
 * ({@code ?itemName=publisher.name}) rather than the path, so it has no URI-template variable for
 * this to key off; it is special-cased the same way {@code MirrorExtensionHandlerInterceptor} already
 * does.
 * <p>
 * The header is inert until a CDN is configured to act on it, and nothing here decides whether a
 * response may be cached: that stays with the {@code Cache-Control} each endpoint sets.
 */
public class SurrogateKeyInterceptor implements HandlerInterceptor {

    /** The two spellings the API uses for the same path variables. */
    private static final String[] NAMESPACE_VARIABLES = { "namespace", "namespaceName" };
    private static final String[] EXTENSION_VARIABLES = { "extension", "extensionName" };

    /**
     * {@code /api/-/search} and its siblings are not a namespace called {@code -}, and the responses
     * they return are about no single extension.
     */
    private static final String NOT_A_NAME = "-";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Before the handler, not after: an endpoint that streams its body or redirects has already
        // committed the response by the time postHandle runs, and a committed response takes no
        // further headers.
        var pathVariables = pathVariables(request);
        var namespace = variable(pathVariables, NAMESPACE_VARIABLES);
        var extension = variable(pathVariables, EXTENSION_VARIABLES);
        if (namespace == null) {
            var item = itemNameExtension(request);
            if (item == null) {
                return true;
            }
            namespace = item.namespace();
            extension = item.extension();
        }
        response.setHeader(
                SurrogateKey.HEADER,
                extension == null
                        ? SurrogateKey.namespace(namespace)
                        : SurrogateKey.extension(namespace, extension));
        return true;
    }

    private static @Nullable ExtensionId itemNameExtension(HttpServletRequest request) {
        if (!"/vscode/item".equals(request.getRequestURI())) {
            return null;
        }
        var itemName = request.getParameter("itemName");
        return itemName == null ? null : NamingUtil.fromExtensionId(itemName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        var attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return attribute instanceof Map ? (Map<String, String>) attribute : Map.of();
    }

    private static @Nullable String variable(Map<String, String> pathVariables, String[] names) {
        for (var name : names) {
            var value = pathVariables.get(name);
            if (StringUtils.isNotBlank(value) && !NOT_A_NAME.equals(value)) {
                return value;
            }
        }
        return null;
    }
}
