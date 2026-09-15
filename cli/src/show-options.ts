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

import { RegistryOptions } from './registry-options';

export interface ShowOptions extends RegistryOptions {
    /**
     * Identifier in the form `namespace.extension` or `namespace/extension`, optionally suffixed
     * with `@<version>` - an exact version, or one of the `latest` / `pre-release` aliases.
     */
    extensionId: string;
    /**
     * Target platform to report on. Defaults to whichever the registry considers current.
     */
    target?: string;
    /**
     * Print the raw metadata as JSON instead of a readable summary.
     */
    json?: boolean;
    /**
     * List every published version rather than the most recent few.
     */
    allVersions?: boolean;
}
