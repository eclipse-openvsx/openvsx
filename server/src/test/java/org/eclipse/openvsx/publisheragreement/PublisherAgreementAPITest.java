/******************************************************************************
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
 *****************************************************************************/
package org.eclipse.openvsx.publisheragreement;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublisherAgreementAPITest {

    private PublisherAgreementAPI api(String location) {
        return new PublisherAgreementAPI(new DefaultResourceLoader(), location);
    }

    @Test
    void servesTheConfiguredText() throws Exception {
        var response = api("classpath:publisheragreement/agreement.md").getText();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Test Publisher Agreement");
    }

    @Test
    void theTextMayBeCachedForAWhile() {
        var response = api("classpath:publisheragreement/agreement.md").getText();

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("max-age=300, must-revalidate, public");
    }

    @Test
    void notFoundWhenNotConfigured() {
        assertThat(api("").getText().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void notFoundWhenTheResourceIsMissing() {
        assertThat(api("classpath:publisheragreement/missing.md").getText().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // Through the request mapping, as the webui calls it: it asks for text/plain.
    @Test
    void servesTheTextWhateverTheClientAccepts() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(api("classpath:publisheragreement/agreement.md")).build();

        for (var accept : new String[] {
            "text/plain",
            "text/markdown",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "application/json"
        }) {
            mvc.perform(get("/api/publisher-agreement").header("Accept", accept))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/plain;charset=UTF-8"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Publisher Agreement")));
        }
        mvc.perform(get("/api/publisher-agreement"))
                .andExpect(status().isOk());
    }
}
