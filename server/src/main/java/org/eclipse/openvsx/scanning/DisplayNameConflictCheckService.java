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
package org.eclipse.openvsx.scanning;

import java.util.ArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.DisplayNameUtil;
import org.eclipse.openvsx.util.NamingUtil;

/**
 * Rejects a publication that adopts a display name another extension already shows.
 * <p>
 * The display name is what a user reads when picking an extension out of a list, and unlike the
 * extension id it is not unique by construction, so a package can be published under the exact name of
 * a popular extension and be taken for it. Only exact matches (ignoring casing and surrounding
 * whitespace) are rejected; near misses are left to the NAME_SQUATTING check.
 * <p>
 * Extensions in namespaces the publisher belongs to are not conflicts: a publisher shipping two
 * extensions under one name is not impersonating anyone.
 * <p>
 * Mirroring does not run publish checks, so a mirror still receives every extension its upstream has.
 */
@Service
@Order(4) // Run fourth: display name conflict check
@ConditionalOnProperty(
    name = "ovsx.scanning.display-name-conflict.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DisplayNameConflictCheckService implements PublishCheck {

    public static final String CHECK_TYPE = "DISPLAY_NAME_CONFLICT";

    private final DisplayNameConflictCheckConfig config;
    private final RepositoryService repositories;

    public DisplayNameConflictCheckService(DisplayNameConflictCheckConfig config, RepositoryService repositories) {
        this.config = config;
        this.repositories = repositories;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public boolean isEnforced() {
        return config.isEnforced();
    }

    @Override
    public boolean isRequired() {
        return config.isRequired();
    }

    @Override
    public Result check(Context context) {
        var scan = context.scan();
        // The declared name, not the scan's label: a package declaring none must not be compared under
        // the extension name the label falls back to.
        var displayName = scan.getDeclaredDisplayName();
        if (DisplayNameUtil.isBlank(displayName)) {
            return Result.pass();
        }

        var namespaceName = scan.getNamespaceName();
        var latestVersion = repositories
                .findLatestVersion(namespaceName, scan.getExtensionName(), scan.getTargetPlatform(), false, true);
        if (!adoptsDisplayName(latestVersion, displayName)) {
            return Result.pass();
        }

        var excludedNamespaces = new ArrayList<String>();
        // A privileged user may publish into a namespace they are not a member of.
        excludedNamespaces.add(namespaceName);
        repositories.findMemberships(context.user())
                .forEach(membership -> excludedNamespaces.add(membership.getNamespace().getName()));

        var conflict = repositories.findActiveExtensionByDisplayName(displayName, excludedNamespaces);
        if (conflict == null) {
            return Result.pass();
        }

        var conflictId = NamingUtil.toExtensionId(conflict.getNamespace().getName(), conflict.getName());
        return Result.fail(
                "Exact display name match",
                "Display name '" + displayName + "' is already used by the extension '" + conflictId
                        + "'. Please choose a different display name.");
    }

    /**
     * Whether this publication introduces the display name it carries, either for a new extension or by
     * renaming an existing one. A version keeping the name its extension already shows is skipped, so
     * extensions that shared a display name before this check existed can still publish updates.
     */
    private boolean adoptsDisplayName(ExtensionVersion latestVersion, String displayName) {
        var currentDisplayName = latestVersion != null ? latestVersion.getDisplayName() : null;
        return !DisplayNameUtil.normalize(currentDisplayName).equals(DisplayNameUtil.normalize(displayName));
    }
}
