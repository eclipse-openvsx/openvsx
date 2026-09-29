/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx.entities;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * This class is not mapped to a database entity, but parsed / serialized to
 * JSON via a column converter.
 */
public record AuthToken(
        String accessToken,
        Instant issuedAt,
        Instant expiresAt,
        Set<String> scopes,
        String refreshToken,
        Instant refreshExpiresAt
) implements Serializable {}
