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
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for the name squatting moderation actions. Pass a single target to moderate one
 * extension, or several for a bulk operation.
 */
@Schema(
    name = "NameSquattingActionRequest",
    description = "Extensions a name squatting moderation action should be applied to"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingActionRequest {

    @Schema(description = "Extensions to apply the action to")
    private List<NameSquattingTargetJson> targets;

    public List<NameSquattingTargetJson> getTargets() {
        return targets;
    }

    public void setTargets(List<NameSquattingTargetJson> targets) {
        this.targets = targets;
    }
}
