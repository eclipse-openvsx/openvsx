/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 ********************************************************************************/

import { describe, expect, it } from 'vitest';
import { DEFAULT_PUBLISH_SIZE, Registry } from '../../src/registry';

/**
 * The CLI refuses a request body larger than `maxBodyLength`, inside the CLI and before
 * anything reaches the registry. Leaving that at the default meant a namespace granted more than
 * 512 MiB could never use it: the size preflight passed and the upload then failed locally.
 */
describe('Registry.publishBodyLimit', () => {
    it('defaults to the built-in publish size when the registry reports no limit', () => {
        const registry = new Registry({ registryUrl: 'https://registry.test' });

        expect(registry.publishBodyLimit(undefined)).toBe(DEFAULT_PUBLISH_SIZE);
    });

    it('stays at the default for a limit the default already covers', () => {
        const registry = new Registry({ registryUrl: 'https://registry.test' });

        expect(registry.publishBodyLimit(1024)).toBe(DEFAULT_PUBLISH_SIZE);
    });

    it('rises to a limit above the default, so an override is actually usable', () => {
        const registry = new Registry({ registryUrl: 'https://registry.test' });
        const sevenHundredMiB = 700 * 1024 * 1024;

        expect(registry.publishBodyLimit(sevenHundredMiB)).toBe(sevenHundredMiB);
    });

    /**
     * The fallback path against an older registry passes the package's own size as the allowance, so
     * a registry whose default is above 512 MiB can actually receive one - "publishing anyway, the
     * registry decides" has to mean the transport lets it through.
     */
    it('admits a package larger than the default when that is the allowance it is given', () => {
        const registry = new Registry({ registryUrl: 'https://registry.test' });
        const sevenHundredMiB = 700 * 1024 * 1024;

        expect(registry.publishBodyLimit(sevenHundredMiB)).toBeGreaterThanOrEqual(sevenHundredMiB);
    });

    it('keeps a pinned publish size authoritative', () => {
        const registry = new Registry({ registryUrl: 'https://registry.test', maxPublishSize: 1024 });

        expect(registry.publishBodyLimit(700 * 1024 * 1024)).toBe(1024);
    });
});
