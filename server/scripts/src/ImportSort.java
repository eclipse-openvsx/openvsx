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

///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.diffplug.spotless:spotless-lib:3.3.1
//JAVA 25+

import com.diffplug.spotless.java.ImportOrderStep;

// Import order groups live in config/import-order.importorder, shared with the
// Gradle Spotless "importOrderFile" step so both paths stay in sync.
void main(String[] args) throws Exception {
    var step = ImportOrderStep.forJava().createFrom(new File("config/import-order.importorder"));

    int changed = 0;
    for (String arg : args) {
        try (Stream<Path> paths = Files.walk(Path.of(arg))) {
            List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path path : javaFiles) {
                String original = Files.readString(path, StandardCharsets.UTF_8);
                String formatted = step.format(original, path.toFile());
                if (!formatted.equals(original)) {
                    Files.writeString(path, formatted, StandardCharsets.UTF_8);
                    IO.println("sorted imports: " + path);
                    changed++;
                }
            }
        }
    }
    IO.println(changed + " file(s) updated");
}
