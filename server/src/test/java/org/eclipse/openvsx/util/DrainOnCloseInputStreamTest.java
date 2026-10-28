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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DrainOnCloseInputStreamTest {

    @Test
    void closeDrainsWhateverWasNeverRead() throws IOException {
        var underlying = new ByteArrayInputStream("the rest of the body".getBytes(StandardCharsets.UTF_8));
        var stream = new DrainOnCloseInputStream(underlying, 1024);

        // Nothing read at all before closing - the whole thing must still get drained.
        stream.close();

        assertThat(underlying.available()).isZero();
    }

    @Test
    void closeIsACheapNoOpWhenAlreadyFullyRead() throws IOException {
        var underlying = new ByteArrayInputStream("fully consumed".getBytes(StandardCharsets.UTF_8));
        var stream = new DrainOnCloseInputStream(underlying, 1024);
        stream.readAllBytes();

        assertThatCode(stream::close).doesNotThrowAnyException();
    }

    // Without a cap, a client rejected for any reason could force the server to read and discard
    // an arbitrarily large body before responding - trading the original LB/proxy problem for a
    // resource-exhaustion one. Only what's within the bound gets drained; the rest is left alone.
    @Test
    void closeDoesNotDrainPastMaxBytes() throws IOException {
        var body = "x".repeat(20).getBytes(StandardCharsets.UTF_8);
        var underlying = new ByteArrayInputStream(body);
        var stream = new DrainOnCloseInputStream(underlying, 5);

        stream.close();

        assertThat(underlying.available()).isEqualTo(15);
    }

    // maxBytes bounds the TOTAL bytes ever read over this instance's lifetime, not just what
    // close() itself reads: ExtensionService#createExtensionFile's own oversized-package detection
    // already reads up to maxContentSize + 1 bytes normally before rejecting. If close() didn't
    // account for that and instead drained a fresh maxBytes on top, an oversized upload would cost
    // up to ~2x maxBytes total - defeating the bound for exactly the case it exists to protect.
    @Test
    void closeAccountsForBytesAlreadyReadNormally() throws IOException {
        var body = "x".repeat(20).getBytes(StandardCharsets.UTF_8);
        var underlying = new ByteArrayInputStream(body);
        var stream = new DrainOnCloseInputStream(underlying, 12);

        // Simulates createExtensionFile's own read of the first part of the body.
        var read = stream.readNBytes(10);
        assertThat(read).hasSize(10);

        stream.close();

        // Only 12 - 10 = 2 bytes of budget were left for the drain, not the full 12: 8 bytes
        // (20 - 10 read - 2 drained) are correctly left undrained.
        assertThat(underlying.available()).isEqualTo(8);
    }

    // A reader that stops at a size cap (as ExtensionService#createExtensionFile does) leaves the
    // rest unread on the wire. Closing through try-with-resources drains that remainder, as long
    // as the bound covers it, which callers get by using the same max upload size for both.
    @Test
    void drainsWhateverASizeCappedReaderNeverReached() throws IOException {
        var body = "x".repeat(20).getBytes(StandardCharsets.UTF_8);
        var underlying = new ByteArrayInputStream(body);
        var draining = new DrainOnCloseInputStream(underlying, 100);

        try (var input = draining) {
            assertThat(input.readNBytes(5)).hasSize(5);
        }

        assertThat(underlying.available()).isZero();
    }

    @Test
    void secondCloseDoesNotTouchTheUnderlyingStream() throws IOException {
        var closes = new int[1];
        InputStream underlying = new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized long skip(long n) {
                if (closes[0] > 0) {
                    throw new IllegalStateException("skipped after close");
                }
                return super.skip(n);
            }

            @Override
            public void close() throws IOException {
                closes[0]++;
                super.close();
            }
        };
        var stream = new DrainOnCloseInputStream(underlying, 1024);

        stream.close();

        assertThatCode(stream::close).doesNotThrowAnyException();
        assertThat(closes[0]).isEqualTo(1);
    }

    @Test
    void closeSwallowsAnIOExceptionFromDraining() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };

        assertThatCode(new DrainOnCloseInputStream(failing, 1024)::close).doesNotThrowAnyException();
    }
}
