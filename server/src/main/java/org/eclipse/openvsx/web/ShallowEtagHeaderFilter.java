/*******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

public class ShallowEtagHeaderFilter extends org.springframework.web.filter.ShallowEtagHeaderFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // limit the filter to /api/{namespace}/{extension}, /api/{namespace}/details,
        // /api/{namespace}/{extension}/{version}, and /api/-/search endpoints
        var path = request.getRequestURI().substring(1).split("/");
        var applyFilter = (path.length == 3 || path.length == 4) && path[0].equals("api");
        if (applyFilter && path[1].equals("-")) {
            applyFilter = path[2].contains("search");
        }

        return !applyFilter;
    }
}
