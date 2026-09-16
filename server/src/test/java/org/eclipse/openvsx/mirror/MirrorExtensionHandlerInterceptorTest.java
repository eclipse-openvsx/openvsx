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
package org.eclipse.openvsx.mirror;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.eclipse.openvsx.web.SurrogateKey;
import org.eclipse.openvsx.web.SurrogateKeyInterceptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives {@link SurrogateKeyInterceptor} and this interceptor together, in the order
 * {@code WebConfig} registers them, so a header the first one sets is not silently lost to the
 * second's rejection path.
 */
class MirrorExtensionHandlerInterceptorTest {

    @Test
    void rejectionKeepsTheSurrogateKeyHeaderTheEarlierInterceptorSet() throws Exception {
        var dataMirror = mock(DataMirrorService.class);
        when(dataMirror.match("redhat", "java")).thenReturn(false);
        var mockMvc = MockMvcBuilders.standaloneSetup(new StubApi())
                .addInterceptors(new SurrogateKeyInterceptor(), new MirrorExtensionHandlerInterceptor(dataMirror))
                .build();

        mockMvc.perform(get("/vscode/asset/redhat/java/1.0.0/Microsoft.VisualStudio.Code.Manifest"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/java ns/redhat"));
    }

    // getRequestURI() includes the servlet context path, so the /vscode/item special case has to be
    // matched against it too - a registry deployed under one would otherwise always fall through to
    // extractPathParams, which finds nothing for a query-parameter route.
    @Test
    void matchesTheItemRouteByQueryParameterUnderAConfiguredContextPath() throws Exception {
        var dataMirror = mock(DataMirrorService.class);
        when(dataMirror.match("redhat", "java")).thenReturn(true);
        var mockMvc = MockMvcBuilders.standaloneSetup(new StubApi())
                .addInterceptors(new MirrorExtensionHandlerInterceptor(dataMirror))
                .build();

        mockMvc.perform(get("/openvsx-server/vscode/item")
                        .contextPath("/openvsx-server")
                        .param("itemName", "redhat.java"))
                .andExpect(status().isOk());

        verify(dataMirror).match("redhat", "java");
    }

    @RestController
    private static class StubApi {

        @GetMapping("/vscode/asset/{namespaceName}/{extensionName}/{version}/{assetType}")
        ResponseEntity<String> asset(@PathVariable String namespaceName, @PathVariable String extensionName) {
            return ResponseEntity.ok(extensionName);
        }

        @GetMapping("/vscode/item")
        ResponseEntity<String> item(@RequestParam(required = false) String itemName) {
            return ResponseEntity.ok(itemName);
        }
    }
}
