/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

export interface RegistryOptions {
    /**
     * The base URL of the registry API.
     */
    registryUrl?: string;
    /**
     * Personal access token.
     */
    pat?: string;
    /**
     * User name for basic authentication.
     */
    username?: string;
    /**
     * Password for basic authentication.
     */
    password?: string;
    /**
     * Maximal request body size for creating namespaces.
     */
    maxNamespaceSize?: number;
    /**
     * Maximal request body size for publishing.
     */
    maxPublishSize?: number;
    /**
     * How long to wait, in milliseconds, for a request to make progress before giving up.
     *
     * This is an inactivity timeout rather than a deadline: any byte sent or received resets it, so
     * a large package downloading slowly is not cut off, while a connection that has gone quiet does
     * not hold the command open indefinitely. Zero disables it.
     */
    timeout?: number;
}
