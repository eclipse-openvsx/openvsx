/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.openvsx.consistency;

/**
 * One row of the admin "data consistency" overview: a check's identity and its live findings count.
 * Deliberately has no historical/last-run fields - any fix, whether from the scheduled sweep or an
 * admin clicking "Fix"/"Fix all", is recorded as a normal admin log entry (see
 * {@link ConsistencyCheckService}) rather than in a separate table, so there is nothing here to go
 * stale between one page load and the next.
 */
public record ConsistencyCheckSummary(String id, String name, String description, int currentFindingsCount) {}
