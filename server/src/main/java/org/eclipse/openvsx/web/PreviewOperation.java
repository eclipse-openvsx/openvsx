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
package org.eclipse.openvsx.web;

import java.lang.annotation.*;

/**
 * Marks an operation whose contract is not settled yet: its parameters or the shape of its response may
 * still change in a later release.
 * <p>
 * Documentation only -- a preview operation is served like any other. What the marking buys is that a
 * consumer can tell from the published API which endpoints it should expect to have to adapt to, instead
 * of finding out when one changes. Remove it once the contract is being kept stable.
 *
 * @see DocumentationConfig#previewOperation()
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreviewOperation {
}
