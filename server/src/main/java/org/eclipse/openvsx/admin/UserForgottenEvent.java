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
package org.eclipse.openvsx.admin;

/**
 * Published when a user is forgotten, whether the {@code user_data} row is deleted or kept as an anonymized
 * tombstone. Deployments that keep personal data of a user elsewhere clear it on this event, which is
 * handled in the transaction of the erasure.
 */
public record UserForgottenEvent(long userId) {}
