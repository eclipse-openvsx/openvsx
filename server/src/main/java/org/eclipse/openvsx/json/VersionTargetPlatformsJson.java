/*******************************************************************************
 * Copyright (c) 2024 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Groups the target platforms available for a single extension version.
 *
 * @param canDelete whether the current caller is allowed to delete this version. {@code null} means
 *                  "not applicable / unrestricted" (e.g. the public or admin context, where this
 *                  field is omitted from the response); a non-null value is only populated for the
 *                  authenticated user settings view, where namespace owners may delete any version
 *                  while other members may only delete versions they published themselves.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VersionTargetPlatformsJson(
        String version,
        List<TargetPlatformActiveJson> targetPlatforms,
        Boolean canDelete
) {
    public VersionTargetPlatformsJson(String version, List<TargetPlatformActiveJson> targetPlatforms) {
        this(version, targetPlatforms, null);
    }

    public VersionTargetPlatformsJson withCanDelete(boolean canDelete) {
        return new VersionTargetPlatformsJson(version, targetPlatforms, canDelete);
    }
}
