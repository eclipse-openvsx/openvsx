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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.util.TempFile;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionProcessorTest {

    private static final int NO_LIMIT = -1;

    @Test
    void testTodoTree() throws Exception {
        try (
                var file = writeToTempFile("util/todo-tree.zip");
                var processor = new ExtensionProcessor(file)
        ) {
            assertThat(processor.getNamespace()).isEqualTo("Gruntfuggly");
            assertThat(processor.getExtensionName()).isEqualTo("todo-tree");

            var metadata = processor.getMetadata();
            assertThat(metadata.getVersion()).isEqualTo("0.0.213");
            assertThat(metadata.getDisplayName()).isEqualTo("Todo Tree");
            assertThat(metadata.getDescription()).isEqualTo("Show TODO, FIXME, etc. comment tags in a tree view");
            assertThat(metadata.getEngines()).isEqualTo(Arrays.asList("vscode@^1.46.0"));
            assertThat(metadata.getCategories()).isEqualTo(Arrays.asList("Other"));
            assertThat(metadata.getTags()).isEqualTo(Arrays.asList("multi-root ready", "task", "tasklist", "todo"));
            assertThat(metadata.getLicense()).isEqualTo("MIT");
            assertThat(metadata.getRepository()).isEqualTo("https://github.com/Gruntfuggly/todo-tree");

            checkReadme(processor, "README.md");
            checkIcon(processor, "todo-tree.png");
            checkLicense(processor, "License.txt");
        }
    }

    @Test
    void testChangelog() throws Exception {
        try (
                var file = writeToTempFile("util/changelog.zip");
                var processor = new ExtensionProcessor(file)
        ) {
            checkChangelog(processor, "CHANGELOG.md");
        }
    }

    @Test
    void testCapitalizedCaseForResources() throws Exception {
        try (
                var file = writeToTempFile("util/with-capitalized-case.zip");
                var processor = new ExtensionProcessor(file)
        ) {
            checkChangelog(processor, "Changelog.md");
            checkReadme(processor, "Readme.md");
            checkLicense(processor, "License.txt");
        }
    }

    @Test
    void testMinorCaseForResources() throws Exception {
        try (
                var file = writeToTempFile("util/with-minor-case.zip");
                var processor = new ExtensionProcessor(file)
        ) {
            checkChangelog(processor, "changelog.md");
            checkReadme(processor, "readme.md");
            checkLicense(processor, "license.txt");
        }
    }

    @Test
    void testMissingIcon() throws Exception {
        try (
                var file = writeToTempFile("util/with-missing-icon.zip");
                var processor = new ExtensionProcessor(file)
        ) {
            AtomicInteger count = new AtomicInteger(0);
            Consumer<TempFile> consumer = _ -> count.incrementAndGet();
            processor.getFileResources(processor.getMetadata(), consumer);
            assertThat(count.get()).isEqualTo(5);
        }
    }

    @Test
    void testTagsAreCappedAtTheDefaultLimit() throws Exception {
        var declaredTags = tags(35);
        try (
                var file = writeTagsToTempFile(declaredTags);
                var processor = new ExtensionProcessor(file)
        ) {
            // The tags an extension declares first are the ones that are kept.
            assertThat(processor.getMetadata().getTags())
                    .hasSize(30)
                    .containsExactlyElementsOf(declaredTags.subList(0, 30));
        }
    }

    @Test
    void testTagLimitIsConfigurable() throws Exception {
        var declaredTags = tags(25);
        try (
                var file = writeTagsToTempFile(declaredTags);
                var processor = new ExtensionProcessor(file)
        ) {
            assertThat(processor.getMetadata(3, NO_LIMIT).getTags())
                    .containsExactlyElementsOf(declaredTags.subList(0, 3));
        }
    }

    @Test
    void testTagLimitIsDisabledForANegativeLimit() throws Exception {
        var declaredTags = tags(25);
        try (
                var file = writeTagsToTempFile(declaredTags);
                var processor = new ExtensionProcessor(file)
        ) {
            assertThat(processor.getMetadata(NO_LIMIT, NO_LIMIT).getTags())
                    .containsExactlyElementsOf(declaredTags);
        }
    }

    @Test
    void testTagLimitCountsDistinctTagsOnly() throws Exception {
        try (
                var file = writeTagsToTempFile(List.of("Todo", "todo", "task"));
                var processor = new ExtensionProcessor(file)
        ) {
            // The duplicate does not take up one of the two slots, and the first spelling wins.
            assertThat(processor.getMetadata(2, NO_LIMIT).getTags()).containsExactly("task", "Todo");
        }
    }

    @Test
    void testInternalTagsDoNotCountAgainstTheTagLimit() throws Exception {
        var internalTags = internalTags(3);
        var declaredTags = tags(2);
        try (
                var file = writeTagsToTempFile(concat(internalTags, declaredTags));
                var processor = new ExtensionProcessor(file)
        ) {
            // Both tags the extension declares are kept, even though the internal ones alone already
            // exhaust the limit.
            assertThat(processor.getMetadata(2, NO_LIMIT).getTags())
                    .containsExactlyElementsOf(concat(internalTags, declaredTags));
        }
    }

    @Test
    void testInternalTagsAreCappedAtTheirOwnLimit() throws Exception {
        var internalTags = internalTags(5);
        var declaredTags = tags(2);
        try (
                var file = writeTagsToTempFile(concat(internalTags, declaredTags));
                var processor = new ExtensionProcessor(file)
        ) {
            assertThat(processor.getMetadata(NO_LIMIT, 2).getTags())
                    .containsExactlyElementsOf(concat(internalTags.subList(0, 2), declaredTags));
        }
    }

    @Test
    void testInternalTagsAreCappedAtTheDefaultLimit() throws Exception {
        var internalTags = internalTags(105);
        try (
                var file = writeTagsToTempFile(internalTags);
                var processor = new ExtensionProcessor(file)
        ) {
            assertThat(processor.getMetadata().getTags())
                    .hasSize(100)
                    .containsExactlyElementsOf(internalTags.subList(0, 100));
        }
    }

    private List<String> tags(int count) {
        // Named so that the alphabetical order the tags are stored in matches the declaration order.
        return IntStream.rangeClosed(1, count).mapToObj(i -> String.format("tag-%03d", i)).toList();
    }

    private List<String> internalTags(int count) {
        // Internal tags sort before the declared ones, matching the order they are expected in.
        return IntStream.rangeClosed(1, count).mapToObj(i -> String.format("__ext_%03d", i)).toList();
    }

    private List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    /**
     * Writes a package that declares the given tags, based on an existing extension so that the rest of
     * the manifest stays valid.
     */
    private TempFile writeTagsToTempFile(List<String> tags) throws IOException {
        var target = new TempFile("test", ".zip");
        try (
                var source = writeToTempFile("util/todo-tree.zip");
                var zipFile = new ZipFile(source.getPath().toFile());
                var out = new ZipOutputStream(Files.newOutputStream(target.getPath()))
        ) {
            for (var name : List.of("extension.vsixmanifest", "extension/package.json")) {
                var content = new String(
                        zipFile.getInputStream(zipFile.getEntry(name)).readAllBytes(),
                        StandardCharsets.UTF_8);
                if (name.equals("extension.vsixmanifest")) {
                    content = content.replace(
                            "<Tags>todo,task,tasklist,multi-root ready</Tags>",
                            "<Tags>" + String.join(",", tags) + "</Tags>");
                }

                out.putNextEntry(new ZipEntry(name));
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }

        return target;
    }

    private TempFile writeToTempFile(String resource) throws IOException {
        var file = new TempFile("test", ".zip");
        try (
                var in = getClass().getResourceAsStream(resource);
                var out = Files.newOutputStream(file.getPath());
        ) {
            in.transferTo(out);
        }

        return file;
    }

    private void checkChangelog(ExtensionProcessor processor, String expectedName) throws IOException {
        var metadata = processor.getMetadata();
        try (var changelogFile = processor.getChangelog(metadata)) {
            checkResource(changelogFile.getResource(), FileResource.CHANGELOG, expectedName);
        }
    }

    private void checkIcon(ExtensionProcessor processor, String expectedName) throws IOException {
        var metadata = processor.getMetadata();
        try (var iconFile = processor.getIcon(metadata)) {
            checkResource(iconFile.getResource(), FileResource.ICON, expectedName);
        }
    }

    private void checkLicense(ExtensionProcessor processor, String expectedName) throws IOException {
        var metadata = processor.getMetadata();
        try (var licenseFile = processor.getLicense(metadata)) {
            checkResource(licenseFile.getResource(), FileResource.LICENSE, expectedName);
        }
    }

    private void checkReadme(ExtensionProcessor processor, String expectedName) throws IOException {
        var metadata = processor.getMetadata();
        try (var readmeFile = processor.getReadme(metadata)) {
            checkResource(readmeFile.getResource(), FileResource.README, expectedName);
        }
    }

    private void checkResource(FileResource resource, String expectedType, String expectedName) {
        assertThat(resource.getType()).isEqualTo(expectedType);
        assertThat(resource.getName()).isEqualTo(expectedName);
    }
}
