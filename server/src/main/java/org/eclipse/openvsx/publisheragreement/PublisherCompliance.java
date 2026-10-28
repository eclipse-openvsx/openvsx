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
 * Whether a publisher's extensions may stay published, see
 * {@link PublisherAgreementService#compliance}.
 */
public enum PublisherCompliance {

    /** The publisher has an agreement, which may be outdated. */
    COMPLIANT,

    /** The publisher has none, or it was revoked. */
    NON_COMPLIANT,

    /** The provider could not tell, for instance because its server is unreachable. */
    UNKNOWN
}
