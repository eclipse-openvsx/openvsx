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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One step in a user's publisher agreement history: {@code type} is {@code signed}, {@code revoked} or
 * {@code reinstated}, {@code version} is set for {@code signed}, and {@code actorId} is the user or admin who
 * caused it, absent once that user is gone.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublisherAgreementEntryJson(String type, String version, String timestamp, Long actorId) {}
