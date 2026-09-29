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
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body for querying TP status. This is one way, is sent to users only from server.
 */
@JsonInclude(Include.NON_NULL)
public class TrustedPublisherStatusJson extends ResultJson {

    public static TrustedPublisherStatusJson error(String message) {
        var result = new TrustedPublisherStatusJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Indicates whether the trusted publisher feature is enabled")
    private boolean enabled;

    @Schema(description = "Indicates whether the current user is allowed to use the trusted publisher feature")
    private boolean allowed;

    @Schema(description = "List of supported trusted publisher providers")
    private List<TrustedPublisherProviderJson> trustedPublisherProviders;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public List<TrustedPublisherProviderJson> getTrustedPublisherProviders() {
        return trustedPublisherProviders;
    }

    public void setTrustedPublisherProviders(List<TrustedPublisherProviderJson> trustedPublisherProviders) {
        this.trustedPublisherProviders = trustedPublisherProviders;
    }
}
