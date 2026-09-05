/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.ModelAndView;

import org.eclipse.openvsx.util.UrlUtil;

@Controller
// server.error.path was renamed to spring.web.error.path in Spring Boot 4 - it's what both
// BasicErrorController's class-level @RequestMapping and the servlet container's actual
// error-page-forward target (ErrorMvcAutoConfiguration.ErrorPageCustomizer) resolve from now.
@ConditionalOnProperty(value = "spring.web.error.path", havingValue = "/server-error")
public class ServerErrorController extends BasicErrorController {

    private final WebUiProperties webUi;

    public ServerErrorController(ErrorAttributes errorAttributes, WebProperties webProperties, WebUiProperties webUi) {
        super(errorAttributes, webProperties.getError());
        this.webUi = webUi;
    }

    // Override errorHtml() itself rather than adding a new method with its own explicit
    // "/server-error" @RequestMapping: the class-level mapping above already resolves to
    // "/server-error", and Spring concatenates class-level + method-level paths, so an
    // explicit method-level "/server-error" would only ever match "/server-error/server-error".
    // Overriding this exact method (no path of its own, just like the superclass's) inherits
    // the correct, property-driven path with no combination/doubling.
    @Override
    public ModelAndView errorHtml(HttpServletRequest request, HttpServletResponse response) {
        return new ModelAndView("redirect:" + UrlUtil.createApiUrl(webUi.getUrl(), "error"));
    }
}
