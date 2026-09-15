/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.adapter;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import static org.eclipse.openvsx.util.TargetPlatform.*;

// Keep interfaces in sync with
// https://github.com/microsoft/vscode/blob/de0724b414e2f95f6cc484b03bccbc96686c2cfd/src/vs/platform/extensionManagement/common/extensionGalleryService.ts#L34-L81
public record ExtensionQueryResult(List<ResultItem> results) {

    public record ResultItem(List<Extension> extensions, List<ResultMetadata> resultMetadata) {}

    public record Extension(
            @Schema(
                description = "Extension identifier in the format {publisher}.{name}",
                example = "foo.bar"
            ) String extensionId,
            @Schema(description = "Name of the extension") String extensionName,
            @Schema(description = "Name to be displayed in user interfaces") String displayName,
            @Schema(description = "Short description of the extension") String shortDescription,
            Publisher publisher,
            List<ExtensionVersion> versions,
            List<Statistic> statistics,
            List<String> tags,
            @Schema(
                description = "Date and time when this extension was released (ISO-8601, same as published date)"
            ) String releaseDate,
            @Schema(description = "Date and time when this extension was published (ISO-8601)") String publishedDate,
            @Schema(description = "Date and time when this extension was last updated (ISO-8601)") String lastUpdated,
            List<String> categories,
            @Schema(description = "Flag extension as preview") String flags
    ) {
        public static final String FLAG_PREVIEW = "preview";
    }

    public record Publisher(
            @Schema(description = "Publisher name to be displayed in user interfaces") String displayName,
            @Schema(description = "Public id of the publisher (UUID)") String publisherId,
            @Schema(description = "Name of the publisher") String publisherName,
            @Schema(
                description = "Web domain of the publisher, not implemented",
                allowableValues = { "null" }
            ) String domain,
            @Schema(
                description = "Whether the publisher's web domain is verified, not implemented",
                allowableValues = { "null" }
            ) Boolean isDomainVerified
    ) {}

    public record ExtensionVersion(
            String version,
            @Schema(description = "Date and time when this version was last updated (ISO-8601)") String lastUpdated,
            @Schema(description = "URL to get extension version assets") String assetUri,
            @Schema(description = "Fallback URL to get extension version assets") String fallbackAssetUri,
            List<ExtensionFile> files,
            List<Property> properties,
            @Schema(
                description = "Name of the target platform",
                allowableValues = {
                    NAME_WIN32_X64,
                    NAME_WIN32_IA32,
                    NAME_WIN32_ARM64,
                    NAME_LINUX_X64,
                    NAME_LINUX_ARM64,
                    NAME_LINUX_ARMHF,
                    NAME_ALPINE_X64,
                    NAME_ALPINE_ARM64,
                    NAME_DARWIN_X64,
                    NAME_DARWIN_ARM64,
                    NAME_WEB,
                    NAME_UNIVERSAL
                }
            ) String targetPlatform
    ) {}

    public record ExtensionFile(
            @Schema(
                description = "Type of the extension file",
                allowableValues = {
                    FILE_ICON,
                    FILE_DETAILS,
                    FILE_CHANGELOG,
                    FILE_MANIFEST,
                    FILE_VSIX,
                    FILE_LICENSE,
                    FILE_WEB_RESOURCES,
                    FILE_VSIXMANIFEST,
                    FILE_SIGNATURE,
                    FILE_PUBLIC_KEY
                }
            ) String assetType,
            @Schema(description = "URL to get the extension file") String source
    ) {
        public static final String FILE_ICON = "Microsoft.VisualStudio.Services.Icons.Default";
        public static final String FILE_DETAILS = "Microsoft.VisualStudio.Services.Content.Details";
        public static final String FILE_CHANGELOG = "Microsoft.VisualStudio.Services.Content.Changelog";
        public static final String FILE_MANIFEST = "Microsoft.VisualStudio.Code.Manifest";
        public static final String FILE_VSIX = "Microsoft.VisualStudio.Services.VSIXPackage";
        public static final String FILE_LICENSE = "Microsoft.VisualStudio.Services.Content.License";
        public static final String FILE_WEB_RESOURCES = "Microsoft.VisualStudio.Code.WebResources";
        public static final String FILE_VSIXMANIFEST = "Microsoft.VisualStudio.Services.VsixManifest";
        public static final String FILE_SIGNATURE = "Microsoft.VisualStudio.Services.VsixSignature";
        public static final String FILE_PUBLIC_KEY = "Microsoft.VisualStudio.Services.PublicKey";
    }

    public record Property(
            @Schema(
                description = "Identifier of the property",
                allowableValues = {
                    PROP_REPOSITORY,
                    PROP_SPONSOR_LINK,
                    PROP_DEPENDENCY,
                    PROP_EXTENSION_PACK,
                    PROP_ENGINE,
                    PROP_LOCALIZED_LANGUAGES,
                    PROP_BRANDING_COLOR,
                    PROP_BRANDING_THEME,
                    PROP_WEB_EXTENSION,
                    PROP_PRE_RELEASE
                }
            ) String key,
            @Schema(description = "Value of the property") String value
    ) {
        public static final String PROP_REPOSITORY = "Microsoft.VisualStudio.Services.Links.Source";
        public static final String PROP_SPONSOR_LINK = "Microsoft.VisualStudio.Code.SponsorLink";
        public static final String PROP_DEPENDENCY = "Microsoft.VisualStudio.Code.ExtensionDependencies";
        public static final String PROP_EXTENSION_PACK = "Microsoft.VisualStudio.Code.ExtensionPack";
        public static final String PROP_ENGINE = "Microsoft.VisualStudio.Code.Engine";
        public static final String PROP_LOCALIZED_LANGUAGES = "Microsoft.VisualStudio.Code.LocalizedLanguages";
        public static final String PROP_BRANDING_COLOR = "Microsoft.VisualStudio.Services.Branding.Color";
        public static final String PROP_BRANDING_THEME = "Microsoft.VisualStudio.Services.Branding.Theme";
        public static final String PROP_WEB_EXTENSION = "Microsoft.VisualStudio.Code.WebExtension";
        public static final String PROP_PRE_RELEASE = "Microsoft.VisualStudio.Code.PreRelease";
    }

    public record Statistic(
            @Schema(
                description = "Name of the statistic",
                allowableValues = {
                    STAT_INSTALL,
                    STAT_AVERAGE_RATING,
                    STAT_RATING_COUNT
                }
            ) String statisticName,
            @Schema(description = "Value of the statistic") double value
    ) {
        public static final String STAT_INSTALL = "install";
        public static final String STAT_AVERAGE_RATING = "averagerating";
        public static final String STAT_RATING_COUNT = "ratingcount";
    }

    public record ResultMetadata(String metadataType, List<ResultMetadataItem> metadataItems) {}

    public record ResultMetadataItem(String name, long count) {}
}
