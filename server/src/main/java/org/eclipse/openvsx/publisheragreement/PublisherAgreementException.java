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
 * Why a {@link PublisherAgreementProvider} could not do what it was asked. The reason, not an HTTP status, is
 * what a provider reports: {@link PublisherAgreementService} turns it into the response.
 */
public class PublisherAgreementException extends RuntimeException {

    public enum Reason {
        /** The user has to link or renew the login the provider needs first. */
        LOGIN_REQUIRED,
        /** The provider refused the request. */
        REJECTED,
        /** The provider could not be reached or gave an answer that cannot be used. */
        UNAVAILABLE,
        /** The provider does not support the operation. */
        UNSUPPORTED
    }

    private final Reason reason;

    public PublisherAgreementException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PublisherAgreementException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
