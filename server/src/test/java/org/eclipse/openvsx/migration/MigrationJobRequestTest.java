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
package org.eclipse.openvsx.migration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A self-referential type bound here -- T extends JobRequestHandler&lt;MigrationJobRequest&lt;?&gt;&gt;,
 * i.e. T's bound mentions this very class parametrized with itself again -- previously sent Jackson's
 * generic type resolution into infinite recursion while serializing the {@code handler} field for
 * JobRunr's queue storage, surfacing as a StackOverflowError wrapped in a DatabindException the moment
 * any migration item was actually enqueued (see MigrationJobRequest's Class&lt;T&gt; field).
 */
class MigrationJobRequestTest {

    @Test
    void serializesWithoutInfiniteRecursion() {
        // JobRunr's own Jackson3JsonMapper wraps a JsonMapper built much like this one; a plain
        // JsonMapper is enough to reproduce (and guard against regressing) the type-resolution issue,
        // which happens purely from the field's declared generic signature, not JobRunr-specific config.
        var mapper = JsonMapper.builder().build();
        var request = new MigrationJobRequest<>(FileResourceSizeJobRequestHandler.class, 42L);

        var json = mapper.writeValueAsString(request);

        assertThat(json).contains("42");
    }
}
