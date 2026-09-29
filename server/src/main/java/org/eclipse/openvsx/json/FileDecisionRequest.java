/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request types for file decision operations.
 */
public final class FileDecisionRequest {

    private FileDecisionRequest() {
    }

    @Schema(
        name = "FileDecisionRequest",
        description = "Request body for creating/updating file decisions"
    )
    public record Create(
            @Schema(description = "List of file hashes to apply the decision to") List<String> fileHashes,

            @Schema(description = "Decision to apply: 'allowed' or 'blocked'") String decision
    ) {}

    @Schema(
        name = "FileDecisionDeleteRequest",
        description = "Request body for deleting file decisions"
    )
    public record Delete(
            @Schema(description = "List of file IDs to delete") List<Long> fileIds
    ) {}
}
