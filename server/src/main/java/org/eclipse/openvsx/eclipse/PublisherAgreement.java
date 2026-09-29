/******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx.eclipse;

import java.time.LocalDateTime;

/**
 *
 * @param isActive
 * @param documentId
 * @param version Version of the last signed publisher agreement.
 * @param timestamp Timestamp of the last signed publisher agreement.
 */
public record PublisherAgreement(boolean isActive, String documentId, String version, LocalDateTime timestamp) {}
