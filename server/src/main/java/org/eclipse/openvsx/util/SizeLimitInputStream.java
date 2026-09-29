/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.openvsx.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;

/**
 * InputStream wrapper that enforces a hard byte limit.
 * Useful for preventing OOM from misleading archive entry headers.
 */
public class SizeLimitInputStream extends FilterInputStream {

    private final long maxBytes;
    private long bytesRead = 0;

    public SizeLimitInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            checkLimit(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n != -1) {
            checkLimit(n);
        }
        return n;
    }

    private void checkLimit(long n) throws IOException {
        bytesRead += n;
        if (bytesRead > maxBytes) {
            var maxSize = FileUtils.byteCountToDisplaySize(maxBytes);
            throw new IOException("File size exceeds limit of " + maxSize + ".");
        }
    }
}
