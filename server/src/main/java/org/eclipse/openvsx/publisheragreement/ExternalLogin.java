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

/**
 * A login a user has to link before signing or being revoked, shown by the web UI as "Log in with ...".
 *
 * @param registrationId the OAuth2 registration of the login, which is also its provider id on a user
 * @param displayName the name shown to people
 */
public record ExternalLogin(String registrationId, String displayName) {}
