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
//DEPS org.apache.maven:maven-artifact:3.9.16
//JAVA 25+

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

// Checks the Spring Boot managed-version overrides in build.gradle's
// `ext['x.version'] = libs.versions.y.get()` block against reality. Overrides are discovered from
// build.gradle itself (not a list maintained here), so adding/removing one there needs no change
// to this script.
//
//   - always: fail if any override has fallen BEHIND the version Spring Boot's own BOM manages -
//     that means the override no longer does what it says (see build.gradle's comment) and should
//     be re-checked or removed.
//   - with --latest: additionally (and non-fatally) report whether an override is behind the
//     latest upstream release, same as dependency-update-check.sh does for the full dependency
//     list, but scoped to just these overrides.
//
// Run from the server root (see dependency-override-check.sh).
void main(String[] args) throws Exception {
    boolean checkLatest = List.of(args).contains("--latest");

    var overridePattern = Pattern.compile("ext\\['([^']+)'\\]\\s*=\\s*libs\\.versions\\.([\\w-]+)\\.get\\(\\)");
    var buildGradle = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
    var overrides = new ArrayList<String[]>(); // [property, tomlKey]
    var overrideMatcher = overridePattern.matcher(buildGradle);
    while (overrideMatcher.find()) {
        overrides.add(new String[] {overrideMatcher.group(1), overrideMatcher.group(2)});
    }
    if (overrides.isEmpty()) {
        System.err.println("No `ext['x.version'] = libs.versions.y.get()` overrides found in build.gradle.");
        System.exit(2);
    }

    var toml = Files.readString(Path.of("gradle/libs.versions.toml"), StandardCharsets.UTF_8);
    var tomlVersions = parseTomlVersionsSection(toml);

    var springBootVersion = tomlVersions.get("spring-boot");
    if (springBootVersion == null) {
        System.err.println("No 'spring-boot' entry found in gradle/libs.versions.toml.");
        System.exit(2);
    }

    var gav = "org.springframework.boot:spring-boot-dependencies:" + springBootVersion;
    System.out.println("Fetching Spring Boot " + springBootVersion + " managed dependency versions...");
    var modelXml = runToolbox("effective-model", gav);

    var doc = parseXml(modelXml);
    var bomProperties = directChildTextMap(firstElement(doc, "properties"));

    // Every managed <dependency>'s resolved version -> its GA, for the optional --latest check
    // below (there is no property -> artifact mapping to rely on, so a shared version string is
    // the only link back from a BOM property to a concrete artifact worth asking about updates).
    var versionToGa = new LinkedHashMap<String, String>();
    NodeList depNodes = doc.getElementsByTagName("dependency");
    for (int i = 0; i < depNodes.getLength(); i++) {
        var dep = (Element) depNodes.item(i);
        var groupId = directChildText(dep, "groupId");
        var artifactId = directChildText(dep, "artifactId");
        var version = directChildText(dep, "version");
        if (groupId != null && artifactId != null && version != null) {
            versionToGa.putIfAbsent(version, groupId + ":" + artifactId);
        }
    }

    var behindBom = false;
    var gavsForLatestCheck = new ArrayList<String>();
    for (String[] override : overrides) {
        var property = override[0];
        var tomlKey = override[1];
        var ourVersion = tomlVersions.get(tomlKey);
        if (ourVersion == null) {
            System.err.println(
                    "warning: no libs.versions.toml entry for '" + tomlKey + "' (needed for " + property
                            + "), skipping");
            continue;
        }
        var bomVersion = bomProperties.get(property);
        if (bomVersion == null) {
            System.err.println(
                    "warning: '" + property + "' not found in the Spring Boot " + springBootVersion
                            + " BOM properties, skipping");
            continue;
        }

        var ga = versionToGa.get(bomVersion);
        if (ga != null) {
            gavsForLatestCheck.add(ga + ":" + ourVersion);
        }

        if (new ComparableVersion(ourVersion).compareTo(new ComparableVersion(bomVersion)) < 0) {
            System.out.println(
                    "BEHIND BOM: " + property + " = " + ourVersion + ", Spring Boot " + springBootVersion
                            + " manages " + bomVersion + (ga != null ? " (" + ga + ")" : ""));
            behindBom = true;
        } else {
            System.out.println(
                    "ok: " + property + " = " + ourVersion + " (Spring Boot " + springBootVersion + " manages "
                            + bomVersion + ")");
        }
    }

    if (checkLatest) {
        System.out.println();
        System.out.println("Optional: checking overrides against the latest upstream releases (informational only)...");
        if (gavsForLatestCheck.isEmpty()) {
            System.out.println("Could not resolve a representative artifact for any override; skipping.");
        } else {
            var gavFile = Files.createTempFile("dependency-override-check", ".txt");
            try {
                Files.write(gavFile, gavsForLatestCheck, StandardCharsets.UTF_8);
                var versionsOutput = runToolbox(
                        "versions",
                        "--force-updates",
                        gavFile.toString(),
                        "--artifactVersionSelectorSpec=minor()");
                var any = false;
                for (String line : versionsOutput.split("\n", -1)) {
                    if (!line.isBlank() && !line.contains("up to date")) {
                        System.out.println(line);
                        any = true;
                    }
                }
                if (!any) {
                    System.out.println("All overrides are at the latest minor version.");
                }
            } finally {
                Files.deleteIfExists(gavFile);
            }
        }
    }

    if (behindBom) {
        System.out.println();
        System.out.println(
                "One or more dependency overrides are behind the Spring Boot BOM - update them in "
                        + "build.gradle/libs.versions.toml, or remove the override if it's no longer needed.");
        System.exit(1);
    }

    System.out.println();
    System.out.println("All dependency overrides are at or ahead of the Spring Boot " + springBootVersion + " BOM.");
}

/** Reads the bare `key = "value"` entries of the `[versions]` table only (`[plugins]` etc. use a
 *  `key = { ... }` shape that must not be picked up here, and could otherwise collide by name). */
Map<String, String> parseTomlVersionsSection(String toml) {
    var result = new LinkedHashMap<String, String>();
    var lines = toml.split("\n", -1);
    var inVersions = false;
    var entryPattern = Pattern.compile("^([\\w-]+)\\s*=\\s*\"([^\"]+)\"");
    for (String line : lines) {
        var trimmed = line.strip();
        if (trimmed.startsWith("[")) {
            inVersions = trimmed.equals("[versions]");
            continue;
        }
        if (!inVersions) {
            continue;
        }
        var entryMatcher = entryPattern.matcher(trimmed);
        if (entryMatcher.find()) {
            result.put(entryMatcher.group(1), entryMatcher.group(2));
        }
    }
    return result;
}

Document parseXml(String xml) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var builder = factory.newDocumentBuilder();
    return builder.parse(new InputSource(new StringReader(xml)));
}

Element firstElement(Document doc, String tagName) {
    NodeList nodes = doc.getElementsByTagName(tagName);
    return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
}

/** Direct-child element text, keyed by tag name. Deliberately not recursive (unlike
 *  Element.getElementsByTagName), so a &lt;properties&gt; block isn't confused with anything
 *  nested deeper in the document. */
Map<String, String> directChildTextMap(Element parent) {
    var result = new LinkedHashMap<String, String>();
    if (parent == null) {
        return result;
    }
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
        Node node = children.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            result.put(node.getNodeName(), node.getTextContent().strip());
        }
    }
    return result;
}

/** Direct-child element text for one tag. Deliberately not Element.getElementsByTagName, which
 *  searches all descendants - wrong for e.g. a &lt;dependency&gt; with nested
 *  &lt;exclusions&gt;&lt;exclusion&gt;&lt;groupId&gt;, which would otherwise shadow the
 *  dependency's own groupId. */
String directChildText(Element parent, String tagName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
        Node node = children.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
            return node.getTextContent().strip();
        }
    }
    return null;
}

String runToolbox(String... args) throws Exception {
    var command = new ArrayList<String>();
    command.add("jbang");
    command.add("toolbox@maveniverse");
    for (String arg : args) {
        command.add(arg);
    }
    var processBuilder = new ProcessBuilder(command);
    // Keep the JVM's "Picked up JAVA_TOOL_OPTIONS" notice (and any real warnings) visible to the
    // caller, but off the captured stdout, which must stay parseable (XML for effective-model,
    // plain text for versions).
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    var process = processBuilder.start();
    var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var exitCode = process.waitFor();
    if (exitCode != 0) {
        throw new IllegalStateException(
                "`" + String.join(" ", command) + "` exited with code " + exitCode + ":\n" + stdout);
    }
    return stdout;
}
