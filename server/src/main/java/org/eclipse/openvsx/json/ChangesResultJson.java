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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "ChangesResult",
    description = "Paginated feed of extension version changes"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangesResultJson extends ResultJson {

    public static ChangesResultJson error(String message) {
        var result = new ChangesResultJson();
        result.setError(message);
        return result;
    }

    @Schema(
        description = "Entries in ascending order of their last transition, limited to the size "
                + "specified in the changes request"
    )
    @NotNull
    private List<ChangeEntryJson> changes;

    @Schema(
        description = "Opaque position to pass as the 'after' parameter of the next changes request in "
                + "order to continue where this response ended. Absent only when an empty response was "
                + "requested without a position to continue from, in which case the same request can "
                + "simply be repeated."
    )
    private String nextCursor;

    @Schema(
        description = "Whether more entries match the changes request beyond the ones in this response. "
                + "A consumer following the feed requests the next page straight away while this is true, "
                + "and is up to date once it is false."
    )
    @NotNull
    private boolean hasMore;

    public List<ChangeEntryJson> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeEntryJson> changes) {
        this.changes = changes;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
