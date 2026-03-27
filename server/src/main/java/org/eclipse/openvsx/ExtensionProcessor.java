/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.adapter.ExtensionQueryResult;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Processes uploaded extension files and extracts their metadata.
 */
public class ExtensionProcessor implements AutoCloseable {

    private static final String VSIX_MANIFEST = "extension.vsixmanifest";
    private static final String PACKAGE_JSON = "extension/package.json";
    private static final String[] README = { "extension/README.md", "extension/README", "extension/README.txt" };
    private static final String[] CHANGELOG = { "extension/CHANGELOG.md", "extension/CHANGELOG", "extension/CHANGELOG.txt" };

    private static final String MANIFEST_METADATA = "Metadata";
    private static final String MANIFEST_IDENTITY = "Identity";
    private static final String MANIFEST_PROPERTIES = "Properties";
    private static final String MANIFEST_PROPERTY = "Property";
    private static final String MANIFEST_VALUE = "Value";
    
    protected final Logger logger = LoggerFactory.getLogger(ExtensionProcessor.class);

    private final TempFile extensionFile;

    private ZipFile zipFile;
    private JsonNode packageJson;
    private JsonNode vsixManifest;

    public ExtensionProcessor(TempFile extensionFile) {
        this.extensionFile = extensionFile;
    }

    @Override
    public void close() {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (IOException exc) {
                throw new ServerErrorException("Failed to close extension package", exc);
            }
        }
    }

    private void readInputStream() {
        if (zipFile != null) {
            return;
        }
        try {
            zipFile = new ZipFile(extensionFile.getPath().toFile());
        } catch (ZipException exc) {
            throw new ErrorResultException("Could not read zip file: " + exc.getMessage());
        } catch (IOException exc) {
            throw new ErrorResultException("Could not read from input stream: " + exc.getMessage());
        }
    }

    private void loadPackageJson() {
        if (packageJson != null) {
            return;
        }
        readInputStream();

        // Read package.json
        try (var entryFile = ArchiveUtil.readEntry(zipFile, PACKAGE_JSON)) {
            if (entryFile == null) {
                throw new ErrorResultException(entryNotFoundMessage(PACKAGE_JSON));
            }

            var mapper = new ObjectMapper();
            packageJson = mapper.readTree(entryFile.getPath().toFile());
        } catch (JsonParseException exc) {
            throw new ErrorResultException("Invalid JSON format in " + PACKAGE_JSON
                    + ": " + exc.getMessage());
        } catch (IOException exc) {
            throw new ErrorResultException("Failed to load package.json", exc);
        }
    }

    private void loadVsixManifest() {
        if (vsixManifest != null) {
            return;
        }

        readInputStream();

        // Read extension.vsixmanifest
        try (var entryFile = ArchiveUtil.readEntry(zipFile, VSIX_MANIFEST)) {
            if (entryFile == null) {
                throw new ErrorResultException(entryNotFoundMessage(VSIX_MANIFEST));
            }

            var mapper = new XmlMapper();
            vsixManifest = mapper.readTree(entryFile.getPath().toFile());
        } catch (JsonParseException exc) {
            throw new ErrorResultException("Invalid JSON format in " + VSIX_MANIFEST
                    + ": " + exc.getMessage());
        } catch (IOException exc) {
            throw new ServerErrorException("Failed to read extension.vsixmanifest file", exc);
        }
    }

    private String entryNotFoundMessage(String file) {
        return "Entry not found: " + file;
    }

    private JsonNode findByIdInArray(Iterable<JsonNode> iter, String id) {
        for(JsonNode node : iter){
            var idNode = node.get("Id");
            if(idNode != null && idNode.asText().equals(id)){
                return node;
            }
        }
        return MissingNode.getInstance();
    }

    public String getExtensionName() {
        loadVsixManifest();
        return vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_IDENTITY).path("Id").asText();
    }

    public String getNamespace() {
        loadVsixManifest();
        return vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_IDENTITY).path("Publisher").asText();
    }

    public List<String> getExtensionDependencies() {
        loadVsixManifest();
        var extDepenNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Code.ExtensionDependencies");
        return asStringList(extDepenNode.path(MANIFEST_VALUE).asText(), ",");
    }

    public List<String> getBundledExtensions() {
        loadVsixManifest();
        var extPackNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Code.ExtensionPack");
        return asStringList(extPackNode.path(MANIFEST_VALUE).asText(), ",");
    }

    public List<String> getExtensionKinds() {
        loadVsixManifest();
        var extKindNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Code.ExtensionKind");
        return asStringList(extKindNode.path(MANIFEST_VALUE).asText(), ",");
    }

    public String getHomepage() {
        loadVsixManifest();
        var extKindNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Services.Links.Learn");
        return extKindNode.path(MANIFEST_VALUE).asText();
    }

    public String getRepository() {
        loadVsixManifest();
        var sourceNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Services.Links.Source");
        return sourceNode.path(MANIFEST_VALUE).asText();
    }

    public String getBugs() {
        loadVsixManifest();
        var supportNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Services.Links.Support");
        return supportNode.path(MANIFEST_VALUE).asText();
    }

    public String getGalleryColor() {
        loadVsixManifest();
        var colorNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Services.Branding.Color");
        return colorNode.path(MANIFEST_VALUE).asText();
    }

    public String getGalleryTheme() {
        loadVsixManifest();
        var themeNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Services.Branding.Theme");
        return themeNode.path(MANIFEST_VALUE).asText();
    }

    public List<String> getLocalizedLanguages() {
        loadVsixManifest();
        var languagesNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Code.LocalizedLanguages");
        return asStringList(languagesNode.path(MANIFEST_VALUE).asText(), ",");
    }

    public boolean isPreview() {
        loadVsixManifest();
        var galleryFlags = vsixManifest.path(MANIFEST_METADATA).path("GalleryFlags");
        return asStringList(galleryFlags.asText(), " ").contains("Preview");
    }

    public boolean isPreRelease() {
        loadVsixManifest();
        var preReleaseNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Code.PreRelease");
        return preReleaseNode.path(MANIFEST_VALUE).asBoolean(false);
    }

    public String getSponsorLink() {
        loadVsixManifest();
        var sponsorLinkNode = findByIdInArray(vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_PROPERTIES).path(MANIFEST_PROPERTY), "Microsoft.VisualStudio.Code.SponsorLink");
        return sponsorLinkNode.path(MANIFEST_VALUE).asText();
    }

    public ExtensionVersion getMetadata() {
        loadPackageJson();
        loadVsixManifest();
        var extVersion = new ExtensionVersion();
        extVersion.setVersion(getVersion());
        extVersion.setTargetPlatform(getTargetPlatform());
        extVersion.setPreview(isPreview());
        extVersion.setPreRelease(isPreRelease());
        var displayName = vsixManifest.path(MANIFEST_METADATA).path("DisplayName").asText();
        if(StringUtils.isNotBlank(displayName)) {
            extVersion.setDisplayName(displayName);
        }
        extVersion.setDescription(vsixManifest.path(MANIFEST_METADATA).path("Description").path("").asText());
        extVersion.setEngines(getEngines(packageJson.path("engines")));
        extVersion.setCategories(asStringList(vsixManifest.path(MANIFEST_METADATA).path("Categories").asText(), ","));
        extVersion.setExtensionKind(getExtensionKinds());
        extVersion.setTags(getTags());
        extVersion.setLicense(packageJson.path("license").textValue());
        extVersion.setHomepage(getHomepage());
        extVersion.setRepository(getRepository());
        extVersion.setSponsorLink(getSponsorLink());
        extVersion.setBugs(getBugs());
        extVersion.setMarkdown(packageJson.path("markdown").textValue());
        extVersion.setGalleryColor(getGalleryColor());
        extVersion.setGalleryTheme(getGalleryTheme());
        extVersion.setLocalizedLanguages(getLocalizedLanguages());
        extVersion.setQna(packageJson.path("qna").textValue());

        return extVersion;
    }

    public String getVersion() {
        return vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_IDENTITY).path("Version").asText();
    }

    public String getTargetPlatform() {
        loadVsixManifest();
        var targetPlatform = vsixManifest.path(MANIFEST_METADATA).path(MANIFEST_IDENTITY).path("TargetPlatform").asText();
        if (targetPlatform.isEmpty()) {
            targetPlatform = TargetPlatform.NAME_UNIVERSAL;
        }

        return targetPlatform;
    }

    public String getDisplayName() {
        loadVsixManifest();
        var displayName = vsixManifest.path(MANIFEST_METADATA).path("DisplayName").asText();
        if (StringUtils.isBlank(displayName)) {
            return getExtensionName();
        }
        return displayName;
    }

    private List<String> getTags() {
        var tags = vsixManifest.path(MANIFEST_METADATA).path("Tags").asText();
        return asStringList(tags, ",").stream()
                .collect(Collectors.groupingBy(String::toLowerCase))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().getFirst())
                .collect(Collectors.toList());
    }

    private List<String> asStringList(String value, String sep){
        if (StringUtils.isEmpty(value)) {
            return new ArrayList<>();
        }

        return Arrays.asList(value.split(sep));
    }

    private List<String> getEngines(JsonNode node) {
        if (node.isObject()) {
            var result = new ArrayList<String>();
            var fieldIter = node.fields();
            while (fieldIter.hasNext()) {
                var entry = fieldIter.next();
                result.add(entry.getKey() + "@" + entry.getValue().textValue());
            }
            return result;
        }
        return null;
    }

    public void getFileResources(ExtensionVersion extVersion, Consumer<TempFile> processor) {
        try (
                var manifestFile = getManifest(extVersion);
                var readmeFile = getReadme(extVersion);
                var changelogFile = getChangelog(extVersion);
                var licenseFile = getLicense(extVersion);
                var iconFile = getIcon(extVersion);
                var vsixManifestFile = getVsixManifest(extVersion)
        ) {
            Stream.of(manifestFile, readmeFile, changelogFile, licenseFile, iconFile, vsixManifestFile)
                    .filter(Objects::nonNull)
                    .forEach(processor);
        } catch (IOException e) {
            throw new ServerErrorException("Failed to read file resource", e);
        }
    }

    public FileResource getBinary(ExtensionVersion extVersion, String binaryName) {
        var binary = new FileResource();
        binary.setExtension(extVersion);
        binary.setName(Optional.ofNullable(binaryName).orElse(NamingUtil.toFileFormat(extVersion, ".vsix")));
        binary.setType(FileResource.DOWNLOAD);
        return binary;
    }

    public TempFile generateSha256Checksum(ExtensionVersion extVersion) throws IOException {
        String hash;
        try(var input = Files.newInputStream(extensionFile.getPath())) {
            hash = DigestUtils.sha256Hex(input);
        }

        var sha256File = new TempFile("extension_", ".sha256");
        Files.writeString(sha256File.getPath(), hash);

        var sha256Resource = new FileResource();
        sha256Resource.setExtension(extVersion);
        sha256Resource.setName(NamingUtil.toFileFormat(extVersion, ".sha256"));
        sha256Resource.setType(FileResource.DOWNLOAD_SHA256);
        sha256File.setResource(sha256Resource);
        return sha256File;
    }

    protected TempFile getManifest(ExtensionVersion extVersion) throws IOException {
        readInputStream();
        var entryFile = ArchiveUtil.readEntry(zipFile, PACKAGE_JSON);
        if (entryFile == null) {
            throw new ErrorResultException(entryNotFoundMessage(PACKAGE_JSON));
        }
        var manifestResource = new FileResource();
        manifestResource.setExtension(extVersion);
        manifestResource.setName("package.json");
        manifestResource.setType(FileResource.MANIFEST);
        entryFile.setResource(manifestResource);
        return entryFile;
    }

    protected TempFile getReadme(ExtensionVersion extVersion) throws IOException {
        var result = readFromVsixPackage(ExtensionQueryResult.ExtensionFile.FILE_DETAILS, README);
        if (result == null) {
            return null;
        }

        var readme = result.getResource();
        readme.setExtension(extVersion);
        readme.setType(FileResource.README);
        return result;
    }

    public TempFile getChangelog(ExtensionVersion extVersion) throws IOException {
        var result = readFromVsixPackage(ExtensionQueryResult.ExtensionFile.FILE_CHANGELOG, CHANGELOG);
        if (result == null) {
            return null;
        }

        var changelog = result.getResource();
        changelog.setExtension(extVersion);
        changelog.setType(FileResource.CHANGELOG);
        return result;
    }

    public TempFile getLicense(ExtensionVersion extVersion) throws IOException {
        readInputStream();
        var licenseResource = new FileResource();
        licenseResource.setExtension(extVersion);
        licenseResource.setType(FileResource.LICENSE);

        var assetPath = tryGetLicensePath();
        if(StringUtils.isEmpty(assetPath)) {
            return null;
        }

        var entryFile = ArchiveUtil.readEntry(zipFile, assetPath);
        if(entryFile == null) {
            throw new ErrorResultException(entryNotFoundMessage(assetPath));
        }

        var lastSegmentIndex = assetPath.lastIndexOf('/');
        var lastSegment = assetPath.substring(lastSegmentIndex + 1);
        licenseResource.setName(lastSegment);
        entryFile.setResource(licenseResource);
        return entryFile;
    }

    private TempFile readFromVsixPackage(String assetType, String[] alternateNames) throws IOException {
        var assetPath = tryGetAssetPath(assetType);
        if(StringUtils.isNotEmpty(assetPath)) {
            var entryFile = ArchiveUtil.readEntry(zipFile, assetPath);
            if(entryFile == null) {
                throw new ErrorResultException(entryNotFoundMessage(assetPath));
            }

            var lastSegmentIndex = assetPath.lastIndexOf('/');
            var lastSegment = assetPath.substring(lastSegmentIndex + 1);
            var resource = new FileResource();
            resource.setName(lastSegment);
            entryFile.setResource(resource);
            return entryFile;
        } else {
            readInputStream();
            return readFromAlternateNames(alternateNames);
        }
    }

    private TempFile readFromAlternateNames(String[] names) throws IOException {
        for (var name : names) {
            var entry = ArchiveUtil.getEntryIgnoreCase(zipFile, name);
            if (entry != null) {
                var entryFile = ArchiveUtil.readEntry(zipFile, entry);
                var lastSegmentIndex = entry.getName().lastIndexOf('/');
                var lastSegment = entry.getName().substring(lastSegmentIndex + 1);
                var resource = new FileResource();
                resource.setName(lastSegment);
                entryFile.setResource(resource);
                return entryFile;
            }
        }
        return null;
    }

    private String tryGetLicensePath() {
        loadVsixManifest();
        var licensePath = vsixManifest.path(MANIFEST_METADATA).path("License").asText();
        return licensePath.isEmpty()
                ? tryGetAssetPath(ExtensionQueryResult.ExtensionFile.FILE_LICENSE)
                : licensePath;
    }

    private String tryGetAssetPath(String type) {
        loadVsixManifest();
        for(var asset : vsixManifest.path("Assets").path("Asset")) {
            if(Optional.ofNullable(asset.findValue("Type")).map(JsonNode::asText).orElse("").equals(type)) {
                return Optional.ofNullable(asset.findValue("Path"))
                        .map(JsonNode::asText)
                        .orElse(null);
            }
        }

        return null;
    }

    protected TempFile getIcon(ExtensionVersion extVersion) throws IOException {
        var iconPath = tryGetAssetPath(ExtensionQueryResult.ExtensionFile.FILE_ICON);
        if (StringUtils.isEmpty(iconPath)) {
            loadPackageJson();
            var iconPathNode = packageJson.get("icon");
            iconPath = iconPathNode != null && iconPathNode.isTextual()
                    ? iconPathNode.asText().replace('\\', '/')
                    : null;

            if (StringUtils.isEmpty(iconPath)) {
                return null;
            }

            iconPath = "extension/" + iconPath;
        }

        var entryFile = ArchiveUtil.readEntry(zipFile, iconPath);
        if (entryFile == null) {
            throw new ErrorResultException("Icon " + entryNotFoundMessage(iconPath));
        }

        var iconResource = new FileResource();
        iconResource.setExtension(extVersion);
        var fileNameIndex = iconPath.lastIndexOf('/');
        var iconName = fileNameIndex >= 0
                ? iconPath.substring(fileNameIndex + 1)
                : iconPath;

        iconResource.setName(iconName);
        iconResource.setType(FileResource.ICON);
        entryFile.setResource(iconResource);
        return entryFile;
    }

    public TempFile getVsixManifest(ExtensionVersion extVersion) throws IOException {
        readInputStream();
        var vsixManifestResource = new FileResource();
        vsixManifestResource.setExtension(extVersion);
        vsixManifestResource.setName(VSIX_MANIFEST);
        vsixManifestResource.setType(FileResource.VSIXMANIFEST);

        var entryFile = ArchiveUtil.readEntry(zipFile, VSIX_MANIFEST);
        if(entryFile == null) {
            throw new ErrorResultException(entryNotFoundMessage(VSIX_MANIFEST));
        }

        entryFile.setResource(vsixManifestResource);
        return entryFile;
    }

    public boolean isPotentiallyMalicious() {
        readInputStream();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getExtra() != null) {
                logger.warn("Potentially harmful zip entry with extra fields detected: {}", entry.getName());
                return true;
            }
        }

        return false;
    }
}