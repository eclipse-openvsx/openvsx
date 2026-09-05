/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class ServerErrorControllerTest {

    // Regression: BasicErrorController carries a class-level @RequestMapping resolving to
    // spring.web.error.path (Spring Boot 4; server.error.path under Boot 3), e.g. "/server-error".
    // Spring concatenates class-level and method-level @RequestMapping paths, so an overriding
    // method with its OWN explicit "/server-error" path would only ever match the doubled-up
    // "/server-error/server-error" - never the actual "/server-error" the servlet container
    // forwards errors to. errorHtml() must stay a bare override (no method-level path of its
    // own, exactly like the superclass's) to inherit the class-level path unmodified.
    @Test
    void errorHtmlOverrideHasNoOwnRequestMappingPath() throws Exception {
        var method = ServerErrorController.class.getMethod(
                "errorHtml",
                HttpServletRequest.class,
                HttpServletResponse.class);

        var mapping = method.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNull();
    }

    @Test
    void errorHtmlRedirectsToTheWebuiErrorPage() throws Exception {
        var webUi = new WebUiProperties();
        ReflectionTestUtils.setField(webUi, "url", "https://open-vsx.org");
        var controller = new ServerErrorController(
                Mockito.mock(ErrorAttributes.class),
                new WebProperties(),
                webUi);

        var modelAndView = controller.errorHtml(
                Mockito.mock(HttpServletRequest.class),
                Mockito.mock(HttpServletResponse.class));

        assertThat(modelAndView.getViewName()).isEqualTo("redirect:https://open-vsx.org/error");
    }
}
