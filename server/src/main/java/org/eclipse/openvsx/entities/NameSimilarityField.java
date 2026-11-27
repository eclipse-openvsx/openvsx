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
package org.eclipse.openvsx.entities;

/**
 * The name of an extension that two extensions are compared on when looking for name squatting.
 */
public enum NameSimilarityField {
    /** The extension name, compared case-insensitively. */
    NAME,
    /** The display name of an active version, compared ignoring case and surrounding whitespace. */
    DISPLAY_NAME,
    /** The namespace name, compared case-insensitively. Only ever reported alongside another field. */
    NAMESPACE
}
