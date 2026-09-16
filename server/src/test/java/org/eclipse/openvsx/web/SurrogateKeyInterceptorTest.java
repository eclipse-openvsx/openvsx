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

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the interceptor over the path templates the API actually uses, rather than the endpoints
 * themselves, so this stays a test of the mapping rule and not of what those endpoints return.
 */
class SurrogateKeyInterceptorTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StubApi())
            .addInterceptors(new SurrogateKeyInterceptor())
            .build();

    @Test
    void tagsANamespaceResponseWithItsNamespace() throws Exception {
        mockMvc.perform(get("/api/redhat"))
                .andExpect(status().isOk())
                .andExpect(header().string(SurrogateKey.HEADER, "ns/redhat"));
    }

    @Test
    void tagsAnExtensionResponseWithTheExtensionAndItsNamespace() throws Exception {
        mockMvc.perform(get("/api/redhat/java"))
                .andExpect(status().isOk())
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/java ns/redhat"));
    }

    @Test
    void tagsTheGalleryTheSameWayDespiteItsOwnVariableNames() throws Exception {
        mockMvc.perform(get("/vscode/gallery/redhat/java/latest"))
                .andExpect(status().isOk())
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/java ns/redhat"));
    }

    @Test
    void tagsARequestByWhateverCaseItUses() throws Exception {
        mockMvc.perform(get("/api/RedHat/Java"))
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/java ns/redhat"));
    }

    // Publishing an extension has to drop the cached 404s for it as well as the pages that list it.
    @Test
    void tagsAResponseForSomethingThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/redhat/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/missing ns/redhat"));
    }

    // The header goes on in preHandle, because a response that streams its body is committed by the
    // time postHandle runs and takes no further headers.
    @Test
    void tagsAResponseThatIsWrittenAndFlushedByTheEndpointItself() throws Exception {
        mockMvc.perform(get("/api/redhat/java/1.0.0/file/extension.vsix"))
                .andExpect(header().string(SurrogateKey.HEADER, "ext/redhat/java ns/redhat"));
    }

    @Test
    void tagsNothingWhereThePathNamesNoExtensionOrNamespace() throws Exception {
        mockMvc.perform(get("/api/-/search"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(SurrogateKey.HEADER));
    }

    // "-" is how the API spells "this segment is not a name"; a key for it could never be purged.
    @Test
    void tagsNothingForTheDashThatStandsInForANamespace() throws Exception {
        mockMvc.perform(get("/api/-"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(SurrogateKey.HEADER));
    }

    @Test
    void tagsTheRootWithTheFixedWebuiKey() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string(SurrogateKey.HEADER, SurrogateKey.WEBUI_HTML))
                .andExpect(header().string("Cache-Control", "no-cache, public"));
    }

    @Test
    void tagsTheEntryHtmlWithTheFixedWebuiKey() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string(SurrogateKey.HEADER, SurrogateKey.WEBUI_HTML))
                .andExpect(header().string("Cache-Control", "no-cache, public"));
    }

    @RestController
    private static class StubApi {

        @GetMapping("/")
        ResponseEntity<String> root() {
            return ResponseEntity.ok("root");
        }

        @GetMapping("/index.html")
        ResponseEntity<String> indexHtml() {
            return ResponseEntity.ok("index");
        }

        @GetMapping("/api/{namespace}")
        ResponseEntity<String> namespaceDetails(@PathVariable String namespace) {
            return ResponseEntity.ok(namespace);
        }

        @GetMapping("/api/{namespace}/{extension}")
        ResponseEntity<String> extension(@PathVariable String namespace, @PathVariable String extension) {
            return "missing".equals(extension)
                    ? ResponseEntity.status(HttpStatus.NOT_FOUND).body("not found")
                    : ResponseEntity.ok(extension);
        }

        @GetMapping("/vscode/gallery/{namespaceName}/{extensionName}/latest")
        ResponseEntity<String> latest(@PathVariable String namespaceName, @PathVariable String extensionName) {
            return ResponseEntity.ok(extensionName);
        }

        @GetMapping("/api/{namespace}/{extension}/{version}/file/**")
        void file(HttpServletResponse response) throws Exception {
            response.getOutputStream().write("bytes".getBytes());
            response.flushBuffer();
        }

        @GetMapping("/api/-/search")
        ResponseEntity<String> search() {
            return ResponseEntity.ok("[]");
        }
    }
}
