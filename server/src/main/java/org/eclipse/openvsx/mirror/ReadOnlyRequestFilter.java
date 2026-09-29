/*******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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
package org.eclipse.openvsx.mirror;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class ReadOnlyRequestFilter extends OncePerRequestFilter {

    private List<String> allowedEndpoints;
    private List<String> disallowedMethods;

    public ReadOnlyRequestFilter(String[] allowedEndpoints, String[] disallowedMethods) {
        this.allowedEndpoints = Arrays.asList(allowedEndpoints);
        this.disallowedMethods = Arrays.asList(disallowedMethods);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(disallowedMethods.contains(request.getMethod())
                && !allowedEndpoints.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
    }
}
