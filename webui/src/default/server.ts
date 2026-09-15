/********************************************************************************
 * Copyright (c) 2019-2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import * as express from 'express';
import * as path from 'path';
import { rateLimit } from 'express-rate-limit';

const app = express();

const args = process.argv.slice(2);
if (args.indexOf('-ratelimit') != -1) {
    const proxiesIndex = args.indexOf('-ratelimit-proxies');
    if (proxiesIndex != -1) {
        app.set('trust proxy', Number(args[proxiesIndex + 1]));
    }

    let windowMs = 15 * 60 * 1000; // 15 minutes
    const rateLimitWindowIndex = args.indexOf('-ratelimit-window-seconds');
    if (rateLimitWindowIndex != -1) {
        windowMs = Number(args[rateLimitWindowIndex + 1]) * 1000;
    }

    let limit = 100; // Limit each IP to 100 requests per windowMs
    const rateLimitAmountIndex = args.indexOf('-ratelimit-limit');
    if (rateLimitAmountIndex != -1) {
        limit = Number(args[rateLimitAmountIndex + 1]);
    }

    // Apply rate limiter to all requests
    const limiter = rateLimit({
        windowMs,
        limit,
        standardHeaders: 'draft-7',
        legacyHeaders: false
    });
    app.use(limiter);
}

// Serve static resources
const staticPath = path.join(__dirname, '..', '..', 'dist');
app.use(express.static(staticPath));

// Enable react-router by forwarding the main page to all paths that don't match a resource.
// The braces make the wildcard match the root path too, which a bare '/*splat' does not.
app.get('/{*splat}', (req, res) => {
    res.sendFile(path.join(staticPath, 'index.html'));
});

const port = 3000;
app.listen(port, () => console.log(`Web UI server running on port ${port}...`));
