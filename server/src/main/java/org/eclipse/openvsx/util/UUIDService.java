/******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.stereotype.Service;

import static java.util.Objects.requireNonNull;

/**
 * UUID service based on Tatu Saloranta (FasterXML)
 * <a href="https://github.com/cowtowncoder/java-uuid-generator">java-uuid-generator</a> library.
 * Implementation details:
 * <ul>
 *     <li>Random UUIDs are generated using a time-based epoch generator as UUIDv7.</li>
 *     <li>Name-based UUIDs are generated using a name-based generator with UTF-8 encoding as UUIDv3.</li>
 * </ul>
 */
@Service
public class UUIDService {

    private final TimeBasedEpochGenerator uuid7generator = Generators.timeBasedEpochGenerator();
    private final NameBasedGenerator uuid3generator = Generators.nameBasedGenerator();

    /**
     * Generates random UUIDv7, never returns {@code null}.
     */
    public UUID generateRandom() {
        return uuid7generator.generate();
    }

    /**
     * Generates name based UUIDv3, never returns {@code null}.
     *
     * @param name the name to generate UUID from, must not be {@code null}
     */
    public UUID generateFromName(String name) {
        requireNonNull(name);
        return uuid3generator.generate(name.getBytes(StandardCharsets.UTF_8));
    }
}
