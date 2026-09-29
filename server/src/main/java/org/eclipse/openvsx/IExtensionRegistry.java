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
package org.eclipse.openvsx;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.search.ISearchService;

/**
 * Declaration of the registry API methods that can be accessed without authentication.
 */
public interface IExtensionRegistry {

    NamespaceJson getNamespace(String namespace);

    ExtensionJson getExtension(String namespace, String extensionName, String targetPlatform);

    ExtensionJson getExtension(String namespace, String extensionName, String targetPlatform, String version);

    VersionsJson getVersions(String namespace, String extension, String targetPlatform, int size, int offset);

    VersionReferencesJson getVersionReferences(
            String namespace,
            String extension,
            String targetPlatform,
            int size,
            int offset
    );

    ResponseEntity<StreamingResponseBody> getFile(
            String namespace,
            String extensionName,
            String targetPlatform,
            String version,
            String fileName
    );

    ReviewListJson getReviews(String namespace, String extension);

    SearchResultJson search(ISearchService.Options options);

    QueryResultJson query(QueryRequest request);

    QueryResultJson queryV2(QueryRequestV2 request);

    NamespaceDetailsJson getNamespaceDetails(String namespace);

    ResponseEntity<StreamingResponseBody> getNamespaceLogo(String namespaceName, String fileName);

    String getPublicKey(String publicId);

    RegistryVersionJson getRegistryVersion();
}
