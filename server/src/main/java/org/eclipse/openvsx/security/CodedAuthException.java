/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.security;

import java.io.Serial;

import org.springframework.security.core.AuthenticationException;

/**
 * Authentication exception that contains a code to be used in user interfaces to
 * provide more help for resolving the problem.
 */
public class CodedAuthException extends AuthenticationException {

    public static final String UNSUPPORTED_REGISTRATION = "unsupported-registration";
    public static final String INVALID_GITHUB_USER = "invalid-github-user";
    public static final String INVALID_USER = "invalid-user";
    public static final String NEED_MAIN_LOGIN = "need-main-login";
    public static final String ECLIPSE_MISSING_GITHUB_ID = "eclipse-missing-github-id";
    public static final String ECLIPSE_MISMATCH_GITHUB_ID = "eclipse-mismatch-github-id";

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;

    public CodedAuthException(String message, String code) {
        super(message);
        this.code = code;
    }

    public CodedAuthException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
