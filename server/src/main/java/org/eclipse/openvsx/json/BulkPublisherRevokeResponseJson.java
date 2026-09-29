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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for the request to bulk revoke publishers
 */
@Schema(
    name = "BulkPublisherRevokeResponse",
    description = "List of responses for the bulk publisher revocation request"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkPublisherRevokeResponseJson extends ResultJson {

    public static BulkPublisherRevokeResponseJson error(String message) {
        var result = new BulkPublisherRevokeResponseJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Results for each of the attempted revoke operations matched on the login name of the user")
    private Map<String, ResultJson> responses;

    public BulkPublisherRevokeResponseJson() {
        this.responses = null;
    }

    public BulkPublisherRevokeResponseJson(Map<String, ResultJson> responses) {
        this.responses = responses;
    }

    public void setResponses(Map<String, ResultJson> responses) {
        this.responses = responses;
    }

    public Map<String, ResultJson> getResponses() {
        return this.responses;
    }
}
