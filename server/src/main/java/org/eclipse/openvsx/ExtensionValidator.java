/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.SemanticVersion;
import org.eclipse.openvsx.json.NamespaceDetailsJson;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;

@Component
public class ExtensionValidator {

    private static final List<String> MARKDOWN_VALUES = List.of("github", "standard");

    private static final List<String> GALLERY_THEME_VALUES = List.of("dark", "light");

    private static final List<String> QNA_VALUES = List.of("marketplace", "false");

    private static final int DEFAULT_STRING_SIZE = 255;
    private static final int DESCRIPTION_SIZE = 2048;
    private static final int GALLERY_COLOR_SIZE = 16;

    private final Pattern namePattern = Pattern.compile("[\\w\\-\\+\\$~]+");

    public Optional<Issue> validateNamespace(String namespace) {
        if (StringUtils.isEmpty(namespace) || namespace.equals("-")) {
            return Optional.of(new Issue("Namespace name must not be empty."));
        }
        if (!namePattern.matcher(namespace).matches()) {
            return Optional.of(new Issue("Invalid namespace name: " + namespace));
        }
        if (namespace.length() > DEFAULT_STRING_SIZE) {
            return Optional.of(new Issue(charactersExceededMessage("namespace name", DEFAULT_STRING_SIZE)));
        }
        return Optional.empty();
    }

    private void validateDisplayName(String displayName, int limit, List<Issue> issues) {
        var field = "displayName";
        checkCharacters(displayName, field, issues);
        checkFieldSize(displayName, limit, field, issues);
    }

    private void validateDescription(String description, int limit, List<Issue> issues) {
        var field = "description";
        var zeroWidthJoinerChar = '\u200D'; // character that allows combining multiple emojis into one (https://en.wikipedia.org/wiki/Zero-width_joiner)
        checkCharacters(description, field, issues, List.of(zeroWidthJoinerChar));
        checkFieldSize(description, limit, field, issues);
    }

    public List<Issue> validateNamespaceDetails(NamespaceDetailsJson json) {
        var issues = new ArrayList<Issue>();
        validateDisplayName(json.getDisplayName(), 32, issues);
        validateDescription(json.getDescription(), DEFAULT_STRING_SIZE, issues);
        checkURL(json.getWebsite(), "website", issues);
        checkURL(json.getSupportLink(), "supportLink", issues);

        var githubLink = json.getSocialLinks().get("github");
        if (githubLink != null && !githubLink.matches("https:\\/\\/github\\.com\\/[^\\/]+")) {
            issues.add(new Issue("Invalid GitHub URL"));
        }
        var linkedinLink = json.getSocialLinks().get("linkedin");
        if (linkedinLink != null
                && !linkedinLink.matches("https:\\/\\/www\\.linkedin\\.com\\/(company|in)\\/[^\\/]+")) {
            issues.add(new Issue("Invalid LinkedIn URL"));
        }
        var twitterLink = json.getSocialLinks().get("twitter");
        if (twitterLink != null && !twitterLink.matches("https:\\/\\/twitter\\.com\\/[^\\/]+")) {
            issues.add(new Issue("Invalid Twitter URL"));
        }

        return issues;
    }

    public Optional<Issue> validateExtensionName(String name) {
        if (StringUtils.isEmpty(name)) {
            return Optional.of(new Issue("Name must not be empty."));
        }
        if (!namePattern.matcher(name).matches()) {
            return Optional.of(new Issue("Invalid extension name: " + name));
        }
        if (name.length() > DEFAULT_STRING_SIZE) {
            return Optional.of(new Issue(charactersExceededMessage("extension name", DEFAULT_STRING_SIZE)));
        }
        return Optional.empty();
    }

    public Optional<Issue> validateExtensionVersion(String version) {
        var issues = new ArrayList<Issue>();
        checkVersion(version, issues);
        return issues.isEmpty()
                ? Optional.empty()
                : Optional.of(issues.getFirst());
    }

    public List<Issue> validateMetadata(ExtensionVersion extVersion) {
        var issues = new ArrayList<Issue>();
        checkVersion(extVersion.getVersion(), issues);
        checkTargetPlatform(extVersion.getTargetPlatform(), issues);
        validateDisplayName(extVersion.getDisplayName(), DEFAULT_STRING_SIZE, issues);
        validateDescription(extVersion.getDescription(), DESCRIPTION_SIZE, issues);
        checkCharacters(extVersion.getCategories(), "categories", issues);
        checkFieldSize(extVersion.getCategories(), DEFAULT_STRING_SIZE, "categories", issues);
        checkCharacters(extVersion.getTags(), "keywords", issues);
        checkFieldSize(extVersion.getTags(), DEFAULT_STRING_SIZE, "keywords", issues);
        checkCharacters(extVersion.getLicense(), "license", issues);
        checkFieldSize(extVersion.getLicense(), DEFAULT_STRING_SIZE, "license", issues);
        checkURL(extVersion.getHomepage(), "homepage", issues);
        checkFieldSize(extVersion.getHomepage(), DEFAULT_STRING_SIZE, "homepage", issues);
        checkURL(extVersion.getRepository(), "repository", issues);
        checkFieldSize(extVersion.getRepository(), DEFAULT_STRING_SIZE, "repository", issues);
        checkURL(extVersion.getBugs(), "bugs", issues);
        checkFieldSize(extVersion.getBugs(), DEFAULT_STRING_SIZE, "bugs", issues);
        checkInvalid(
                extVersion.getMarkdown(),
                s -> !MARKDOWN_VALUES.contains(s),
                "markdown",
                issues,
                MARKDOWN_VALUES.toString());
        checkCharacters(extVersion.getGalleryColor(), "galleryBanner.color", issues);
        checkFieldSize(extVersion.getGalleryColor(), GALLERY_COLOR_SIZE, "galleryBanner.color", issues);
        checkInvalid(
                extVersion.getGalleryTheme(),
                s -> !GALLERY_THEME_VALUES.contains(s),
                "galleryBanner.theme",
                issues,
                GALLERY_THEME_VALUES.toString());
        checkFieldSize(extVersion.getLocalizedLanguages(), DEFAULT_STRING_SIZE, "localizedLanguages", issues);
        checkInvalid(
                extVersion.getQna(),
                s -> !QNA_VALUES.contains(s) && isInvalidURL(s),
                "qna",
                issues,
                QNA_VALUES.toString() + " or a URL");
        checkFieldSize(extVersion.getQna(), DEFAULT_STRING_SIZE, "qna", issues);
        return issues;
    }

    private void checkVersion(String version, List<Issue> issues) {
        if (StringUtils.isEmpty(version)) {
            issues.add(new Issue("Version must not be empty."));
            return;
        }
        if (version.equals(VersionAlias.LATEST) || version.equals(VersionAlias.PRE_RELEASE)
                || version.equals("reviews")) {
            issues.add(new Issue("The version string '" + version + "' is reserved."));
        }

        try {
            SemanticVersion.parse(version);
        } catch (IllegalArgumentException | IllegalStateException e) {
            issues.add(new Issue("Invalid semantic version. See https://semver.org/."));
        }
    }

    private void checkTargetPlatform(String targetPlatform, List<Issue> issues) {
        if (!TargetPlatform.isValid(targetPlatform)) {
            issues.add(new Issue("Unsupported target platform '" + targetPlatform + "'"));
        }
    }

    private void checkCharacters(String value, String field, List<Issue> issues) {
        checkCharacters(value, field, issues, Collections.emptyList());
    }

    private void checkCharacters(String value, String field, List<Issue> issues, List<Character> allowedChars) {
        if (value == null) {
            return;
        }

        for (var i = 0; i < value.length(); i++) {
            var character = value.charAt(i);
            if (allowedChars.contains(character)) {
                continue;
            }

            var type = Character.getType(character);
            if (type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.UNASSIGNED || type == Character.PRIVATE_USE
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                issues.add(
                        new Issue("Invalid character found in field '" + field + "': " + value + " (index " + i + ")"));
                return;
            }
        }
    }

    private void checkCharacters(List<String> values, String field, List<Issue> issues) {
        if (values == null) {
            return;
        }
        for (var value : values) {
            checkCharacters(value, field, issues);
        }
    }

    private void checkFieldSize(String value, int limit, String field, List<Issue> issues) {
        if (value != null && value.length() > limit) {
            issues.add(new Issue(charactersExceededMessage("field '" + field + "'", limit)));
        }
    }

    private String charactersExceededMessage(String name, int limit) {
        return "The " + name + " exceeds the current limit of " + limit + " characters.";
    }

    private void checkFieldSize(List<String> values, int limit, String field, List<Issue> issues) {
        if (values == null) {
            return;
        }
        for (var value : values) {
            checkFieldSize(value, limit, field, issues);
        }
    }

    private void checkInvalid(
            String value,
            Predicate<String> isInvalid,
            String field,
            List<Issue> issues,
            String allowedValues
    ) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        if (isInvalid.test(value)) {
            issues.add(
                    new Issue(
                            "Invalid value in field '" + field + "': " + value
                                    + ". Allowed values: " + allowedValues));
        }
    }

    private void checkURL(String value, String field, List<Issue> issues) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        if (isInvalidURL(value)) {
            issues.add(new Issue("Invalid URL in field '" + field + "': " + value));
        }
    }

    private boolean isInvalidURL(String value) {
        if (StringUtils.isEmpty(value)) {
            return true;
        }
        if (value.startsWith("git+") && value.length() > 4) {
            value = value.substring(4);
        }

        try {
            var uri = new URI(value);
            if (!uri.isAbsolute()) {
                return true;
            }

            var url = uri.toURL();
            return url.getProtocol().matches("http(s)?") && StringUtils.isEmpty(url.getHost());
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException exc) {
            return true;
        }
    }

    public static class Issue {

        private final String message;

        Issue(String message) {
            this.message = message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Issue issue = (Issue) o;
            return Objects.equals(message, issue.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }

        @Override
        public String toString() {
            return message;
        }

        public String getMessage() {
            return message;
        }
    }

}
