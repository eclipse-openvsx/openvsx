/********************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Marks one access-log file, identified by name and storage type, as already backfilled into
 * download analytics. Kept separate from {@link DownloadIngestion}, which tracks the registry's
 * own (unrelated) log-ingestion bookkeeping and which backfill deliberately does not touch.
 */
@Entity
@Table(name = "download_backfill")
public class DownloadBackfill {

    @Id
    @GeneratedValue(generator = "downloadBackfillSeq")
    @SequenceGenerator(name = "downloadBackfillSeq", sequenceName = "download_backfill_seq")
    private long id;

    private String fileName;

    private String storageType;

    private LocalDateTime processedOn;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public LocalDateTime getProcessedOn() {
        return processedOn;
    }

    public void setProcessedOn(LocalDateTime processedOn) {
        this.processedOn = processedOn;
    }
}
