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
package org.eclipse.openvsx.accesstoken;

import java.util.Optional;

/**
 * Provides information about the intended use of the token.
 */
public sealed interface AccessTokenAction {
    /**
     * Returns {@code true} if action makes "use" of token.
     * Using token implies "accessed TS" update and in case of one-time tokens, their immediate deactivation.
     */
    default boolean isUsing() {
        return true;
    }

    /**
     * Returns namespace as optional. Value is present for actions that are namespace specific (e.g. publish version).
     */
    default Optional<String> namespace() {
        return Optional.empty();
    }

    /**
     * Returns extension name as optional. Value is present for actions that are namespace + extension specific.
     * If this value is present, it is implied that {@link #namespace()} is also present.
     */
    default Optional<String> extension() {
        return Optional.empty();
    }

    /**
     * Action that verifies token only (does not "use" it).
     */
    record Verify() implements AccessTokenAction {
        @Override
        public boolean isUsing() {
            return false;
        }
    }

    /**
     * Action that uses token for administration.
     */
    record Administration() implements AccessTokenAction {}

    /**
     * Action that uses token for namespace creation.
     */
    record CreateNamespace(String namespaceName) implements AccessTokenAction {
        public CreateNamespace {
            if (namespaceName == null || namespaceName.isBlank()) {
                throw new IllegalArgumentException("Namespace cannot be null or blank");
            }
        }

        @Override
        public Optional<String> namespace() {
            return Optional.of(namespaceName);
        }
    }

    /**
     * Action that uses token for extension publishing.
     */
    record PublishVersion(String namespaceName, String extensionName) implements AccessTokenAction {
        public PublishVersion {
            if (namespaceName == null || namespaceName.isBlank()) {
                throw new IllegalArgumentException("Namespace cannot be null or blank");
            }
            if (extensionName == null || extensionName.isBlank()) {
                throw new IllegalArgumentException("Extension name cannot be null or blank");
            }
        }

        @Override
        public Optional<String> namespace() {
            return Optional.of(namespaceName);
        }

        @Override
        public Optional<String> extension() {
            return Optional.of(extensionName);
        }
    }

    /**
     * Action that uses token for extension deletion.
     */
    record DeleteVersion(String namespaceName, String extensionName) implements AccessTokenAction {
        public DeleteVersion {
            if (namespaceName == null || namespaceName.isBlank()) {
                throw new IllegalArgumentException("Namespace cannot be null or blank");
            }
            if (extensionName == null || extensionName.isBlank()) {
                throw new IllegalArgumentException("Extension name cannot be null or blank");
            }
        }

        @Override
        public Optional<String> namespace() {
            return Optional.of(namespaceName);
        }

        @Override
        public Optional<String> extension() {
            return Optional.of(extensionName);
        }
    }
}
