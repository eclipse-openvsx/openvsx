/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.NotFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultExtensionQueryRequestHandler implements IExtensionQueryRequestHandler {

    private final LocalVSCodeService local;
    private final UpstreamVSCodeService upstream;
    private final List<IVSCodeService> registries;

    public DefaultExtensionQueryRequestHandler(LocalVSCodeService local, UpstreamVSCodeService upstream) {
        this.local = local;
        this.upstream = upstream;
        this.registries = setupRegistries();
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

    @Override
    public ExtensionQueryResult getResult(ExtensionQueryParam param, int pageSize, int defaultPageSize) {
        var totalCount = 0L;
        var extensions = new ArrayList<ExtensionQueryResult.Extension>();
        var extensionIds = new HashSet<String>();

        var services = getVSCodeServices().iterator();
        while(extensions.size() < pageSize && services.hasNext()) {
            try {
                var service = services.next();
                if(extensions.isEmpty()) {
                    var subResult = service.extensionQuery(param, defaultPageSize);
                    var subExtensions = subResult.results().getFirst().extensions();
                    if(subExtensions != null) {
                        extensions.addAll(subExtensions);
                    }

                    totalCount = getTotalCount(subResult);
                } else {
                    var extensionCount = extensions.size();
                    var subResult = service.extensionQuery(param, defaultPageSize);
                    var subExtensions = subResult.results().getFirst().extensions();
                    var subExtensionsCount = subExtensions != null ? subExtensions.size() : 0;
                    if (subExtensionsCount > 0) {
                        int limit = pageSize - extensionCount;
                        mergeExtensionQueryResults(extensions, extensionIds, subExtensions, limit);
                    }

                    var mergedExtensionsCount = extensions.size();
                    var subTotalCount = getTotalCount(subResult);
                    totalCount += subTotalCount - ((extensionCount + subExtensionsCount) - mergedExtensionsCount);
                }
            } catch (NotFoundException | ResponseStatusException exc) {
                // Try the next registry
            }
        }

        return local.toQueryResult(extensions, totalCount);
    }

    private void mergeExtensionQueryResults(List<ExtensionQueryResult.Extension> extensions, Set<String> extensionIds, List<ExtensionQueryResult.Extension> subExtensions, int limit) {
        if(extensionIds.isEmpty() && !extensions.isEmpty()) {
            var extensionIdSet = extensions.stream()
                    .map(NamingUtil::toExtensionId)
                    .collect(Collectors.toSet());

            extensionIds.addAll(extensionIdSet);
        }

        var subExtensionsIter = subExtensions.iterator();
        while (subExtensionsIter.hasNext() && extensions.size() < limit) {
            var subExtension = subExtensionsIter.next();
            var key = NamingUtil.toExtensionId(subExtension);
            if(!extensionIds.contains(key)) {
                extensions.add(subExtension);
                extensionIds.add(key);
            }
        }
    }

    private long getTotalCount(ExtensionQueryResult subResult) {
        return subResult.results().getFirst().resultMetadata().stream()
                .filter(metadata -> metadata.metadataType().equals("ResultCount"))
                .findFirst()
                .map(ExtensionQueryResult.ResultMetadata::metadataItems)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(item -> item.name().equals("TotalCount"))
                .findFirst()
                .map(ExtensionQueryResult.ResultMetadataItem::count)
                .orElse(0L);
    }
}
