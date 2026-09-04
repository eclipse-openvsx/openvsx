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
//JAVA 25+
//DEPS org.eclipse.jdt:org.eclipse.jdt.core:3.46.0
//SOURCES AddBracesFixCore.java

void main(String[] args) throws Exception {
    int changed = 0;
    for (String arg : args) {
        try (Stream<Path> paths = Files.walk(Path.of(arg))) {
            List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path path : javaFiles) {
                String original = Files.readString(path, StandardCharsets.UTF_8);
                String formatted = AddBracesFixCore.fix(original);
                if (!formatted.equals(original)) {
                    Files.writeString(path, formatted, StandardCharsets.UTF_8);
                    IO.println("added braces: " + path);
                    changed++;
                }
            }
        }
    }
    IO.println(changed + " file(s) updated");
}
