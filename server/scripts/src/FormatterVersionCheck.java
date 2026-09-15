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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

// Checks the versions that drive the Java formatter, which runs from two paths - Spotless (via
// Gradle) and jbang (scripts/format.sh, which pre-commit runs). The two format the same source
// differently when they load different versions of the same library, so drift here does not fail:
// it silently leaves the two paths contradicting each other about how the code should look.
//
// Two things are checked, each against an authoritative source rather than against a declaration:
//
//   - org.eclipse.jdt.core, against the eclipse_jdt_formatter/v<N>.lockfile that spotless-lib-extra
//     bundles for the eclipse('<N>') version in build.gradle. jbang-fmt hardcodes its own, older
//     jdt.core, which is why format.sh has to override it.
//   - com.diffplug.spotless:spotless-lib(-extra), against the spotless-lib the Spotless Gradle
//     plugin in libs.versions.toml actually depends on - the check buildSrc/build.gradle's comment
//     asks a human to do by hand.
//
// Declaration sites are discovered from the files themselves, so another jbang script with a
// //DEPS line on either coordinate is covered without touching the check.
//
// Run from the server root (see formatter-version-check.sh).
void main(String[] args) throws Exception {
    var ok = checkJdt();
    System.out.println();
    ok = checkSpotlessLib() && ok;
    if (!ok) {
        System.exit(1);
    }
}

boolean checkJdt() throws Exception {
    var eclipseVersion = findEclipseFormatterVersion();
    var libExtraVersion = findSpotlessLibExtraVersion();
    System.out.println("== org.eclipse.jdt.core ==");
    System.out.println("build.gradle formats with eclipse('" + eclipseVersion + "')");

    var sites = findJdtDeclarationSites();
    if (sites.isEmpty()) {
        System.err.println("No org.eclipse.jdt.core declarations found; has the layout changed?");
        System.exit(2);
    }

    // The lockfile is authoritative. Without it (offline, say) the declarations can still be
    // checked against each other, which catches drift between them - just not a stale bump.
    var lockfiles = readLockfileJdtVersions(libExtraVersion);
    String expected;
    String source;
    if (lockfiles == null) {
        var fallback = sites.getFirst();
        expected = fallback.version();
        source = "the other declarations";
        System.out.println(
                "warning: could not read spotless-lib-extra " + libExtraVersion + "; comparing the "
                        + "declarations against each other only, using " + fallback.label() + " as the reference.");
    } else if (!lockfiles.containsKey(eclipseVersion)) {
        System.out.println(
                "NO LOCKFILE: spotless-lib-extra " + libExtraVersion + " does not bundle a lockfile for "
                        + "eclipse('" + eclipseVersion + "'), so Spotless falls back to provisioning it live from "
                        + "Eclipse's P2 repository - which fails outright when that repository is not populated.");
        System.out.println("             Bundled versions go up to " + lockfiles.lastKey() + ".");
        return false;
    } else {
        expected = lockfiles.get(eclipseVersion);
        source = "the version eclipse('" + eclipseVersion + "') provisions";
        System.out.println(
                "eclipse('" + eclipseVersion + "') provisions jdt.core " + expected
                        + " (per eclipse_jdt_formatter/v" + eclipseVersion + ".lockfile)");
    }
    return report(sites, expected, source);
}

boolean checkSpotlessLib() throws Exception {
    var pluginVersion = findSpotlessPluginVersion();
    System.out.println("== com.diffplug.spotless:spotless-lib ==");
    System.out.println("libs.versions.toml applies the Spotless plugin " + pluginVersion);

    var sites = findSpotlessLibDeclarationSites();
    if (sites.isEmpty()) {
        System.err.println("No spotless-lib declarations found; has the layout changed?");
        System.exit(2);
    }

    var expected = readPluginSpotlessLibVersion(pluginVersion);
    String source;
    if (expected == null) {
        var fallback = sites.getFirst();
        expected = fallback.version();
        source = "the other declarations";
        System.out.println(
                "warning: could not read the Spotless plugin " + pluginVersion + " POM; comparing the "
                        + "declarations against each other only, using " + fallback.label() + " as the reference.");
    } else {
        source = "what the Spotless plugin " + pluginVersion + " depends on";
        System.out.println("Spotless plugin " + pluginVersion + " depends on spotless-lib " + expected);
    }
    return report(sites, expected, source);
}

boolean report(List<Site> sites, String expected, String source) {
    System.out.println();
    var drifted = false;
    for (Site site : sites) {
        if (site.version().equals(expected)) {
            System.out.println("ok: " + site.label() + " = " + site.version());
        } else {
            System.out.println("MISMATCH: " + site.label() + " = " + site.version() + ", expected " + expected);
            drifted = true;
        }
    }
    if (drifted) {
        System.out.println(
                "-> declarations disagree with " + source + "; set them all to " + expected + ".");
    }
    return !drifted;
}

record Site(String label, String version) {}

String findEclipseFormatterVersion() throws IOException {
    return matchOne(Path.of("build.gradle"), Pattern.compile("eclipse\\('([\\w.\\-]+)'\\)"), "eclipse('<version>')");
}

/** The [plugins] entry, whose `{ id = ..., version = "..." }` shape differs from [versions]. */
String findSpotlessPluginVersion() throws IOException {
    return matchOne(
            Path.of("gradle/libs.versions.toml"),
            Pattern.compile("^spotless\\s*=\\s*\\{[^}]*version\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE),
            "the [plugins] spotless entry");
}

String findSpotlessLibExtraVersion() throws IOException {
    return matchOne(
            Path.of("buildSrc/build.gradle"),
            Pattern.compile("com\\.diffplug\\.spotless:spotless-lib-extra:([\\w.\\-]+)"),
            "spotless-lib-extra coordinate");
}

/** Every file that names a concrete jdt.core version, in the shape that file declares it. */
List<Site> findJdtDeclarationSites() throws IOException {
    var gav = "org\\.eclipse\\.jdt:org\\.eclipse\\.jdt\\.core:([\\w.\\-]+)";
    var sites = new ArrayList<Site>();
    addSite(sites, Path.of("buildSrc/build.gradle"), Pattern.compile("implementation\\s+'" + gav + "'"));
    addSite(sites, Path.of("scripts/format.sh"), Pattern.compile("^JDT_VERSION=([\\w.\\-]+)", Pattern.MULTILINE));
    addScriptDepsSites(sites, gav);
    return sites;
}

/** spotless-lib and spotless-lib-extra are released together, so both are held to the one version. */
List<Site> findSpotlessLibDeclarationSites() throws IOException {
    var gav = "com\\.diffplug\\.spotless:(spotless-lib(?:-extra)?):([\\w.\\-]+)";
    var sites = new ArrayList<Site>();
    addSite(sites, Path.of("buildSrc/build.gradle"), Pattern.compile("implementation\\s+'" + gav + "'"));
    addScriptDepsSites(sites, gav);
    return sites;
}

/** Discovered rather than listed, so a new jbang script is covered automatically. */
void addScriptDepsSites(List<Site> sites, String gav) throws IOException {
    var depsPattern = Pattern.compile("^//DEPS\\s+" + gav, Pattern.MULTILINE);
    try (Stream<Path> scripts = Files.list(Path.of("scripts/src"))) {
        for (Path script : scripts.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
            addSite(sites, script, depsPattern);
        }
    }
}

/** Appends one Site per match, so a file declaring two of them is reported twice. The version is
 *  the last group; a preceding group, where the pattern has one, names the artifact so the two
 *  declarations in a single file can be told apart. */
void addSite(List<Site> sites, Path file, Pattern pattern) throws IOException {
    if (!Files.exists(file)) {
        return;
    }
    var matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
    while (matcher.find()) {
        var groups = matcher.groupCount();
        var label = groups > 1 ? file + " (" + matcher.group(groups - 1) + ")" : file.toString();
        sites.add(new Site(label, matcher.group(groups)));
    }
}

String matchOne(Path file, Pattern pattern, String what) throws IOException {
    var matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
    if (!matcher.find()) {
        System.err.println("Could not find " + what + " in " + file + "; has the layout changed?");
        System.exit(2);
    }
    return matcher.group(1);
}

/** The spotless-lib version the Spotless Gradle plugin depends on, or null if the POM is
 *  unavailable. Read straight out of the POM rather than resolved, which needs no build tooling. */
String readPluginSpotlessLibVersion(String pluginVersion) {
    Path pom;
    try {
        pom = locateArtifact("spotless-plugin-gradle", pluginVersion, "pom");
    } catch (Exception e) {
        System.err.println("warning: " + e);
        return null;
    }
    // The POM lists spotless-lib and spotless-lib-extra at the same version; either will do.
    var pattern = Pattern.compile(
            "<artifactId>spotless-lib(?:-extra)?</artifactId>\\s*<version>([^<]+)</version>");
    try {
        var matcher = pattern.matcher(Files.readString(pom, StandardCharsets.UTF_8));
        return matcher.find() ? matcher.group(1).strip() : null;
    } catch (IOException e) {
        System.err.println("warning: could not read " + pom + ": " + e);
        return null;
    }
}

/** Eclipse formatter version -> the jdt.core its lockfile pins, or null if the jar is unavailable.
 *  Sorted by version so the highest bundled one can be reported. */
TreeMap<String, String> readLockfileJdtVersions(String libExtraVersion) {
    Path jar;
    try {
        jar = locateArtifact("spotless-lib-extra", libExtraVersion, "jar");
    } catch (Exception e) {
        System.err.println("warning: " + e);
        return null;
    }

    var prefix = "com/diffplug/spotless/extra/eclipse_jdt_formatter/v";
    var suffix = ".lockfile";
    var pinPattern = Pattern.compile("^org\\.eclipse\\.jdt:org\\.eclipse\\.jdt\\.core:(.+)$", Pattern.MULTILINE);
    var versions = new TreeMap<String, String>(versionOrder());
    try (var zip = new ZipFile(jar.toFile())) {
        for (var entries = zip.entries(); entries.hasMoreElements();) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (!name.startsWith(prefix) || !name.endsWith(suffix)) {
                continue;
            }
            var content = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            var pin = pinPattern.matcher(content);
            if (pin.find()) {
                versions.put(name.substring(prefix.length(), name.length() - suffix.length()), pin.group(1).strip());
            }
        }
    } catch (IOException e) {
        System.err.println("warning: could not read " + jar + ": " + e);
        return null;
    }
    return versions.isEmpty() ? null : versions;
}

/** Prefers an already-downloaded copy so the check works offline, then falls back to Maven Central.
 *  Only com.diffplug.spotless artifacts are needed, so the group is not a parameter. */
Path locateArtifact(String artifact, String version, String extension) throws Exception {
    var fileName = artifact + "-" + version + "." + extension;
    var home = Path.of(System.getProperty("user.home"));
    var m2 = home.resolve(".m2/repository/com/diffplug/spotless").resolve(artifact).resolve(version).resolve(fileName);
    if (Files.isRegularFile(m2)) {
        return m2;
    }
    // The Gradle cache interposes a hash directory between the version and the file.
    var gradleCache = home.resolve(".gradle/caches/modules-2/files-2.1/com.diffplug.spotless")
            .resolve(artifact).resolve(version);
    if (Files.isDirectory(gradleCache)) {
        try (Stream<Path> found = Files.walk(gradleCache, 2)) {
            var hit = found.filter(p -> p.getFileName().toString().equals(fileName)).findFirst();
            if (hit.isPresent()) {
                return hit.get();
            }
        }
    }

    var url = "https://repo1.maven.org/maven2/com/diffplug/spotless/" + artifact + "/" + version + "/" + fileName;
    var target = Files.createTempFile(artifact, "." + extension);
    target.toFile().deleteOnExit();
    try (var client = HttpClient.newHttpClient()) {
        var response = client.send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
        }
    }
    return target;
}

/** Numeric per segment, so 4.9 sorts below 4.10 rather than above it. */
Comparator<String> versionOrder() {
    return (left, right) -> {
        var leftParts = left.split("\\.");
        var rightParts = right.split("\\.");
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            var cmp = Integer.compare(segment(leftParts, i), segment(rightParts, i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    };
}

/** Missing and non-numeric segments count as 0 - enough to order the bundled formatter versions,
 *  which are all plain <major>.<minor>. */
int segment(String[] parts, int index) {
    if (index >= parts.length) {
        return 0;
    }
    try {
        return Integer.parseInt(parts[index]);
    } catch (NumberFormatException e) {
        return 0;
    }
}
