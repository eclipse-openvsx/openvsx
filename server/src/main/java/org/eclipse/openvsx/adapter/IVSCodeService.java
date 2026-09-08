/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface IVSCodeService {

    /**
     * The version of the VS Code Gallery API protocol this service speaks, as used in the
     * {@code api-version} media type parameter of the {@code Accept}/{@code Content-Type} headers,
     * e.g. {@code application/json;api-version=3.0-preview.1}. Shared between the outgoing requests
     * to an upstream marketplace ({@link VSCodeIdService}) and the {@code Content-Type} this server
     * itself returns from the gallery endpoints in {@link VSCodeAPI}.
     */
    // TODO: check if this version is still valid when connecting to the VSC Marketplace
    String GALLERY_API_VERSION = "3.0-preview.1";

    ExtensionQueryResult extensionQuery(ExtensionQueryParam param, int defaultPageSize);

    ExtensionQueryResult.Extension latest(String namespaceName, String extensionName);

    ResponseEntity<StreamingResponseBody> browse(
            String namespaceName,
            String extensionName,
            String version,
            String targetPlatform,
            String path
    );

    String download(String namespace, String extension, String version, String targetPlatform);

    String getItemUrl(String namespace, String extension);

    ResponseEntity<StreamingResponseBody> getAsset(
            String namespace,
            String extensionName,
            String version,
            String assetType,
            String targetPlatform,
            String restOfTheUrl
    );
}
