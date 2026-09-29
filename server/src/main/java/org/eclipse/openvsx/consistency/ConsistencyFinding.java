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
package org.eclipse.openvsx.consistency;

/**
 * One entity a {@link ConsistencyCheck} found to be inconsistent.
 *
 * @param entityId the id {@link ConsistencyCheck#fix(long)} needs to repair this finding
 * @param label a short, human-readable identifier for the affected entity (e.g. its namespace/extension id)
 * @param detail what specifically is inconsistent about it
 */
public record ConsistencyFinding(long entityId, String label, String detail) {}
