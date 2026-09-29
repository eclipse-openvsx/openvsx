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
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;

import org.eclipse.openvsx.entities.*;

public interface ExtensionVersionRepository extends Repository<ExtensionVersion, Long> {

    /**
     * Finds all distinct publishers who are enlisted as publisher in the extension_version table for active extension.
     * In other words, returns distinct {@link UserData} for all users who published a currently active extension.
     */
    @Query(
        "select distinct ev.publishedBy from ExtensionVersion ev where ev.active = true and ev.publishedBy is not null"
    )
    Streamable<UserData> findPublishersWithActiveVersions();

    Streamable<ExtensionVersion> findByExtension(Extension extension);

    Streamable<ExtensionVersion> findByExtensionAndActiveTrue(Extension extension);

    ExtensionVersion findByVersionAndTargetPlatformAndExtension(
            String version,
            String targetPlatform,
            Extension extension
    );

    ExtensionVersion findByVersionAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
            String version,
            String targetPlatform,
            String extensionName,
            String namespace
    );

    ExtensionVersion findByPublishedByAndVersionAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
            UserData user,
            String version,
            String targetPlatform,
            String extensionName,
            String namespace
    );

    Streamable<ExtensionVersion> findByPublishedByAndActive(UserData user, boolean active);

    long countByRemovedBy(UserData removedBy);

    Streamable<ExtensionVersion> findAll();

    Streamable<ExtensionVersion> findBySignatureKeyPairNotOrSignatureKeyPairIsNull(SignatureKeyPair keyPair);

    @Query(
        "select ev from ExtensionVersion ev where concat(',', ev.bundledExtensions, ',') like concat('%,', ?1, ',%')"
    )
    Streamable<ExtensionVersion> findByBundledExtensions(String extensionId);

    @Query("select ev from ExtensionVersion ev where concat(',', ev.dependencies, ',') like concat('%,', ?1, ',%')")
    Streamable<ExtensionVersion> findByDependencies(String extensionId);

    @Query("select min(ev.timestamp) from ExtensionVersion ev")
    LocalDateTime getOldestTimestamp();

    @Modifying
    @Query("update ExtensionVersion ev set ev.signatureKeyPair = null")
    void setKeyPairsNull();

    Page<ExtensionVersion> findByActiveTrueAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
            String extension,
            String namespace,
            Pageable page
    );

    Page<ExtensionVersion> findByActiveTrueAndTargetPlatformAndExtensionNameIgnoreCaseAndExtensionNamespaceNameIgnoreCase(
            String targetPlatform,
            String extension,
            String namespace,
            Pageable page
    );
}
