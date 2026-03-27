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
package org.eclipse.openvsx.scanning;

import org.eclipse.openvsx.entities.ExtensionScan;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.util.TempFile;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface for checks that run during extension publishing.
 * <p>
 * Implementations are auto-discovered by Spring and executed by PublishCheckRunner.
 * To add a new publishing check, create a @Component that implements this interface.
 */
public interface PublishCheck {

    /**
     * Unique identifier for this check type.
     * Used for recording failures and filtering in the admin UI.
     */
    String getCheckType();

    /**
     * Whether this check is enabled. Disabled checks are skipped entirely.
     */
    boolean isEnabled();

    /**
     * Whether failures from this check should block publication.
     * Non-enforced checks record failures for monitoring but allow publication to proceed.
     */
    boolean isEnforced();

    /**
     * Whether errors (exceptions) from this check should block publication.
     * <p>
     * If true (default): an exception during check execution will cause publishing to fail.
     * If false: an exception is logged and recorded, but publishing continues.
     * <p>
     * This is useful for non-critical checks where availability issues shouldn't block publishing.
     */
    default boolean isRequired() {
        return true;
    }

    /**
     * Execute the check.
     */
    Result check(Context context);

    /**
     * Generate the user-facing error message for failures from this check.
     * <p>
     * Override this method to customize what users see when publication is blocked.
     * The detailed failure reasons are still stored in the database for admin review.
     * <p>
     * Default implementation shows up to 3 failure reasons.
     */
    default String getUserFacingMessage(List<Failure> failures) {
        if (failures.isEmpty()) {
            return getCheckType() + " check failed";
        }
        
        int maxToShow = Math.min(3, failures.size());
        var reasons = failures.stream()
                .limit(maxToShow)
                .map(Failure::reason)
                .collect(Collectors.joining(", "));
        
        if (failures.size() > maxToShow) {
            reasons += " ... and " + (failures.size() - maxToShow) + " more";
        }
        
        return reasons;
    }

    /**
     * Context passed to publish checks during extension publishing.
     * <p>
     * Contains the scan record (with extension metadata), the extension file,
     * and the publishing user. Use scan.getNamespaceName(), scan.getExtensionName(),
     * etc. to access extension metadata.
     */
    record Context(
        @Nonnull ExtensionScan scan,
        @Nonnull TempFile extensionFile,
        @Nonnull UserData user
    ) {}

    /**
     * Result of a publishing check execution.
     */
    record Result(
        boolean passed,
        List<Failure> failures
    ) {
        public static Result pass() {
            return new Result(true, List.of());
        }

        public static Result fail(List<Failure> failures) {
            return new Result(false, failures);
        }

        public static Result fail(String ruleName, String reason) {
            return new Result(false, List.of(new Failure(ruleName, reason)));
        }
    }

    /**
     * A single check failure with details for recording.
     */
    record Failure(
        String ruleName,
        String reason
    ) {}
}
