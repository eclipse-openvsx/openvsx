/******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.persistence.Embeddable;
import org.apache.commons.lang3.StringUtils;

@Embeddable
public class SemanticVersion implements Comparable<SemanticVersion>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // source: https://semver.org/, search for: https://regex101.com/r/vkijKf/1/
    public static final Pattern VERSION_PARSE_PATTERN = Pattern.compile(
            "^(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)\\.(?<patch>0|[1-9]\\d*)(?:-(?<prerelease>(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+(?<buildmetadata>[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    // has been modified to only use non-capturing groups (?:.*), so that it can be used as a URI template regex
    public static final String VERSION_PATH_PARAM_REGEX = "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)(?:-(?:(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+(?:[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?";

    public static SemanticVersion parse(String version) {
        var matcher = VERSION_PARSE_PATTERN.matcher(version);
        matcher.find();

        var semver = new SemanticVersion();
        semver.setMajor(Integer.parseInt(matcher.group("major")));
        semver.setMinor(Integer.parseInt(matcher.group("minor")));
        semver.setPatch(Integer.parseInt(matcher.group("patch")));
        semver.setPreRelease(matcher.group("prerelease"));
        semver.setBuildMetadata(matcher.group("buildmetadata"));
        return semver;
    }

    private int major;
    private int minor;
    private int patch;
    private String preRelease;
    private boolean isPreRelease;
    private String buildMetadata;

    public int getMajor() {
        return major;
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public int getMinor() {
        return minor;
    }

    public void setMinor(int minor) {
        this.minor = minor;
    }

    public int getPatch() {
        return patch;
    }

    public void setPatch(int patch) {
        this.patch = patch;
    }

    public String getPreRelease() {
        return preRelease;
    }

    public void setPreRelease(String preRelease) {
        this.preRelease = preRelease;
        this.isPreRelease = !StringUtils.isEmpty(preRelease);
    }

    public boolean isIsPreRelease() {
        return isPreRelease;
    }

    public void setIsPreRelease(boolean isPreRelease) {
        // do nothing, property is derived from preRelease
    }

    public String getBuildMetadata() {
        return buildMetadata;
    }

    public void setBuildMetadata(String buildMetadata) {
        this.buildMetadata = buildMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SemanticVersion that = (SemanticVersion) o;
        return major == that.major
                && minor == that.minor
                && patch == that.patch
                && isPreRelease == that.isPreRelease
                && Objects.equals(preRelease, that.preRelease)
                && Objects.equals(buildMetadata, that.buildMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease, isPreRelease, buildMetadata);
    }

    @Override
    public int compareTo(SemanticVersion that) {
        List<Supplier<Integer>> comparators = List.of(
                () -> Integer.compare(that.getMajor(), this.getMajor()),
                () -> Integer.compare(that.getMinor(), this.getMinor()),
                () -> Integer.compare(that.getPatch(), this.getPatch()),
                () -> -Boolean.compare(that.isIsPreRelease(), this.isIsPreRelease()));

        var compare = 0;
        for (var comparator : comparators) {
            compare = comparator.get();
            if (compare != 0) {
                return compare;
            }
        }

        return compare;
    }
}
