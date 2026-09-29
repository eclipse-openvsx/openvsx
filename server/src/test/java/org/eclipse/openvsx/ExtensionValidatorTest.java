/*******************************************************************************
 * Copyright (c) 2020 TypeFox and others
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
package org.eclipse.openvsx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.util.TargetPlatform;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
class ExtensionValidatorTest {

    @Autowired
    ExtensionValidator validator;

    @Test
    void testInvalidVersion1() {
        var issue = validator.validateExtensionVersion("latest");
        assertThat(issue).isPresent();
        assertThat(issue.get())
                .isEqualTo(new ExtensionValidator.Issue("The version string 'latest' is reserved."));
    }

    @Test
    void testInvalidVersion2() {
        var issue = validator.validateExtensionVersion("1/2");
        assertThat(issue).isPresent();
        assertThat(issue.get())
                .isEqualTo(new ExtensionValidator.Issue("Invalid semantic version. See https://semver.org/."));
    }

    @Test
    void testInvalidTargetPlatform() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform("debian-x64");
        extension.setVersion("1.0.0");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst())
                .isEqualTo(new ExtensionValidator.Issue("Unsupported target platform 'debian-x64'"));
    }

    @Test
    void testInvalidURL() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("Foo and bar!");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst())
                .isEqualTo(new ExtensionValidator.Issue("Invalid URL in field 'repository': Foo and bar!"));
    }

    @Test
    void testInvalidURL2() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("https://");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst())
                .isEqualTo(new ExtensionValidator.Issue("Invalid URL in field 'repository': https://"));
    }

    @Test
    void testInvalidURL3() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("http://");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst())
                .isEqualTo(new ExtensionValidator.Issue("Invalid URL in field 'repository': http://"));
    }

    @Test
    void testNonAbsoluteURL() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("github.com/Foo/Bar");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst())
                .isEqualTo(new ExtensionValidator.Issue("Invalid URL in field 'repository': github.com/Foo/Bar"));
    }

    @Test
    void testNonAbsoluteURLWithGitPrefix() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("git+github.com/Foo/Bar");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst())
                .isEqualTo(new ExtensionValidator.Issue("Invalid URL in field 'repository': git+github.com/Foo/Bar"));
    }

    @Test
    void testMailtoURL() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("mailto:foo@bar.net");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).isEmpty();
    }

    @Test
    void testGitProtocol() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setRepository("git+https://github.com/Foo/Bar.git");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).isEmpty();
    }

    @Test
    void testDescription() {
        var extension = new ExtensionVersion();
        extension.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extension.setVersion("1.0.0");
        extension.setDescription("\uD83C\uDFC3\u200D♂\uFE0F Jump/Select to the Start/End of a word in VSCode");
        var issues = validator.validateMetadata(extension);
        assertThat(issues).isEmpty();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ExtensionValidator extensionValidator() {
            return new ExtensionValidator();
        }
    }
}
