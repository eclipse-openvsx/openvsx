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
package org.eclipse.openvsx.publisheragreement;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.openvsx.entities.SemanticVersion;

/**
 * Which signed versions of the agreement still count, whichever provider keeps the signature. A version is a
 * semantic version in which the minor and patch numbers may be left out ({@code 1}, {@code 1.0}), compared
 * without its build metadata. With allowed versions, a signature counts if its version is one of them or the
 * configured one; without, it counts within the major version of the configured one or a higher one, so only a new major version
 * makes signatures outdated.
 */
public class PublisherAgreementVersions {

    private static final Logger logger = LoggerFactory.getLogger(PublisherAgreementVersions.class);

    private static final String VERSION_PROPERTY = "ovsx.publisher-agreement.version";
    private static final String ALLOWED_VERSIONS_PROPERTY = "ovsx.publisher-agreement.allowed-versions";
    private static final Pattern SHORT_VERSION = Pattern.compile("\\d+(?:\\.\\d+){0,2}");

    private final @Nullable SemanticVersion current;
    /** The allowed versions in their full form, with the configured one; empty to accept the same or a higher major. */
    private final Set<String> allowed;

    /**
     * @param version the current version, blank if there is none
     * @param allowedVersions the versions a signature may have, besides the current one; none to accept any
     *        signature of the same or a higher major version
     * @throws IllegalArgumentException if a version is neither blank nor like 1, 1.0 or a semantic version
     */
    public PublisherAgreementVersions(@Nullable String version, List<String> allowedVersions) {
        var text = version == null ? "" : version.trim();
        this.current = text.isEmpty() ? null : SemanticVersion.parse(full(VERSION_PROPERTY, text));
        var allowedSet = allowedVersions.stream()
                .map(allowedVersion -> full(ALLOWED_VERSIONS_PROPERTY, allowedVersion.trim()))
                .collect(Collectors.toSet());
        if (!allowedSet.isEmpty() && current != null) {
            allowedSet.add(full(VERSION_PROPERTY, text));
        }
        this.allowed = Set.copyOf(allowedSet);
    }

    /**
     * Whether a signature of this version still counts. Everything does while there is no current version, and
     * a version that cannot be read does not.
     */
    public boolean accepts(String signedVersion) {
        if (current == null) {
            return true;
        }
        try {
            return allowed.isEmpty()
                    ? SemanticVersion.parse(full(VERSION_PROPERTY, signedVersion)).getMajor() >= current.getMajor()
                    : allowed.contains(full(VERSION_PROPERTY, signedVersion));
        } catch (IllegalArgumentException exc) {
            logger.warn(
                    "The signed publisher agreement version '{}' cannot be read, so it counts as outdated",
                    signedVersion);
            return false;
        }
    }

    /** The version with its minor and patch numbers spelled out and without build metadata, checked to be one. */
    private static String full(String property, String version) {
        var missing = 2 - version.chars().filter(c -> c == '.').count();
        var full = SHORT_VERSION.matcher(version).matches() ? version + ".0".repeat((int) missing) : version;
        try {
            if (SemanticVersion.VERSION_PARSE_PATTERN.matcher(full).matches()) {
                SemanticVersion.parse(full);
                var build = full.indexOf('+');
                return build < 0 ? full : full.substring(0, build);
            }
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException("Invalid " + property + ": '" + version + "'", exc);
        }
        throw new IllegalArgumentException("Invalid " + property + ": '" + version + "'");
    }
}
