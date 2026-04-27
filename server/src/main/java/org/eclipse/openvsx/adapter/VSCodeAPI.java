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

import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.ExtensionFile.*;
import static org.eclipse.openvsx.util.TargetPlatform.*;

@RestController
public class VSCodeAPI {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Logger logger = LoggerFactory.getLogger(VSCodeAPI.class);

    private final LocalVSCodeService local;
    private final UpstreamVSCodeService upstream;
    private final List<IVSCodeService> registries;
    private final IExtensionQueryRequestHandler extensionQueryRequestHandler;

    public VSCodeAPI(
            LocalVSCodeService local,
            UpstreamVSCodeService upstream,
            IExtensionQueryRequestHandler extensionQueryRequestHandler
    ) {
        this.local = local;
        this.upstream = upstream;
        this.registries = setupRegistries();
        this.extensionQueryRequestHandler = extensionQueryRequestHandler;
    }

    private List<IVSCodeService> setupRegistries() {
        if (upstream.isValid()) {
            return List.of(local, upstream);
        } else {
            return List.of(local);
        }
    }

    private Iterable<IVSCodeService> getVSCodeServices() {
        return registries;
    }

    @PostMapping(
        path = "/vscode/gallery/extensionquery",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Provides metadata of extensions matching the given parameters")
    @ApiResponse(
            responseCode = "200",
            description = "Returns the query results"
    )
    public ExtensionQueryResult extensionQuery(@RequestBody @Parameter(description = "Parameters of the extension query") ExtensionQueryParam param) {
        var size = 0;
        if(param.filters() != null && !param.filters().isEmpty()) {
            size = param.filters().getFirst().pageSize();
        }
        if(size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        return extensionQueryRequestHandler.getResult(param, size, DEFAULT_PAGE_SIZE);
    }

    @Observed
    @GetMapping("/vscode/asset/{namespaceName}/{extensionName}/{version}/{assetType}/**")
    @CrossOrigin
    @Operation(summary = "Access an extension asset")
    @ApiResponse(
            responseCode = "200",
            description = "The file content is returned",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    )
    @ApiResponse(
            responseCode = "302",
            description = "The asset is found at the specified location",
            content = @Content(),
            headers = @Header(
                    name = "Location",
                    description = "The actual URL where the asset can be accessed",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "The namespace name is the built-in extension namespace",
            content = @Content()
    )
    @ApiResponse(
            responseCode = "404",
            description = "The specified asset could not be found",
            content = @Content()
    )
    public ResponseEntity<StreamingResponseBody> getAsset(
            HttpServletRequest request,
            @PathVariable @Parameter(description = "Extension namespace", example = "vitest") String namespaceName,
            @PathVariable @Parameter(description = "Extension name", example = "explorer") String extensionName,
            @PathVariable @Parameter(description = "Extension version", example = "1.6.6") String version,
            @PathVariable
            @Parameter(
                    description = "Asset type",
                    example = FILE_VSIX,
                    schema = @Schema(type = "string", allowableValues = {
                            FILE_ICON, FILE_DETAILS, FILE_CHANGELOG, FILE_MANIFEST, FILE_VSIX, FILE_LICENSE,
                            FILE_WEB_RESOURCES, FILE_VSIXMANIFEST, FILE_SIGNATURE, FILE_PUBLIC_KEY
                    })
            )
            String assetType,
            @RequestParam(defaultValue = TargetPlatform.NAME_UNIVERSAL)
            @Parameter(
                    description = "Target platform",
                    example = TargetPlatform.NAME_LINUX_X64,
                    schema = @Schema(type = "string", allowableValues = {
                            NAME_WIN32_X64, NAME_WIN32_IA32, NAME_WIN32_ARM64,
                            NAME_LINUX_X64, NAME_LINUX_ARM64, NAME_LINUX_ARMHF,
                            NAME_ALPINE_X64, NAME_ALPINE_ARM64,
                            NAME_DARWIN_X64, NAME_DARWIN_ARM64,
                            NAME_WEB, NAME_UNIVERSAL
                    })
            )
            String targetPlatform
    ) {
        var restOfTheUrl = UrlUtil.extractWildcardPath(request);
        try {
            for (var service : getVSCodeServices()) {
                try {
                    return service.getAsset(namespaceName, extensionName, version, assetType, targetPlatform, restOfTheUrl);
                } catch (NotFoundException exc) {
                    // Try the next registry
                }
            }
            return ResponseEntity.notFound().build();
        } catch (ErrorResultException ex) {
            logger.error(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/vscode/item")
    @CrossOrigin
    @Operation(summary = "Access an extension item")
    @ApiResponse(
            responseCode = "302",
            description = "The item is found at the specified location",
            content = @Content(),
            headers = @Header(
                    name = "Location",
                    description = "The actual URL where the item can be accessed",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "The itemName could not be parsed to publisher and extension name",
            content = @Content()
    )
    @ApiResponse(
            responseCode = "404",
            description = "The specified item could not be found",
            content = @Content()
    )
    public ModelAndView getItemUrl(
            @RequestParam
            @Parameter(description = "Identifier in the format {publisher}.{name}", example = "foo.bar")
            String itemName,
            ModelMap model
    ) {
        var dotIndex = itemName.indexOf('.');
        if (dotIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expecting an item of the form `{publisher}.{name}`");
        }

        var namespace = itemName.substring(0, dotIndex);
        var extension = itemName.substring(dotIndex + 1);
        for (var service : getVSCodeServices()) {
            try {
                var itemUrl = service.getItemUrl(namespace, extension);
                return new ModelAndView("redirect:" + itemUrl, model);
            } catch (NotFoundException exc) {
                // Try the next registry
            }
        }

        return new ModelAndView(null, HttpStatus.NOT_FOUND);
    }

    @GetMapping("/vscode/gallery/publishers/{namespaceName}/vsextensions/{extensionName}/{version}/vspackage")
    @CrossOrigin
    @Operation(summary = "Access an extension package")
    @ApiResponse(
            responseCode = "302",
            description = "The package is found at the specified location",
            content = @Content(),
            headers = @Header(
                    name = "Location",
                    description = "The actual URL where the package can be downloaded",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "The namespace name is the built-in extension namespace",
            content = @Content()
    )
    @ApiResponse(
            responseCode = "404",
            description = "The specified package could not be found",
            content = @Content()
    )
    public ModelAndView download(
            @PathVariable @Parameter(description = "Extension namespace", example = "JFrog") String namespaceName,
            @PathVariable @Parameter(description = "Extension name", example = "jfrog-vscode-extension") String extensionName,
            @PathVariable @Parameter(description = "Extension version", example = "2.11.7") String version,
            @RequestParam(defaultValue = TargetPlatform.NAME_UNIVERSAL)
            @Parameter(
                    description = "Target platform",
                    example = TargetPlatform.NAME_LINUX_X64,
                    schema = @Schema(type = "string", allowableValues = {
                            NAME_WIN32_X64, NAME_WIN32_IA32, NAME_WIN32_ARM64,
                            NAME_LINUX_X64, NAME_LINUX_ARM64, NAME_LINUX_ARMHF,
                            NAME_ALPINE_X64, NAME_ALPINE_ARM64,
                            NAME_DARWIN_X64, NAME_DARWIN_ARM64,
                            NAME_WEB, NAME_UNIVERSAL
                    })
            )
            String targetPlatform,
            ModelMap model
    ) {
        for (var service : getVSCodeServices()) {
            try {
                var downloadUrl = service.download(namespaceName, extensionName, version, targetPlatform);
                return new ModelAndView("redirect:" + downloadUrl, model);
            } catch (NotFoundException exc) {
                // Try the next registry
            }
        }

        return new ModelAndView(null, HttpStatus.NOT_FOUND);
    }

    @Observed
    @GetMapping("/vscode/unpkg/{namespaceName}/{extensionName}/{version}/**")
    @CrossOrigin
    @Operation(summary = "Browse an extension package")
    @ApiResponse(
            responseCode = "200",
            description = "The file content is returned in binary format or a list of file URLs is returned in JSON format in case the path is a directory",
            content = {@Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(value = "[\"https://open-vsx.org/vscode/unpkg/redhat/java/0.65.0/extension\", \"https://open-vsx.org/vscode/unpkg/redhat/java/0.65.0/extension.vsixmanifest\"]")
            ), @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)}
    )
    @ApiResponse(
            responseCode = "302",
            description = "The file is found at the specified location",
            content = @Content(),
            headers = @Header(
                    name = "Location",
                    description = "The actual URL where the file can be accessed",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "The namespace name is the built-in extension namespace",
            content = @Content()
    )
    @ApiResponse(
            responseCode = "404",
            description = "The specified file or directory could not be found",
            content = @Content()
    )
    public ResponseEntity<StreamingResponseBody> browse(
            HttpServletRequest request,
            @PathVariable @Parameter(description = "Extension namespace", example = "malloydata") String namespaceName,
            @PathVariable @Parameter(description = "Extension name", example = "malloy-vscode") String extensionName,
            @PathVariable @Parameter(description = "Extension version", example = "0.3.1710435722") String version
    ) {
        var path = UrlUtil.extractWildcardPath(request);
        try {
            for (var service : getVSCodeServices()) {
                try {
                    return service.browse(namespaceName, extensionName, version, path);
                } catch (NotFoundException exc) {
                    // Try the next registry
                }
            }
            return ResponseEntity.notFound().build();
        } catch (ErrorResultException ex) {
            logger.error(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/vscode/gallery/{namespaceName}/{extensionName}/latest",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @Operation(summary = "Provides metadata of the extension matching the given parameters")
    @ApiResponse(
            responseCode = "200",
            description = "Returns the extension metadata"
    )
    @ApiResponse(
            responseCode = "404",
            description = "The specified extension could not be found",
            content = @Content()
    )
    public ResponseEntity<ExtensionQueryResult.Extension> getLatest(
            @PathVariable @Parameter(description = "Extension namespace", example = "malloydata") String namespaceName,
            @PathVariable @Parameter(description = "Extension name", example = "malloy-vscode") String extensionName
    ) {
        try {
            for (var service : getVSCodeServices()) {
                try {
                    var result = service.latest(namespaceName, extensionName);
                    return ResponseEntity.ok(result);
                } catch (NotFoundException exc) {
                    // Try the next registry
                }
            }
            return ResponseEntity.notFound().build();
        } catch (ErrorResultException ex) {
            logger.error(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
