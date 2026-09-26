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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Counts of extensions flagged by the name squatting publisher check, broken down by what became
 * of the extension after the check ran.
 */
@Schema(
    name = "NameSquattingCounts",
    description = "Counts of extensions flagged by the name squatting publisher check"
)
@JsonInclude(Include.NON_NULL)
public class NameSquattingCountsJson extends ResultJson {

    @Schema(description = "Total number of flagged extensions")
    private int total;

    @Schema(description = "Flagged extensions that are live and can be moderated")
    private int published;

    @Schema(description = "Flagged extensions that exist but have already been deactivated")
    private int deactivated;

    @Schema(description = "Flagged extensions whose publication was blocked, so nothing was created")
    private int rejected;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPublished() {
        return published;
    }

    public void setPublished(int published) {
        this.published = published;
    }

    public int getDeactivated() {
        return deactivated;
    }

    public void setDeactivated(int deactivated) {
        this.deactivated = deactivated;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }
}
