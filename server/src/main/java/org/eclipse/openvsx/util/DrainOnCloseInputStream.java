/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InputStream wrapper whose close() drains whatever's left of a {@code maxBytes} budget that
 * normal reads haven't already consumed.
 * <p>
 * A request body that a validation check rejects before the body has been fully read can make
 * some load balancers/proxies mistake the resulting early connection close for a server error.
 * Wrapping a request InputStream once at the entry point and reading it exclusively through
 * try-with-resources makes draining automatic on every exit path - success, an early validation
 * throw, or anything added later - instead of relying on each call site remembering to drain
 * manually before it responds.
 * <p>
 * This does <b>not</b> itself enforce a hard limit on how many bytes a caller may read normally -
 * only close()'s own drain is bounded, to whatever remains of {@code maxBytes} once normal reads
 * are subtracted. Without any bound at all, a client rejected for any reason (invalid token, an
 * oversized upload, ...) could force close() to read and discard an arbitrarily large remainder
 * before responding, trading the original LB/proxy problem for a resource-exhaustion one; this
 * keeps close() itself from being the one to do that. The actual total (normal reads plus
 * whatever close() drains) only stays bounded near {@code maxBytes} in combination with a caller
 * that itself stops reading once it has seen enough to reject the request - e.g.
 * {@code ExtensionService#createExtensionFile}, which detects an oversized package after at most
 * {@code maxContentSize + 1} bytes. {@code maxBytes} should be that same "reasonable size" limit
 * the caller already accepts reading in full on the success path.
 */
public class DrainOnCloseInputStream extends FilterInputStream {

    private final long maxBytes;
    private long bytesRead = 0;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DrainOnCloseInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            bytesRead++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n != -1) {
            bytesRead += n;
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        // Both the publishing entry point and the size-capped reader close this stream.
        if (closed.compareAndSet(false, true)) {
            try {
                // skipNBytes throws EOFException when the stream ends early, which is fine here
                skipNBytes(Math.max(0, maxBytes - bytesRead));
            } catch (IOException ignored) {
                // best-effort drain; connection state doesn't matter once we're closing this stream
            } finally {
                super.close();
            }
        }
    }
}
