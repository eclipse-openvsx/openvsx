/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.mirror;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import org.eclipse.openvsx.util.NamingUtil;

@Component
@ConditionalOnProperty(value = "ovsx.data.mirror.enabled", havingValue = "true")
public class MirrorExtensionHandlerInterceptor implements HandlerInterceptor {

    private final DataMirrorService dataMirror;

    public MirrorExtensionHandlerInterceptor(DataMirrorService dataMirror) {
        this.dataMirror = dataMirror;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // getRequestURI() includes the servlet context path, so the literal has to be qualified with
        // it too - a registry deployed under one would otherwise never match here.
        var params = (request.getContextPath() + "/vscode/item").equals(request.getRequestURI())
                ? extractQueryParams(request)
                : extractPathParams(request);
        var namespaceName = params.get("namespaceName");
        var extensionName = params.get("extensionName");
        if (!dataMirror.match(namespaceName, extensionName)) {
            // resetBuffer, not reset: this must not discard headers earlier interceptors already set,
            // such as SurrogateKeyInterceptor's Surrogate-Key.
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

        return true;
    }

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        var itemName = NamingUtil.fromExtensionId(request.getParameter("itemName"));
        return itemName != null
                ? Map.of("namespaceName", itemName.namespace(), "extensionName", itemName.extension())
                : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractPathParams(HttpServletRequest request) {
        return new TreeMap<>(
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE));
    }
}
