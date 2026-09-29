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
package org.eclipse.openvsx.entities;

import jakarta.persistence.*;

@Entity
public class FileResource {

    // Resource types
    public static final String DOWNLOAD = "download";
    public static final String DOWNLOAD_SHA256 = "sha256";
    public static final String DOWNLOAD_SIG = "signature";
    public static final String PUBLIC_KEY = "publicKey";
    public static final String MANIFEST = "manifest";
    public static final String ICON = "icon";
    public static final String README = "readme";
    public static final String LICENSE = "license";
    public static final String CHANGELOG = "changelog";
    public static final String VSIXMANIFEST = "vsixmanifest";

    // Storage types
    public static final String STORAGE_LOCAL = "local";
    public static final String STORAGE_GOOGLE = "google-cloud";
    public static final String STORAGE_AZURE = "azure-blob";
    public static final String STORAGE_AWS = "aws";

    @Id
    @GeneratedValue(generator = "fileResourceSeq")
    @SequenceGenerator(name = "fileResourceSeq", sequenceName = "file_resource_seq")
    private long id;

    @OneToOne
    private ExtensionVersion extension;

    private String name;

    @Column(length = 32)
    private String type;

    @Column(length = 32)
    private String storageType;

    private Long size;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public ExtensionVersion getExtension() {
        return extension;
    }

    public void setExtension(ExtensionVersion extension) {
        this.extension = extension;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    /**
     * The size of the file in bytes, or {@code null} if it hasn't been determined yet
     * (e.g. for resources created before this field was introduced, pending backfill).
     */
    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
