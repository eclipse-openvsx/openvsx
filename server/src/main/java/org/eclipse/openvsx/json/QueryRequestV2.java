/******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
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
package org.eclipse.openvsx.json;

public record QueryRequestV2(
        String namespaceName,
        String extensionName,
        String extensionVersion,
        String extensionId,
        String extensionUuid,
        String namespaceUuid,
        String includeAllVersions,
        String targetPlatform,
        int size,
        int offset
) {
    public QueryRequest toQueryRequest() {
        return new QueryRequest(
                namespaceName,
                extensionName,
                extensionVersion,
                extensionId,
                extensionUuid,
                namespaceUuid,
                includeAllVersions.equals("true"),
                targetPlatform,
                size,
                offset);
    }
}
